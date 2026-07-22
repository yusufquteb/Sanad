/**
 * Sanad App — Firebase Cloud Functions (v2)
 *
 * [مرحلة 2.1] Server-side Matching:
 *   ✅ onReportApproved — عند approval يُشغّل cross-match مع found_persons
 *   ✅ onFoundPersonCreated — عند إضافة found_person يُطابق مع reports
 *   ✅ onReportResolved — عند الحل يُضيف لـ success_stories تلقائياً
 *   ✅ Amber Alert geo-targeting — FCM topic للمحافظة
 *   ✅ Rate limiting — منع أكثر من 5 بلاغات/24 ساعة
 *
 * [موجود مسبقاً]:
 *   ✅ sendPushOnNotification — FCM push عند كتابة notification
 *   ✅ sendPushOnAdminNotification — FCM push للمديرين
 *   ✅ updateStatsOnReportChange — تحديث stats/ تلقائياً
 */

const functions = require("firebase-functions");
const admin     = require("firebase-admin");

admin.initializeApp();
const db  = admin.database();
const fcm = admin.messaging();

// ═══════════════════════════════════════════════════════════════════════
// الموجود مسبقاً — FCM Push عند كتابة إشعار جديد
// ═══════════════════════════════════════════════════════════════════════

exports.sendPushOnNotification = functions
  .region("us-central1")
  .database.ref("notifications/{uid}/{notifId}")
  .onCreate(async (snapshot, context) => {
    const uid     = context.params.uid;
    const data    = snapshot.val() || {};
    const message = data.message || "إشعار جديد من تطبيق سند";
    const type    = data.type    || "general";
    const reportId = data.reportId || null;
    const foundId  = data.foundId  || null;

    try {
      const tokenSnap = await db.ref(`users/${uid}/fcmToken`).once("value");
      const token = tokenSnap.val();

      if (!token) {
        console.log(`[sendPush] لا يوجد FCM token للمستخدم ${uid}`);
        return null;
      }

      const payload = {
        notification: {
          title: buildTitle(type),
          body:  message,
        },
        data: {
          type,
          click_action: "FLUTTER_NOTIFICATION_CLICK",
          ...(reportId && { reportId }),
          ...(foundId  && { foundId  }),
        },
        token,
        android: {
          priority: "high",
          notification: {
            sound:     "default",
            channelId: chooseChannel(type),
          },
        },
      };

      const result = await fcm.send(payload);
      console.log(`[sendPush] ✅ أُرسل إلى ${uid}: ${result}`);
      await snapshot.ref.update({ fcmSent: true, fcmSentAt: Date.now() });
      return result;
    } catch (err) {
      console.error(`[sendPush] ❌ فشل الإرسال إلى ${uid}:`, err.message);
      return null;
    }
  });

exports.sendPushOnAdminNotification = functions
  .region("us-central1")
  .database.ref("admin_notifications/{notifId}")
  .onCreate(async (snapshot, context) => {
    const data    = snapshot.val() || {};
    const message = data.message || "إشعار إداري جديد";
    const type    = data.type    || "admin";

    try {
      const usersSnap = await db.ref("users").orderByChild("role").once("value");

      const tokens = [];
      usersSnap.forEach(child => {
        const role  = child.val().role  || "member";
        const token = child.val().fcmToken;
        if ((role === "admin" || role === "manager") && token) {
          tokens.push(token);
        }
      });

      if (tokens.length === 0) {
        console.log("[sendAdminPush] لا يوجد مديرون مسجّلون");
        return null;
      }

      const result = await fcm.sendEachForMulticast({
        notification: { title: "⚡ " + buildTitle(type), body: message },
        data: { type, reportId: data.reportId || "", foundId: data.foundId || "" },
        tokens,
        android: { priority: "high" },
      });

      console.log(`[sendAdminPush] ✅ أُرسل لـ ${result.successCount} مدير`);
      return result;
    } catch (err) {
      console.error("[sendAdminPush] ❌", err.message);
      return null;
    }
  });

exports.updateStatsOnReportChange = functions
  .region("us-central1")
  .database.ref("reports/{reportId}")
  .onWrite(async (change, context) => {
    try {
      const reportsSnap = await db.ref("reports").once("value");

      let approved = 0, resolved = 0, pending = 0, total = 0;
      const now = new Date();
      const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
      let resolvedMonth = 0;

      reportsSnap.forEach(child => {
        total++;
        const status = child.val().status || "pending";
        const ts     = child.val().resolvedAt || 0;

        if (status === "approved") approved++;
        if (status === "resolved") {
          resolved++;
          if (ts >= monthStart) resolvedMonth++;
        }
        if (status === "pending") pending++;
      });

      await db.ref("stats").update({
        total, approved, resolved, pending, resolvedMonth,
        lastUpdated: Date.now(),
      });

      console.log(`[updateStats] ✅ total=${total} approved=${approved} resolved=${resolved}`);
      return null;
    } catch (err) {
      console.error("[updateStats] ❌", err.message);
      return null;
    }
  });

// ═══════════════════════════════════════════════════════════════════════
// [مرحلة 2.1] Server-side Cross-Matching عند الموافقة على بلاغ
//
// يُطلَق عند تغيير reports/{reportId}/status إلى "approved"
// يُطابق البلاغ مع found_persons ويُنشئ match_candidates
// ═══════════════════════════════════════════════════════════════════════

exports.onReportApproved = functions
  .region("us-central1")
  .database.ref("reports/{reportId}/status")
  .onUpdate(async (change, context) => {
    const beforeStatus = change.before.val();
    const afterStatus  = change.after.val();

    // فقط عند التحول إلى "approved"
    if (afterStatus !== "approved" || beforeStatus === "approved") return null;

    const reportId = context.params.reportId;
    console.log(`[onReportApproved] 🔍 بدء matching للبلاغ: ${reportId}`);

    try {
      const reportSnap = await db.ref(`reports/${reportId}`).once("value");
      const report     = reportSnap.val();
      if (!report) return null;

      const reportEmb    = report.faceEmbedding || null;
      const reportName   = (report.personName   || "").toLowerCase().trim();
      const reportAge    = parseInt(report.personAge) || 0;
      const reportGov    = (report.location     || "").split(" ")[0]; // أول كلمة = المحافظة
      const reportGender = report.gender        || "";

      // جلب found_persons للمقارنة
      const foundSnap = await db.ref("found_persons")
        .orderByChild("status").equalTo("pending")
        .once("value");

      const candidates = [];

      foundSnap.forEach(child => {
        const found    = child.val();
        let   score    = 0;
        const reasons  = [];

        // 1. مقارنة الجنس (مطلوب)
        if (reportGender && found.gender && reportGender !== found.gender) return; // skip

        // 2. مقارنة المحافظة (+20)
        const foundGov = (found.location || "").split(" ")[0];
        if (reportGov && foundGov && reportGov === foundGov) {
          score += 20;
          reasons.push("same_governorate");
        }

        // 3. مقارنة العمر التقريبي (+15)
        const foundAge = parseInt(found.estimatedAge) || 0;
        if (reportAge > 0 && foundAge > 0 && Math.abs(reportAge - foundAge) <= 5) {
          score += 15;
          reasons.push("age_match");
        }

        // 4. مقارنة الـ face embedding — الأهم (+65)
        const sim = bestSimilarityAgainstRecord(reportEmb, found);
        if (sim >= 0.6) {
          score += Math.round(sim * 65);
          reasons.push(`face_sim_${Math.round(sim * 100)}`);
        }

        // فقط المرشحون فوق 40%
        if (score >= 40) {
          candidates.push({
            foundId:    child.key,
            reportId,
            score,
            reasons:    reasons.join(","),
            reporterUid: report.reporterId || "",
            foundUid:   found.reporterId  || "",
            createdAt:  Date.now(),
            status:     score >= 75 ? "high_confidence" : "review_needed",
          });
        }
      });

      // حفظ match_candidates
      const writes = candidates.map(async (candidate) => {
        const key = `${reportId}_${candidate.foundId}`;
        await db.ref(`match_candidates/${key}`).set(candidate);

        // إشعار صاحب البلاغ إذا كانت النسبة عالية
        if (candidate.score >= 70 && candidate.reporterUid) {
          await db.ref(`notifications/${candidate.reporterUid}`).push({
            type:      "report_matches_found",
            message:   `تم العثور على تطابق محتمل ${candidate.score}% لبلاغك`,
            reportId,
            foundId:   candidate.foundId,
            createdAt: Date.now(),
            read:      false,
          });
        }

        // إشعار الإدارة دائماً
        await db.ref("admin_notifications").push({
          type:      "admin_face_match",
          message:   `تطابق ${candidate.score}% — بلاغ: ${reportId} / وجد: ${candidate.foundId}`,
          reportId,
          foundId:   candidate.foundId,
          createdAt: Date.now(),
        });
      });

      await Promise.all(writes);
      console.log(`[onReportApproved] ✅ ${candidates.length} match candidates لـ ${reportId}`);
      return null;
    } catch (err) {
      console.error(`[onReportApproved] ❌`, err.message);
      return null;
    }
  });

// ═══════════════════════════════════════════════════════════════════════
// [مرحلة 2.1] Server-side Matching عند إضافة found_person
// ═══════════════════════════════════════════════════════════════════════

exports.onFoundPersonCreated = functions
  .region("us-central1")
  .database.ref("found_persons/{foundId}")
  .onCreate(async (snapshot, context) => {
    const foundId = context.params.foundId;
    const found   = snapshot.val();
    if (!found) return null;

    console.log(`[onFoundPersonCreated] 🔍 matching لـ found: ${foundId}`);

    try {
      const foundEmb    = found.faceEmbedding || null;
      const foundGov    = (found.location     || "").split(" ")[0];
      const foundAge    = parseInt(found.estimatedAge || found.personAge) || 0;
      const foundGender = found.gender        || "";

      // جلب البلاغات المعتمدة
      const reportsSnap = await db.ref("reports")
        .orderByChild("status").equalTo("approved")
        .limitToLast(500)
        .once("value");

      const candidates = [];

      reportsSnap.forEach(child => {
        const report     = child.val();
        let   score      = 0;
        const reasons    = [];

        if (foundGender && report.gender && foundGender !== report.gender) return;

        const reportGov = (report.location || "").split(" ")[0];
        if (foundGov && reportGov && foundGov === reportGov) {
          score += 20;
          reasons.push("same_governorate");
        }

        const reportAge = parseInt(report.personAge) || 0;
        if (foundAge > 0 && reportAge > 0 && Math.abs(foundAge - reportAge) <= 5) {
          score += 15;
          reasons.push("age_match");
        }

        const sim = bestSimilarityAgainstRecord(foundEmb, report);
        if (sim >= 0.6) {
          score += Math.round(sim * 65);
          reasons.push(`face_sim_${Math.round(sim * 100)}`);
        }

        if (score >= 40) {
          candidates.push({
            foundId,
            reportId:    child.key,
            score,
            reasons:     reasons.join(","),
            reporterUid: report.reporterId || "",
            foundUid:    found.reporterId  || "",
            createdAt:   Date.now(),
            status:      score >= 75 ? "high_confidence" : "review_needed",
          });
        }
      });

      const writes = candidates.map(async (candidate) => {
        const key = `${candidate.reportId}_${foundId}`;
        await db.ref(`match_candidates/${key}`).set(candidate);

        if (candidate.score >= 70 && candidate.reporterUid) {
          await db.ref(`notifications/${candidate.reporterUid}`).push({
            type:      "found_matches_report",
            message:   `🎉 وُجد شخص يُشبه مفقودك بنسبة ${candidate.score}%`,
            reportId:  candidate.reportId,
            foundId,
            createdAt: Date.now(),
            read:      false,
          });
        }
      });

      await Promise.all(writes);
      console.log(`[onFoundPersonCreated] ✅ ${candidates.length} candidates لـ ${foundId}`);
      return null;
    } catch (err) {
      console.error(`[onFoundPersonCreated] ❌`, err.message);
      return null;
    }
  });

// ═══════════════════════════════════════════════════════════════════════
// [مرحلة 2.1 + 6.2] تلقائي: عند الحل → success_stories
// ═══════════════════════════════════════════════════════════════════════

exports.onReportResolved = functions
  .region("us-central1")
  .database.ref("reports/{reportId}/status")
  .onUpdate(async (change, context) => {
    const before = change.before.val();
    const after  = change.after.val();

    if (after !== "resolved" || before === "resolved") return null;

    const reportId = context.params.reportId;

    try {
      const reportSnap = await db.ref(`reports/${reportId}`).once("value");
      const report     = reportSnap.val();
      if (!report) return null;

      // تحقق أن القصة غير موجودة بالفعل
      const existingSnap = await db.ref("success_stories")
        .orderByChild("reportId").equalTo(reportId)
        .limitToFirst(1).once("value");

      if (existingSnap.exists()) {
        console.log(`[onReportResolved] قصة موجودة مسبقاً للبلاغ ${reportId}`);
        return null;
      }

      // إنشاء قصة نجاح تلقائية
      const story = {
        reportId,
        personName:   report.personName   || "مجهول",
        personAge:    report.personAge    || 0,
        photoUrl:     report.photoUrl     || "",
        location:     report.location     || "",
        resolvedAt:   report.resolvedAt   || Date.now(),
        reporterId:   report.reporterId   || "",
        matchPercent: report.matchPercent || 0,
        autoGenerated: true,
        // يُخفى حتى يوافق عليه المدير
        approved:     false,
        createdAt:    Date.now(),
      };

      await db.ref("success_stories").push(story);
      console.log(`[onReportResolved] ✅ success story أُضيفت للبلاغ ${reportId}`);

      // إشعار الإدارة بمراجعة القصة
      await db.ref("admin_notifications").push({
        type:      "success_story_pending",
        message:   `قصة نجاح جديدة بانتظار المراجعة: ${report.personName || reportId}`,
        reportId,
        createdAt: Date.now(),
      });

      return null;
    } catch (err) {
      console.error(`[onReportResolved] ❌`, err.message);
      return null;
    }
  });

// ═══════════════════════════════════════════════════════════════════════
// [مرحلة 6.1] Amber Alert — FCM Topic للمحافظة
// ═══════════════════════════════════════════════════════════════════════

exports.onAmberAlertCreated = functions
  .region("us-central1")
  .database.ref("amber_alerts/{alertId}")
  .onCreate(async (snapshot, context) => {
    const alertId = context.params.alertId;
    const alert   = snapshot.val();
    if (!alert || !alert.active) return null;

    const governorate = (alert.location || "").split(" ")[0];
    const reportId    = alert.reportId  || "";

    console.log(`[onAmberAlertCreated] 🚨 Alert: ${alertId} gov: ${governorate}`);

    try {
      // 1. إرسال لـ topic المحافظة
      if (governorate) {
        const topicName = "amber_" + governorate.replace(/\s/g, "_");
        await fcm.send({
          notification: {
            title: "🚨 تنبيه طفل مفقود — " + governorate,
            body:  `${alert.personName || "طفل"} — ${alert.description || "يُرجى الإبلاغ فوراً"}`,
          },
          data: {
            type:     "amber_alert",
            reportId,
            alertId,
          },
          topic: topicName,
          android: {
            priority: "high",
            notification: { channelId: "amber_alerts", sound: "default" },
          },
        });
        console.log(`[onAmberAlertCreated] ✅ أُرسل لـ topic: ${topicName}`);
      }

      // 2. إرسال لكل المستخدمين في نفس المحافظة (fallback)
      if (governorate) {
        const usersSnap = await db.ref("users")
          .orderByChild("governorate").equalTo(governorate)
          .once("value");

        const tokens = [];
        usersSnap.forEach(child => {
          const token = child.val().fcmToken;
          if (token) tokens.push(token);
        });

        if (tokens.length > 0) {
          const batchSize = 500; // FCM limit
          for (let i = 0; i < tokens.length; i += batchSize) {
            const batch = tokens.slice(i, i + batchSize);
            await fcm.sendEachForMulticast({
              notification: {
                title: "🚨 تنبيه طفل مفقود — " + governorate,
                body:  `${alert.personName || "طفل"} في منطقتك`,
              },
              data: { type: "amber_alert", reportId, alertId },
              tokens: batch,
              android: { priority: "high" },
            });
          }
          console.log(`[onAmberAlertCreated] ✅ أُرسل لـ ${tokens.length} مستخدم في ${governorate}`);
        }
      }

      return null;
    } catch (err) {
      console.error(`[onAmberAlertCreated] ❌`, err.message);
      return null;
    }
  });

// ═══════════════════════════════════════════════════════════════════════
// [مرحلة 5.2] Rate Limiting — منع أكثر من 5 بلاغات في 24 ساعة
// ═══════════════════════════════════════════════════════════════════════

exports.enforceRateLimit = functions
  .region("us-central1")
  .database.ref("reports/{reportId}")
  .onCreate(async (snapshot, context) => {
    const reportId  = context.params.reportId;
    const reporterId = snapshot.val()?.reporterId;

    if (!reporterId) return null;

    try {
      const since = Date.now() - 24 * 60 * 60 * 1000; // آخر 24 ساعة

      const recentSnap = await db.ref("reports")
        .orderByChild("reporterId").equalTo(reporterId)
        .once("value");

      let recentCount = 0;
      recentSnap.forEach(child => {
        const ts = child.val().createdAt || 0;
        if (ts >= since && child.key !== reportId) recentCount++;
      });

      if (recentCount >= 5) {
        // حذف البلاغ الجديد وتسجيل المخالفة
        await snapshot.ref.update({ status: "rate_limited", rateLimitedAt: Date.now() });
        console.warn(`[enforceRateLimit] ⚠️ ${reporterId} تجاوز الحد — بلاغات: ${recentCount + 1}`);

        // إشعار الإدارة
        await db.ref("admin_notifications").push({
          type:      "rate_limit_violation",
          message:   `المستخدم ${reporterId} رفع ${recentCount + 1} بلاغات في 24 ساعة`,
          reportId,
          createdAt: Date.now(),
        });
      }

      return null;
    } catch (err) {
      console.error(`[enforceRateLimit] ❌`, err.message);
      return null;
    }
  });

// ═══════════════════════════════════════════════════════════════════════
// [مرحلة 5.3] Image Moderation — فحص الصور المرفوعة بـ Vision API
// يُطلَق عند كتابة photoUrl جديدة في reports أو found_persons
// ═══════════════════════════════════════════════════════════════════════

exports.moderateReportImage = functions
  .region("us-central1")
  .database.ref("reports/{reportId}/photoUrl")
  .onWrite(async (change, context) => {
    const photoUrl = change.after.val();
    if (!photoUrl || typeof photoUrl !== "string") return null;

    const reportId = context.params.reportId;
    return moderateImageUrl(photoUrl, "reports", reportId);
  });

exports.moderateFoundPersonImage = functions
  .region("us-central1")
  .database.ref("found_persons/{foundId}/photoUrl")
  .onWrite(async (change, context) => {
    const photoUrl = change.after.val();
    if (!photoUrl || typeof photoUrl !== "string") return null;

    const foundId = context.params.foundId;
    return moderateImageUrl(photoUrl, "found_persons", foundId);
  });

/**
 * يستخدم Google Cloud Vision SafeSearch API لفحص صورة
 * إذا كانت adult/violence يُخفى البلاغ ويُشعر الإدارة
 */
async function moderateImageUrl(photoUrl, collection, docId) {
  try {
    // جلب Vision API بشكل lazy لتجنب تحميله إن لم يُستخدَم
    const vision = require("@google-cloud/vision");
    const client = new vision.ImageAnnotatorClient();

    const [result] = await client.safeSearchDetection(photoUrl);
    const safe     = result.safeSearchAnnotation;

    if (!safe) return null;

    const THRESHOLD = "LIKELY"; // LIKELY أو VERY_LIKELY
    const levels    = ["LIKELY", "VERY_LIKELY"];

    const isHarmful = levels.includes(safe.adult)    ||
                      levels.includes(safe.violence)  ||
                      levels.includes(safe.racy);

    if (isHarmful) {
      console.warn(`[moderateImage] ⚠️ صورة ضارة في ${collection}/${docId}`,
        { adult: safe.adult, violence: safe.violence, racy: safe.racy });

      // إخفاء البلاغ فوراً
      await db.ref(`${collection}/${docId}`).update({
        imageModerated:   true,
        moderationResult: "rejected",
        moderationFlags:  {
          adult:    safe.adult    || "UNKNOWN",
          violence: safe.violence || "UNKNOWN",
          racy:     safe.racy     || "UNKNOWN",
        },
        status: "image_rejected",
      });

      // إشعار الإدارة
      await db.ref("admin_notifications").push({
        type:      "image_moderation_failed",
        message:   `صورة مرفوضة تلقائياً في ${collection}: ${docId}`,
        reportId:  collection === "reports" ? docId : null,
        foundId:   collection === "found_persons" ? docId : null,
        createdAt: Date.now(),
      });
    } else {
      // تمرير: علّم الصورة كـ safe
      await db.ref(`${collection}/${docId}`).update({
        imageModerated:   true,
        moderationResult: "approved",
      });
    }

    return null;
  } catch (err) {
    // إذا لم تكن Vision API مفعّلة أو حدث خطأ، نتجاوز بدون إيقاف البلاغ
    console.warn(`[moderateImage] ⚠️ Vision API غير متاحة، تم تجاوز الفحص: ${err.message}`);
    return null;
  }
}

// Helpers

function buildTitle(type) {
  switch (type) {
    case "match_confirmed":      return "✅ تأكيد تطابق!";
    case "report_matches_found": return "🔍 تطابق محتمل";
    case "found_matches_report": return "🎉 عُثر على مفقود!";
    case "sighting_match":       return "👁️ رؤية مطابقة";
    case "admin":                return "⚡ إشعار إداري";
    case "amber_alert":          return "🚨 تنبيه طفل مفقود";
    default:                     return "🔔 إشعار من سند";
  }
}

function chooseChannel(type) {
  if (type === "amber_alert") return "amber_alerts";
  if (type === "match_confirmed" || type === "found_matches_report") return "matches_channel";
  if (type === "admin" || type === "admin_face_match") return "admin_channel";
  return "general_channel";
}

/**
 * أعلى تشابه بين embedding البحث وأي بصمة مخزّنة في سجل (بلاغ/معثور).
 *
 * يفحص كِلا مخططَي التخزين الموجودين في قاعدة البيانات: الحقل الفردي
 * القديم "faceEmbedding" (المسار الذي يكتبه التطبيق فعلياً اليوم عند
 * إنشاء البلاغ) ومصفوفة "embeddings/*\/vector" (V3، تكتبها
 * FaceEmbeddingWorker في التطبيق إن استُخدمت مستقبلاً). بدون هذا الفحص
 * المزدوج، أي سجل تُخزَّن بصمته حصرياً في "embeddings" يبدو بلا بصمة
 * إطلاقاً لهذه الدالة السحابية فتُستبعد من المطابقة بصمت.
 */
function bestSimilarityAgainstRecord(queryEmb, record) {
  if (!queryEmb || !record) return 0;
  let best = 0;

  if (record.faceEmbedding) {
    best = Math.max(best, cosineSimilarityFromStrings(queryEmb, record.faceEmbedding));
  }

  if (record.embeddings && typeof record.embeddings === "object") {
    for (const key of Object.keys(record.embeddings)) {
      const entry = record.embeddings[key];
      const vec = entry && entry.vector;
      if (!vec) continue;
      best = Math.max(best, cosineSimilarityFromStrings(queryEmb, vec));
    }
  }

  return best;
}

/**
 * حساب cosine similarity بين embedding مخزّن كـ string (JSON array)
 */
function cosineSimilarityFromStrings(embA, embB) {
  try {
    // embeddings مخزنة كـ CSV ("0.1,0.2,...") وليس JSON array
    const a = typeof embA === "string" ? embA.split(",").map(Number) : embA;
    const b = typeof embB === "string" ? embB.split(",").map(Number) : embB;

    if (!Array.isArray(a) || !Array.isArray(b) || a.length !== b.length) return 0;

    let dot = 0, normA = 0, normB = 0;
    for (let i = 0; i < a.length; i++) {
      dot   += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }

    const denom = Math.sqrt(normA) * Math.sqrt(normB);
    return denom === 0 ? 0 : Math.max(0, Math.min(1, dot / denom));
  } catch (e) {
    return 0;
  }
}
