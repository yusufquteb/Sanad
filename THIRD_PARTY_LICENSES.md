# تراخيص طرف ثالث — Third-Party Licenses

هذا الملف يوثّق أصل وترخيص الموارد الخارجية (غير المكتوبة لأجل هذا المشروع)
المضمَّنة في تطبيق سند | Sanad.

---

## نموذج التعرّف على الوجوه — mobilefacenet.tflite

**الملف**: `app/src/main/assets/mobilefacenet.tflite`

**المصدر**: [MCarlomagno/FaceRecognitionAuth](https://github.com/MCarlomagno/FaceRecognitionAuth)
(`assets/mobilefacenet.tflite`)

تم التحقق من المطابقة عبر بصمة SHA-256 (تطابق تام، نفس الملف حرفياً):
```
be4bc7cfc53f7bc336d0f28b1ab92535f618c913a422b683210750f6b5354854
```

**الترخيص**: BSD 3-Clause License

```
BSD 3-Clause License

Copyright (c) 2020, Marcos Carlomagno
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

**الخلاصة**: ترخيص BSD-3-Clause **مجاني ومفتوح المصدر**، ويسمح صراحةً
بالاستخدام التجاري وغير التجاري، والتعديل، وإعادة التوزيع — بلا أي رسوم أو
قيود على نوع الاستخدام. الشرط الوحيد هو الإبقاء على إشعار حقوق النشر أعلاه
(وهذا الملف يفي بذلك). **لا حاجة لأي إذن إضافي من صاحب الحقوق لاستخدامه في
تطبيق سند، تجارياً كان أو غير ربحي.**

**يُنصَح** بإضافة نفس هذا الإشعار أيضاً إلى قسم "التراخيص مفتوحة المصدر"
(Open Source Licenses) في صفحة "عن التطبيق" داخل التطبيق نفسه، وفي وصف
Google Play إن وُجد قسم مماثل — هذا ملف توثيق داخلي للمستودع، وليس بديلاً
عن الإفصاح للمستخدم النهائي إن كانت سياسة المتجر تتطلب ذلك.

---

## بدائل تم فحصها ولم تُعتمَد (للرجوع المستقبلي)

| المصدر | الترخيص | لماذا لم يُعتمَد |
|---|---|---|
| [mobilesec/arcface-tensorflowlite](https://github.com/mobilesec/arcface-tensorflowlite) | EUPL 1.2 (مفتوح تجارياً) | دقة أعلى (96.9% LFW)، لكن الوزن الفعلي لم يُختبر بعد (يحمَّل من رابط جامعي خارجي) |
| [deepinsight/insightface](https://github.com/deepinsight/insightface) (buffalo_l/buffalo_sc) | **بحثي غير تجاري فقط** — يتطلب إذناً من `recognition-oss-pack@insightface.ai` | أعلى دقة (99.7%+ LFW)، لكن الترخيص يمنع الاستخدام في تطبيق مُوزَّع فعلياً بدون إذن مباشر |
| adaface_ir18_112.tflite (الملف المُستبدَل) | غير معروف | ثبت عبر اختبار فعلي أنه ليس نموذج تعرّف وجوه صالحاً إطلاقاً (راجع `AdaFaceRecognizer.java`) |
