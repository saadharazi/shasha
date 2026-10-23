# قواعد R8: نخلي نشاطات التطبيق (من الـ manifest)، والأسماء على حالها عشان تقارير الأعطال تنقرا (الكود مفتوح أصلًا)
-dontobfuscate
-keep class io.github.saadharazi.shasha.*Activity { <init>(); }
# موارد إشعارات التحميل حق ExoPlayer ما نستخدمها، فما نبني R حقها
-dontwarn androidx.media3.exoplayer.R*
-dontwarn kotlin.**
# ExoPlayer يستخدم واجهات أندرويد أحدث من android-34 (مثل LoudnessCodecController في 35) بعد ما يتأكد من نسخة الجهاز
-dontwarn android.**
