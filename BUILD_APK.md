# ملفات وخطوات تحويل مشروع FolderPush إلى APK

يحتوي المشروع الآن على كافة الملفات والإعدادات المطلوبة لبناء ملف **APK** سواء عبر **GitHub Actions** أو محلياً:

## 1. قائمة الملفات المطلوبة لتحويل المشروع إلى APK
1. `build.gradle.kts` — تعريف إضافات Gradle الأساسية على مستوى المشروع.
2. `settings.gradle.kts` — مستودعات التبعيات (`google()`, `mavenCentral()`) واسم المشروع وتضمين `:app`.
3. `gradle/libs.versions.toml` — كتالوج الإصدارات والمكتبات (AGP `9.1.1`, Kotlin `2.4.21`, Compose, Room, OkHttp).
4. `gradle/wrapper/gradle-wrapper.properties` — تحديد إصدار Gradle `9.3.1`.
5. `gradle.properties` — إعدادات الذاكرة والترميز وGradle.
6. `app/build.gradle.kts` — إعدادات التطبيق (`applicationId`, `minSdk = 24`, `targetSdk = 36`, `signingConfigs`, `buildTypes`).
7. `app/proguard-rules.pro` — قواعد الحفاظ على الكلاسات والمكونات أثناء بناء APK.
8. `app/src/main/AndroidManifest.xml` — التصريحات والأنشطة والخدمات ومزود المستندات.
9. `debug.keystore.base64` — مفتاح التوقيع التجريبي المشفر بـ Base64 لبناء `app-debug.apk`.
10. `.env.example` — ملف المتغيرات الافتراضي المطلوب لإضافة `secrets-gradle-plugin`.
11. `.github/workflows/build-apk.yml` — ملف GitHub Actions لبناء `FolderPush-debug.apk` و`FolderPush-release.apk` تلقائياً ورفعهما كـ Artifacts.
12. `scripts/build-apk.sh` — سكريبت تشغيل سريع لبناء APK من سطر الأوامر.

## 2. البناء تلقائياً عبر GitHub Actions
1. ارفع المشروع إلى مستودع GitHub.
2. سيعمل سير العمل `.github/workflows/build-apk.yml` تلقائياً عند أي `push` أو يدوياً من تبويب **Actions → Build Android APK → Run workflow**.
3. بعد الانتهاء، افتح نتيجة التشغيل وحمّل حزمة **`FolderPush-APK`** من قسم **Artifacts** للحصول على:
   - `FolderPush-debug.apk`
   - `FolderPush-release.apk`
4. (اختياري للنشر الدائم على المتجر) أضف الأسرار التالية في **Settings → Secrets and variables → Actions**:
   - `RELEASE_KEYSTORE_BASE64`
   - `STORE_PASSWORD`
   - `KEY_PASSWORD`

## 3. البناء محلياً عبر سطر الأوامر
```bash
bash scripts/build-apk.sh debug
# أو لبناء النسختين معاً:
bash scripts/build-apk.sh both
```
سيتم توليد الملفات داخل مجلد `apk-output/`.
