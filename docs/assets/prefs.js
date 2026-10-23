// إعدادات الزائر (المظهر، الصفحة المفتوحة، شكل المعاينة) تنحفظ في كوكيز متصفحه بس لمدة سنة، وما تنرسل لأي مكان.
(function () {
  var path = location.pathname.indexOf('/shasha/') === 0 ? '/shasha/' : '/';
  window.prefs = {
    get: function (k, d) {
      var m = document.cookie.match('(?:^|; )shasha_' + k + '=([^;]*)');
      return m ? decodeURIComponent(m[1]) : d;
    },
    set: function (k, v) {
      document.cookie = 'shasha_' + k + '=' + encodeURIComponent(v) + '; max-age=31536000; path=' + path +
        '; SameSite=Lax' + (location.protocol === 'https:' ? '; Secure' : '');
    }
  };
  // المظهر قبل ما ترسم الصفحة، عشان ما تومض
  var t = window.prefs.get('theme', 'auto');
  if (t === 'light' || t === 'dark') document.documentElement.setAttribute('data-theme', t);
})();
