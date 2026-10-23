package io.github.saadharazi.shasha;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * إضافة الاشتراك من الجوال: صفحة صغيرة على شبكة البيت فيها نموذج، والتلفزيون يتحقق من البيانات ويحفظها.
 * تشتغل بس وشاشة «أضف اشتراك» مفتوحة، وتنقفل معها.
 */
final class PhoneForm {
    interface Handler {
        /** يتحقق ويحفظ (من خيط الاتصال)، ويرجّع رسالة الخطأ أو null لو تمام. */
        String submit(Map<String, String> form);
    }

    private ServerSocket server;
    private volatile boolean on;

    /** يرجّع العنوان اللي تفتحه من الجوال، أو null لو ما فيه شبكة. */
    String start(final Handler handler) {
        String ip = localIp();
        if (ip == null) return null;
        try {
            try {
                server = new ServerSocket(8642);
            } catch (Exception busy) {
                server = new ServerSocket(0);
            }
        } catch (Exception e) {
            return null;
        }
        on = true;
        Thread t = new Thread(() -> {
            while (on) {
                try {
                    final Socket s = server.accept();
                    new Thread(() -> serve(s, handler)).start();
                } catch (Exception e) {
                    if (!on) return;
                }
            }
        }, "phone-form");
        t.setDaemon(true);
        t.start();
        return "http://" + ip + (server.getLocalPort() == 80 ? "" : ":" + server.getLocalPort());
    }

    void stop() {
        on = false;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
    }

    private static void serve(Socket s, Handler handler) {
        try (Socket sock = s) {
            sock.setSoTimeout(15000);
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
            String first = in.readLine();
            if (first == null) return;
            int length = 0;
            for (String line; (line = in.readLine()) != null && !line.isEmpty(); )
                if (line.toLowerCase().startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
            String html;
            if (first.startsWith("POST") && length > 0 && length < 8192) {
                char[] body = new char[length];
                int got = 0;
                while (got < length) {
                    int n = in.read(body, got, length - got);
                    if (n < 0) break;
                    got += n;
                }
                String error = handler.submit(parse(new String(body, 0, got)));
                html = page(error == null ? "<div class=ok><b>تمام، انضاف الاشتراك</b>ارجع للتلفزيون، القنوات تتجهز الحين.</div>"
                        : "<div class=err><b>ما ضبط</b>" + esc(error) + "</div>" + FORM);
            } else html = page(FORM);
            byte[] b = html.getBytes("UTF-8");
            OutputStream out = sock.getOutputStream();
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: " + b.length
                    + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            out.write(b);
            out.flush();
        } catch (Exception ignored) {
        }
    }

    static Map<String, String> parse(String body) {
        Map<String, String> m = new HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                m.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"), URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            } catch (Exception ignored) {
            }
        }
        return m;
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String page(String content) {
        return "<!doctype html><html lang=ar dir=rtl><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<title>شاشة: أضف اشتراك</title><style>"
                + "body{margin:0;background:#0F1012;color:#F3F3F0;font:17px/1.6 system-ui,sans-serif}main{max-width:460px;margin:0 auto;padding:28px 18px 40px}"
                + "h1{font-size:26px;margin:0 0 4px}p{color:#a8aaa8;margin:0 0 20px}label{display:block;font-size:14px;color:#a8aaa8;margin:16px 0 6px}"
                + "input{width:100%;box-sizing:border-box;height:50px;border-radius:14px;border:1px solid #2b2e33;background:#17191C;color:#F3F3F0;padding:0 14px;font:inherit;direction:ltr;text-align:right}"
                + "input:focus{outline:none;border-color:#F3F3F0}.seg{display:flex;background:#17191C;border-radius:16px;padding:4px;gap:4px}"
                + ".seg label{flex:1;margin:0;text-align:center;padding:11px 0;border-radius:12px;color:#F3F3F0;font-size:16px}.seg input{display:none}"
                + ".seg label.on{background:#2a2d32}.mf{display:none}form.m .mf{display:block}form.m .x{display:none}"
                + "button{width:100%;height:54px;margin-top:24px;border:0;border-radius:27px;background:#F3F3F0;color:#0F1012;font:600 18px system-ui,sans-serif}"
                + ".ok,.err{border-radius:16px;padding:16px 18px;margin-bottom:18px}.ok{background:#1b2a22}.err{background:#2e1f1e}.ok b,.err b{display:block;font-size:19px}"
                + "small{display:block;color:#73767a;margin-top:26px;font-size:13px}</style></head><body><main>"
                + "<h1>أضف اشتراك للتلفزيون</h1><p>البيانات تروح لتلفزيونك بس، على شبكة البيت.</p>" + content
                + "<small>© 2026 شاشة، للتواصل " + HomeActivity.CONTACT + "</small></main></body></html>";
    }

    private static final String FORM = "<form method=post>"
            + "<div class=seg><label class=on><input type=radio name=kind value=xtream checked>حساب Xtream</label><label><input type=radio name=kind value=m3u>رابط M3U</label></div>"
            + "<div class=x><label>رابط السيرفر</label><input name=server placeholder='http://example.com:8080' autocomplete=off>"
            + "<label>اسم المستخدم</label><input name=user autocomplete=off autocapitalize=none>"
            + "<label>كلمة المرور</label><input name=pass type=password autocomplete=off></div>"
            + "<div class=mf><label>رابط القائمة</label><input name=url placeholder='http://example.com/playlist.m3u' autocomplete=off></div>"
            + "<label>اسم يميّزه (اختياري)</label><input name=name style='direction:rtl'>"
            + "<button>احفظ في التلفزيون</button></form>"
            // نبدّل بين الحقول بسكربت صغير (يشتغل حتى في المتصفحات القديمة)
            + "<script>var f=document.forms[0];f.onchange=function(){var m=f.kind.value=='m3u';f.className=m?'m':'';"
            + "var l=f.querySelectorAll('.seg label');l[0].className=m?'':'on';l[1].className=m?'on':''}</script>";
}
