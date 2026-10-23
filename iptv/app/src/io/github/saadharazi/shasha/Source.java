package io.github.saadharazi.shasha;

import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** اشتراك: حساب Xtream Codes أو رابط قائمة M3U. ينحفظ في الجهاز بس. */
final class Source {
    String id, kind, name, server, user, pass, url;

    static Source xtream(String server, String user, String pass) {
        Source s = new Source();
        s.id = UUID.randomUUID().toString();
        s.kind = "xtream";
        s.server = cleanServer(server);
        s.user = user.trim();
        s.pass = pass.trim();
        return s;
    }

    static Source m3u(String url) {
        Source s = new Source();
        s.id = UUID.randomUUID().toString();
        s.kind = "m3u";
        s.url = withScheme(url.trim());
        return s;
    }

    /** رابط get.php فيه username وpassword = حساب Xtream (يجيب الأفلام والمسلسلات بشكل أحسن). */
    static Source fromXtreamLink(String link) {
        Uri u = Uri.parse(withScheme(link.trim()));
        String user = u.getQueryParameter("username"), pass = u.getQueryParameter("password");
        String path = u.getPath() == null ? "" : u.getPath();
        if (user == null || pass == null || !(path.endsWith("get.php") || path.endsWith("player_api.php"))) return null;
        String base = u.getScheme() + "://" + u.getEncodedAuthority() + path.substring(0, path.lastIndexOf('/'));
        return xtream(base, user, pass);
    }

    boolean xtream() {
        return "xtream".equals(kind);
    }

    String api(String action) {
        return server + "/player_api.php?username=" + Uri.encode(user) + "&password=" + Uri.encode(pass)
                + (action == null ? "" : "&action=" + action);
    }

    String host() {
        String h = Uri.parse(xtream() ? server : url).getHost();
        return h == null ? "اشتراك" : h;
    }

    String title() {
        return name != null && !name.isEmpty() ? name : host();
    }

    JSONObject json() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id).put("kind", kind).putOpt("name", name).putOpt("server", server).putOpt("user", user)
                    .putOpt("pass", pass).putOpt("url", url);
        } catch (JSONException ignored) {
        }
        return o;
    }

    static Source from(JSONObject o) {
        Source s = new Source();
        s.id = o.optString("id");
        s.kind = o.optString("kind");
        s.name = o.optString("name", null);
        s.server = o.optString("server", null);
        s.user = o.optString("user", null);
        s.pass = o.optString("pass", null);
        s.url = o.optString("url", null);
        return s;
    }

    private static String withScheme(String s) {
        return s.matches("(?i)^[a-z][a-z0-9+.-]*://.*") ? s : "http://" + s;
    }

    /** يقبل الرابط بأي شكل: بدون http، أو معه / في الآخر، أو معه player_api.php. */
    static String cleanServer(String s) {
        s = withScheme(s.trim());
        int q = s.indexOf('?');
        if (q > 0) s = s.substring(0, q);
        for (String tail : new String[]{"/player_api.php", "/get.php", "/c/"}) if (s.endsWith(tail)) s = s.substring(0, s.length() - tail.length());
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
