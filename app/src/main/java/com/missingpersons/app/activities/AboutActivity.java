package com.missingpersons.app.activities;

import android.os.Bundle;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.missingpersons.app.R;
import com.missingpersons.app.utils.LanguageHelper;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LanguageHelper.applyLanguage(base));
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        // [إصلاح 6 — Edge-to-Edge]
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bot);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("عن التطبيق");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        TextView tvAbout = findViewById(R.id.tv_about_content);
        tvAbout.setText(getLegalText());

        // [توحيد النسخة] تحديث TextView النسخة في بطاقة المعلومات من strings
        TextView tvVersion = findViewById(R.id.tv_about_version);
        if (tvVersion != null) {
            tvVersion.setText("v" + getString(R.string.app_version_name) + " — 2025");
        }
    }

    private String getLegalText() { // يستخدم getString() للنسخة الموحدة
        return
        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
        "🌟 رسالة التطبيق\n" +
        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +

        "\"تطبيق المفقودين\" منصة إنسانية غير ربحية تهدف إلى " +
        "المساعدة في إيجاد الأشخاص المفقودين، بما في ذلك:\n\n" +
        "• الأطفال التائهون أو المخطوفون\n" +
        "• ضحايا الحوادث والكوارث غير المعرّفين\n" +
        "• المحتجزون أو المفقودون في ظروف غامضة\n" +
        "• كبار السن المصابون بالنسيان\n\n" +

        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
        "⚖️ إخلاء المسؤولية القانوني\n" +
        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +

        "بموجب هذا الإشعار القانوني، يُقرّ المستخدم ويوافق على ما يلي:\n\n" +

        "أولاً: حدود مسؤولية التطبيق\n" +
        "يُقدّم هذا التطبيق خدماته كمنصة وسيطة للتواصل بين " +
        "المبلّغين والباحثين عن مفقودين فحسب. لا يتحمل التطبيق " +
        "أو مطوروه أو مشغلوه أي مسؤولية قانونية أو مدنية أو جنائية " +
        "عن:\n\n" +
        "- صحة المعلومات المُدرجة من قبل المستخدمين\n" +
        "- نتائج اللقاءات التي تتم بين المستخدمين\n" +
        "- أي ضرر مادي أو معنوي ينجم عن سوء الاستخدام\n" +
        "- انتهاك الخصوصية الناتج عن تصرفات المستخدمين\n\n" +

        "ثانياً: مسؤوليات المستخدم\n" +
        "يلتزم المستخدم بما يلي:\n" +
        "- التحقق من هوية الشخص الذي يتواصل معه قبل أي لقاء\n" +
        "- عدم لقاء أي شخص في مكان منعزل\n" +
        "- إجراء أي لقاء في أماكن عامة مزدحمة\n" +
        "- إبلاغ جهة أمنية معتمدة في حال الشك\n" +
        "- عدم طلب أموال أو مقابل مادي مقابل المعلومات\n" +
        "- عدم الابتزاز أو التهديد بأي شكل كان\n\n" +

        "ثالثاً: الإبلاغ الكاذب\n" +
        "يُعدّ تقديم بلاغات كاذبة أو مزورة جريمة يعاقب عليها " +
        "القانون. سيتم التعاون مع الجهات الأمنية المختصة لكشف " +
        "هوية مرتكبي هذه الأفعال وتسليم بياناتهم للجهات القضائية.\n\n" +

        "رابعاً: الخصوصية والبيانات\n" +
        "تُحفظ بيانات المستخدمين وفق سياسة الخصوصية المعتمدة. " +
        "لا تُباع البيانات أو تُشارك مع أطراف ثالثة إلا بأمر قضائي.\n\n" +

        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
        "⚠️ تحذير أمني مهم\n" +
        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
        "• لا تلتقِ بأشخاص مجهولين بمفردك\n" +
        "• لا تدفع أموالاً مقابل معلومات\n" +
        "• أبلغ الشرطة فوراً إذا طلب أحدهم فدية\n" +
        "• وثّق كل تواصل يجري عبر التطبيق\n\n" +

        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
        "📞 التواصل مع الإدارة\n" +
        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
        "للإبلاغ عن أي إساءة أو استفسار:\n" +
        "albaramost@gmail.com\n\n" +
        getString(R.string.version_full);
    }

    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}
