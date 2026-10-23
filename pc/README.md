<div dir="rtl">

# أداة شاشة للكمبيوتر

تلقى التلفزيونات في شبكة البيت وتعرفها، وتتحكم في تلفزيونات أندرويد وجوجل تي في من الكمبيوتر: معلومات الجهاز، تثبيت التطبيقات، عرض الشاشة، تسريع وتحسين الشبكة (وكل تغيير يرجع بزر)، وإعدادات واجهتي ونسخها الاحتياطية.

## التشغيل

ويندوز 10 أو 11، بدون تثبيت: افتح `Shasha.cmd`. تنفتح نافذة الأداة (Edge أو Chrome أو Brave)، وأول مرة تعرض تحمّل لك adb من Google وscrcpy من GitHub.

على التلفزيون فعّل «تصحيح الأخطاء» من خيارات المطوّر (الأداة تشرح الخطوات لما تختار تلفزيونك).

## كيف تشتغل

- `Shasha.ps1` خادم صغير على `localhost` بس (PowerShell 5.1)، يشغّل adb وscrcpy، ويقفل لحاله لما تقفل النافذة.
- `ui/index.html` الواجهة. كل طلب للخادم يحتاج رمز يتولّد كل مرة، فما يقدر موقع ثاني في المتصفح يطلب من الأداة شيء.
- البحث: adb، وmDNS (Google Cast وAndroid TV وadb اللاسلكي)، وSSDP (DLNA وDIAL)، والمنفذ 5555 في نفس الشبكة. ما يطلع شيء للإنترنت.
- الإعدادات وسجل التغييرات في `%APPDATA%\Shasha`، والنسخ الاحتياطية في `المستندات\Shasha\Backups`.

للتجربة من متصفح ثاني بدون نافذة: `SHASHA_NO_WINDOW=1` قبل التشغيل، والأداة تطبع العنوان.

</div>

---

**Shasha PC tool**: finds TVs on your home network (adb, mDNS/Cast, SSDP/DLNA) and manages Android/Google TVs over adb: device info, APK installs, screen mirroring (scrcpy), reversible speed and network tweaks, and Wajhati launcher settings and backups. Windows 10/11, no install: run `Shasha.cmd`. A localhost-only PowerShell server with a per-run token; it closes with its window.
