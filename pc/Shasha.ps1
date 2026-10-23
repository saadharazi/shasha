# شاشة: أداة لتلفزيونات أندرويد من الكمبيوتر. خادم محلي صغير يعرض الواجهة في نافذة Edge، ويشغّل adb وscrcpy.
# Windows PowerShell 5.1 وما بعده، بدون تثبيت. شغّلها من Shasha.cmd
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'  # شريط التقدّم يبطّئ التحميل في PowerShell 5.1
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}

$Version = '1.2'
$Here = $PSScriptRoot
$Tools = Join-Path $Here 'tools'
$DataDir = Join-Path $env:APPDATA 'Shasha'
$StateFile = Join-Path $DataDir 'state.json'
$BackupDir = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Shasha\Backups'
$Wajhati = 'com.wajhati.tv'
$WajhatiHome = 'com.wajhati.tv/.HomeActivity'
$WajhatiDream = 'com.wajhati.tv/.WajhatiDream'
$WajhatiWatcher = 'com.wajhati.tv/com.wajhati.tv.HomeWatcherService'
$IptvPkg = 'io.github.saadharazi.shasha'
$Site = 'https://saadharazi.github.io/shasha'
# تطبيقات عرض المحلات والإعلانات (تلفزيونات Skyworth/Coocaa وغيرها): تشتغل بالخلفية بدون فايدة في البيت
$Bloat = @(
    @{ pkg = 'com.coocaa.demonow.lightining'; name = 'عرض المحلات (Demo)' },
    @{ pkg = 'com.smartdevice.epopplayer'; name = 'مشغّل إعلانات المحلات (ePop)' },
    @{ pkg = 'fusion.android.tv.demo'; name = 'عرض تجريبي' },
    @{ pkg = 'com.smartdevice.recommendation'; name = 'توصيات وإعلانات الواجهة الأصلية' }
)
[void](New-Item -ItemType Directory -Force -Path $DataDir)

# ---------------- البحث في الشبكة: mDNS (Cast وريموت Android TV وadb اللاسلكي)، SSDP (DLNA وDIAL)، والمنفذ 5555 ----------------

$finderCode = @'
using System;
using System.Collections.Generic;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Threading;

public class ShashaDevice
{
    public string Name = "", Ip = "", Model = "";
    public int AdbPort, PairPort;
    public bool Legacy, IsTv, IsCast;
}

public static class ShashaFinder
{
    const string Cast = "_googlecast._tcp.local";
    const string Remote = "_androidtvremote2._tcp.local";
    const string AdbTls = "_adb-tls-connect._tcp.local";
    const string AdbPair = "_adb-tls-pairing._tcp.local";
    const string AdbOld = "_adb._tcp.local";
    static readonly string[] Services = { Cast, Remote, AdbTls, AdbPair, AdbOld };
    static readonly IPAddress Group = IPAddress.Parse("224.0.0.251");

    class Srv { public string Target; public int Port; }
    class State
    {
        public Dictionary<string, HashSet<string>> Ptr = new Dictionary<string, HashSet<string>>(StringComparer.OrdinalIgnoreCase);
        public Dictionary<string, Srv> SrvOf = new Dictionary<string, Srv>(StringComparer.OrdinalIgnoreCase);
        public Dictionary<string, Dictionary<string, string>> Txt = new Dictionary<string, Dictionary<string, string>>(StringComparer.OrdinalIgnoreCase);
        public Dictionary<string, string> A = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        public Dictionary<string, string> From = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
    }

    public static List<IPAddress> LocalAddresses()
    {
        List<IPAddress> list = new List<IPAddress>();
        foreach (NetworkInterface ni in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (ni.OperationalStatus != OperationalStatus.Up || ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
            foreach (UnicastIPAddressInformation ua in ni.GetIPProperties().UnicastAddresses)
            {
                if (ua.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                byte[] b = ua.Address.GetAddressBytes();
                if (b[0] == 169 && b[1] == 254) continue;
                list.Add(ua.Address);
            }
        }
        return list;
    }

    static List<Socket> Senders()
    {
        List<Socket> socks = new List<Socket>();
        foreach (IPAddress ip in LocalAddresses())
        {
            try
            {
                Socket s = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
                s.Bind(new IPEndPoint(ip, 0));
                s.SetSocketOption(SocketOptionLevel.IP, SocketOptionName.MulticastInterface, ip.GetAddressBytes());
                s.SetSocketOption(SocketOptionLevel.IP, SocketOptionName.MulticastTimeToLive, 4);
                socks.Add(s);
            }
            catch (Exception) { }
        }
        return socks;
    }

    // يرسل السؤال كم مرة ويجمع الردود لين تخلص المدة
    static void Ask(List<Socket> socks, byte[] query, IPEndPoint dest, int millis, Action<byte[], string> onReply)
    {
        DateTime start = DateTime.UtcNow;
        int sent = 0;
        int[] sendAt = { 0, 600, 1400 };
        byte[] buf = new byte[9000];
        while (true)
        {
            double el = (DateTime.UtcNow - start).TotalMilliseconds;
            if (el > millis) break;
            while (sent < sendAt.Length && el >= sendAt[sent])
            {
                foreach (Socket s in socks)
                {
                    if (((IPEndPoint)s.LocalEndPoint).Port == dest.Port) continue;
                    try { s.SendTo(query, dest); } catch (Exception) { }
                }
                sent++;
            }
            if (socks.Count == 0) { Thread.Sleep(50); continue; }
            List<Socket> ready = new List<Socket>(socks);
            try { Socket.Select(ready, null, null, 100000); } catch (Exception) { Thread.Sleep(50); continue; }
            foreach (Socket s in ready)
            {
                try
                {
                    EndPoint from = new IPEndPoint(IPAddress.Any, 0);
                    int n = s.ReceiveFrom(buf, ref from);
                    byte[] pkt = new byte[n];
                    Array.Copy(buf, pkt, n);
                    onReply(pkt, ((IPEndPoint)from).Address.ToString());
                }
                catch (Exception) { }
            }
        }
        foreach (Socket s in socks) { try { s.Close(); } catch (Exception) { } }
    }

    public static List<ShashaDevice> Mdns(int millis)
    {
        State st = new State();
        List<Socket> socks = Senders();
        try
        {
            // بعض الأجهزة ترد للمجموعة بدل ما ترد علينا مباشرة
            Socket m = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
            m.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
            m.Bind(new IPEndPoint(IPAddress.Any, 5353));
            foreach (IPAddress ip in LocalAddresses())
            {
                try { m.SetSocketOption(SocketOptionLevel.IP, SocketOptionName.AddMembership, new MulticastOption(Group, ip)); }
                catch (Exception) { }
            }
            socks.Add(m);
        }
        catch (Exception) { }
        Ask(socks, BuildQuery(), new IPEndPoint(Group, 5353), millis, delegate (byte[] p, string from) { Parse(p, from, st); });
        return Build(st);
    }

    // SSDP: تلفزيونات سامسونج وإل جي، ومشغّلات DLNA، وDIAL (يوتيوب على التلفزيون). يرجّع [ip, location, server, st]
    public static List<string[]> Ssdp(int millis)
    {
        Dictionary<string, string[]> seen = new Dictionary<string, string[]>();
        byte[] q = Encoding.ASCII.GetBytes("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n");
        Ask(Senders(), q, new IPEndPoint(IPAddress.Parse("239.255.255.250"), 1900), millis, delegate (byte[] p, string from)
        {
            string loc = "", server = "", st = "";
            foreach (string line in Encoding.UTF8.GetString(p).Split('\n'))
            {
                int c = line.IndexOf(':');
                if (c < 1) continue;
                string k = line.Substring(0, c).Trim().ToUpperInvariant(), v = line.Substring(c + 1).Trim();
                if (k == "LOCATION") loc = v; else if (k == "SERVER") server = v; else if (k == "ST") st = v;
            }
            if (loc.Length == 0) return;
            lock (seen) { if (!seen.ContainsKey(loc)) seen[loc] = new string[] { from, loc, server, st }; }
        });
        return new List<string[]>(seen.Values);
    }

    static byte[] BuildQuery()
    {
        List<byte> q = new List<byte>();
        q.AddRange(new byte[] { 0, 0, 0, 0, 0, (byte)Services.Length, 0, 0, 0, 0, 0, 0 });
        foreach (string svc in Services)
        {
            foreach (string label in svc.Split('.'))
            {
                byte[] lb = Encoding.UTF8.GetBytes(label);
                q.Add((byte)lb.Length);
                q.AddRange(lb);
            }
            q.Add(0);
            q.AddRange(new byte[] { 0, 12, 0x80, 1 });
        }
        return q.ToArray();
    }

    static string ReadName(byte[] p, ref int pos)
    {
        StringBuilder sb = new StringBuilder();
        int jumps = 0, cur = pos;
        bool jumped = false;
        while (true)
        {
            if (cur >= p.Length) throw new Exception("bad name");
            int len = p[cur];
            if (len == 0) { cur++; break; }
            if ((len & 0xC0) == 0xC0)
            {
                if (cur + 1 >= p.Length || ++jumps > 20) throw new Exception("bad pointer");
                int ptr = ((len & 0x3F) << 8) | p[cur + 1];
                if (!jumped) pos = cur + 2;
                jumped = true;
                cur = ptr;
                continue;
            }
            if (cur + 1 + len > p.Length) throw new Exception("bad label");
            if (sb.Length > 0) sb.Append('.');
            sb.Append(Encoding.UTF8.GetString(p, cur + 1, len));
            cur += 1 + len;
        }
        if (!jumped) pos = cur;
        return sb.ToString();
    }

    static int U16(byte[] p, int i) { return (p[i] << 8) | p[i + 1]; }

    static void Parse(byte[] p, string fromIp, State st)
    {
        if (p.Length < 12 || (p[2] & 0x80) == 0) return;
        int qd = U16(p, 4), an = U16(p, 6), ns = U16(p, 8), ar = U16(p, 10);
        int pos = 12;
        for (int i = 0; i < qd; i++) { ReadName(p, ref pos); pos += 4; }
        for (int i = 0; i < an + ns + ar; i++)
        {
            string name = ReadName(p, ref pos);
            if (pos + 10 > p.Length) return;
            int type = U16(p, pos), rdlen = U16(p, pos + 8), rd = pos + 10;
            pos = rd + rdlen;
            if (pos > p.Length) return;
            if (type == 12)
            {
                int q = rd;
                string inst = ReadName(p, ref q);
                HashSet<string> set;
                if (!st.Ptr.TryGetValue(name, out set)) { set = new HashSet<string>(StringComparer.OrdinalIgnoreCase); st.Ptr[name] = set; }
                set.Add(inst);
                st.From[inst] = fromIp;
            }
            else if (type == 33 && rdlen >= 7)
            {
                int q = rd + 6;
                Srv s = new Srv();
                s.Port = U16(p, rd + 4);
                s.Target = ReadName(p, ref q);
                st.SrvOf[name] = s;
                if (!st.From.ContainsKey(name)) st.From[name] = fromIp;
            }
            else if (type == 16)
            {
                Dictionary<string, string> kv = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
                int q = rd;
                while (q < rd + rdlen)
                {
                    int l = p[q];
                    if (q + 1 + l > rd + rdlen) break;
                    string s = Encoding.UTF8.GetString(p, q + 1, l);
                    int eq = s.IndexOf('=');
                    if (eq > 0) kv[s.Substring(0, eq)] = s.Substring(eq + 1);
                    q += 1 + l;
                }
                st.Txt[name] = kv;
            }
            else if (type == 1 && rdlen == 4)
            {
                st.A[name] = p[rd] + "." + p[rd + 1] + "." + p[rd + 2] + "." + p[rd + 3];
            }
        }
    }

    static List<ShashaDevice> Build(State st)
    {
        Dictionary<string, ShashaDevice> byIp = new Dictionary<string, ShashaDevice>();
        foreach (string svc in Services)
        {
            HashSet<string> set;
            if (!st.Ptr.TryGetValue(svc, out set)) continue;
            foreach (string inst in set)
            {
                Srv srv;
                st.SrvOf.TryGetValue(inst, out srv);
                string ip = null;
                if (srv != null && srv.Target != null) st.A.TryGetValue(srv.Target, out ip);
                if (ip == null) st.From.TryGetValue(inst, out ip);
                if (string.IsNullOrEmpty(ip)) continue;
                ShashaDevice d;
                if (!byIp.TryGetValue(ip, out d)) { d = new ShashaDevice(); d.Ip = ip; byIp[ip] = d; }
                Dictionary<string, string> txt;
                st.Txt.TryGetValue(inst, out txt);
                string label = inst.EndsWith("." + svc, StringComparison.OrdinalIgnoreCase) ? inst.Substring(0, inst.Length - svc.Length - 1) : inst;
                string v;
                if (svc == Cast)
                {
                    d.IsTv = d.IsCast = true;
                    if (txt != null && txt.TryGetValue("fn", out v) && v.Length > 0) d.Name = v;
                    if (txt != null && txt.TryGetValue("md", out v)) d.Model = v;
                }
                else if (svc == Remote) { d.IsTv = true; if (d.Name.Length == 0) d.Name = label; }
                else if (svc == AdbTls) { if (srv != null) d.AdbPort = srv.Port; }
                else if (svc == AdbPair) { if (srv != null) d.PairPort = srv.Port; }
                else if (svc == AdbOld) { d.Legacy = true; }
            }
        }
        return new List<ShashaDevice>(byIp.Values);
    }

    // الأجهزة اللي فاتحة منفذ (مثل 5555 لتصحيح الأخطاء عبر الشبكة) في نفس الشبكة (/24)
    public static List<string> ScanPort(int port, int timeoutMs)
    {
        List<string> found = new List<string>();
        List<KeyValuePair<string, Socket>> pending = new List<KeyValuePair<string, Socket>>();
        List<IAsyncResult> results = new List<IAsyncResult>();
        HashSet<string> mine = new HashSet<string>();
        foreach (IPAddress local in LocalAddresses()) mine.Add(local.ToString());
        foreach (IPAddress local in LocalAddresses())
        {
            byte[] b = local.GetAddressBytes();
            for (int h = 1; h < 255; h++)
            {
                string ip = b[0] + "." + b[1] + "." + b[2] + "." + h;
                if (mine.Contains(ip)) continue;
                try
                {
                    Socket s = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
                    results.Add(s.BeginConnect(IPAddress.Parse(ip), port, null, null));
                    pending.Add(new KeyValuePair<string, Socket>(ip, s));
                }
                catch (Exception) { }
            }
        }
        DateTime end = DateTime.UtcNow.AddMilliseconds(timeoutMs);
        for (int i = 0; i < pending.Count; i++)
        {
            int left = (int)Math.Max(0, (end - DateTime.UtcNow).TotalMilliseconds);
            try { if (results[i].AsyncWaitHandle.WaitOne(left) && pending[i].Value.Connected) found.Add(pending[i].Key); }
            catch (Exception) { }
        }
        foreach (KeyValuePair<string, Socket> kv in pending) { try { kv.Value.Close(); } catch (Exception) { } }
        return found;
    }
}
'@
if (-not ('ShashaFinder' -as [type])) { Add-Type -TypeDefinition $finderCode -Language CSharp }

# ---------------- الحالة المحفوظة (في %APPDATA%\Shasha) ----------------

# ConvertFrom-Json في 5.1 يرجّع PSCustomObject؛ نحوّله لجداول عشان نعدّل فيه بسهولة
function To-Hash($o) {
    if ($o -is [System.Management.Automation.PSCustomObject]) {
        $h = @{}
        foreach ($p in $o.PSObject.Properties) { $h[$p.Name] = To-Hash $p.Value }
        return $h
    }
    if ($o -is [System.Collections.IList]) { return , @($o | ForEach-Object { To-Hash $_ }) }
    return $o
}

$script:state = @{}
if (Test-Path $StateFile) { try { $script:state = To-Hash (Get-Content -Raw -Encoding UTF8 $StateFile | ConvertFrom-Json) } catch {} }
foreach ($k in 'settings', 'last', 'launcher') { if (-not ($script:state[$k] -is [hashtable])) { $script:state[$k] = @{} } }
foreach ($k in 'installs', 'changes') { if ($null -eq $script:state[$k]) { $script:state[$k] = @() } else { $script:state[$k] = @($script:state[$k]) } }
$S = $script:state.settings
foreach ($k in 'adb', 'cast', 'dlna', 'autoConnect') { if ($null -eq $S[$k]) { $S[$k] = $true } }

function Save-State {
    [IO.File]::WriteAllText($StateFile, ($script:state | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding $false))
}

# ---------------- adb وscrcpy ----------------

function Quote-Arg([string]$a) {
    if ($a -eq '') { return '""' }
    if ($a -notmatch '[\s"]') { return $a }
    return '"' + ($a -replace '"', '\"') + '"'
}

function Find-Exe([string]$setting, [string]$name) {
    $p = [string]$S[$setting]
    if ($p -and (Test-Path -LiteralPath $p)) { return (Resolve-Path -LiteralPath $p).Path }
    $local = Get-ChildItem -Path $Tools -Filter $name -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($local) { return $local.FullName }
    # مجلد واجهتي: الأداة في pc، وadb وscrcpy جنبها في platform-tools وscrcpy
    foreach ($d in 'platform-tools', 'scrcpy') {
        $f = Join-Path (Join-Path (Split-Path $Here) $d) $name
        if (Test-Path -LiteralPath $f) { return $f }
    }
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

$script:adb = $null
$script:serial = $null

function Ensure-Adb {
    $found = Find-Exe 'adbPath' 'adb.exe'
    if (-not $found) { throw 'noadb' }
    if ($found -ne $script:adb) {
        $script:adb = $found
        # نشغّل خادم adb مرة بدون ما نلتقط مخرجاته (الخادم يورث المخرجات ويعلّق القراءة)
        Start-Process -FilePath $found -ArgumentList 'start-server' -WindowStyle Hidden -Wait
    }
}

function Adb([string[]]$AdbArgs, [int]$Sec = 60) {
    Ensure-Adb
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $script:adb
    $psi.Arguments = (($AdbArgs | ForEach-Object { Quote-Arg $_ }) -join ' ')
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    $psi.StandardOutputEncoding = [Text.Encoding]::UTF8
    $psi.StandardErrorEncoding = [Text.Encoding]::UTF8
    $p = [Diagnostics.Process]::Start($psi)
    $o = $p.StandardOutput.ReadToEndAsync()
    $e = $p.StandardError.ReadToEndAsync()
    if (-not $p.WaitForExit($Sec * 1000)) { try { $p.Kill() } catch {} }
    [void]$p.WaitForExit(3000)
    return ($o.Result + "`n" + $e.Result).Trim()
}

function Need-Device {
    if (-not $script:serial) { throw 'nodevice' }
    $st = Adb @('-s', $script:serial, 'get-state') 8
    if ($st -notmatch '^device') { throw 'nodevice' }
}

function Sh([string]$Cmd, [int]$Sec = 30) { return Adb @('-s', $script:serial, 'shell', $Cmd) $Sec }

# عدة أوامر في رحلة وحدة للتلفزيون، والنتيجة قطع بنفس الترتيب
function Sh-Many([string[]]$Cmds, [int]$Sec = 40) {
    $out = Sh (($Cmds | ForEach-Object { "$_ 2>/dev/null; echo '@@'" }) -join '; ') $Sec
    $parts = @($out -split "@@\r?\n?")
    return , @($parts | ForEach-Object { $_.Trim() })
}

function Get-Setting([string]$ns, [string]$key) {
    $v = (Sh "settings get $ns $key" 10).Trim()
    if ($v -eq 'null') { return '' }
    return $v
}

# قيمة فاضية = نحذف الإعداد ويرجع لافتراضي التلفزيون
function Set-Setting([string]$ns, [string]$key, [string]$val) {
    # القيم ثابتة عندنا أو جايه من التلفزيون نفسه (بدون مسافات)
    if ($val -eq '') { [void](Sh "settings delete $ns $key" 10) } else { [void](Sh "settings put $ns $key $val" 10) }
}

function Has-Package([string]$pkg) { return ((Sh "pm list packages $pkg" 15) -match ('package:' + [regex]::Escape($pkg) + '(\s|$)')) }

function Version-Of([string]$pkg) {
    $m = [regex]::Match((Sh "dumpsys package $pkg | grep -m1 versionName" 15), 'versionName=(\S+)')
    if ($m.Success) { return $m.Groups[1].Value }
    return ''
}

# ---------------- واجهتي على التلفزيون ----------------

# ملف واجهتي: جنب الأداة، أو في المجلد اللي فوقها (مجلد واجهتي فيه wajhati.apk)
function Wajhati-Apk {
    foreach ($p in @((Join-Path $Here 'wajhati.apk'), (Join-Path (Split-Path $Here) 'wajhati.apk'))) {
        if (Test-Path -LiteralPath $p) { return (Resolve-Path -LiteralPath $p).Path }
    }
    return $null
}

# الواجهة اللي تفتح مع زر الرئيسية الحين (حزمة/نشاط)
function Resolve-Home {
    $out = Sh 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' 20
    $lines = @($out -split "`r?`n" | Where-Object { $_.Trim() -ne '' })
    if ($lines.Count -eq 0) { return '' }
    return $lines[-1].Trim()
}

function Is-Ours([string]$comp) { return ($comp -match [regex]::Escape($Wajhati)) }

# إعدادات واجهتي تروح لها كـ JSON في الأمر اللي يفتحها
function Wajhati-Cfg($cfg, [bool]$reload = $false) {
    $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(($cfg | ConvertTo-Json -Compress)))
    $a = @('-s', $script:serial, 'shell', 'am', 'start', '-n', $WajhatiHome, '--es', 'cfg', $b64)
    if ($reload) { $a += @('--ez', 'reload', 'true') }
    $r = Adb $a 30
    if ($r -match 'Error') { throw $r }
}

function Need-Wajhati {
    Need-Device
    if (-not (Has-Package $Wajhati)) { throw 'واجهتي مو مثبّتة على هذا التلفزيون' }
}

# نشيل خدمة واجهتي من خدمات إمكانية الوصول (ونخلي الباقي)
function Drop-Watcher {
    $cur = Get-Setting secure enabled_accessibility_services
    if ($cur -notmatch [regex]::Escape($WajhatiWatcher)) { return }
    $rest = @($cur -split ':' | Where-Object { $_ -ne '' -and $_ -ne $WajhatiWatcher })
    Set-Setting secure enabled_accessibility_services ($rest -join ':')
}

# الأداة القديمة (مجلد واجهتي) تحفظ وش عطّلت ووش كانت الواجهة الأصلية: نحتاجه عشان نرجّع اللي سوّته
function Old-State {
    $f = Join-Path (Split-Path $Here) '.wajhati-tool.json'
    if (-not (Test-Path -LiteralPath $f)) { return $null }
    try { return (Get-Content -Raw -Encoding UTF8 -LiteralPath $f | ConvertFrom-Json) } catch { return $null }
}

function Old-Disabled {
    $o = Old-State
    if (-not $o) { return @() }
    $dis = Sh 'pm list packages -d' 20
    return @(@($o.disabled) | Where-Object { "$_" -match '^[A-Za-z][\w.]*$' -and $dis -match ('package:' + [regex]::Escape("$_") + '(\s|$)') })
}

# يرجّع الواجهة الأصلية: اللي سجّلناه نحن، وإلا اللي سوّته الأداة القديمة، وإلا اللي نعرفه
function Restore-Home {
    $any = (Undo-Change 'stockoff')
    $any = (Undo-Change 'home') -or $any
    if ($any) { return }
    $o = Old-State
    foreach ($p in (Old-Disabled)) { [void](Sh "pm enable $p" 20) }
    if ($o -and [string]$o.origHome -match '^[\w.]+/[\w.$]+$') { [void](Sh "cmd package set-home-activity $($o.origHome)" 20) }
    Drop-Watcher
    foreach ($p in 'com.google.android.apps.tv.launcherx', 'com.google.android.tvlauncher', 'com.android.tv.launcher') {
        if ((Sh "pm list packages -d $p" 15) -match ('package:' + [regex]::Escape($p) + '(\s|$)')) { [void](Sh "pm enable $p" 20) }
    }
    [void](Sh 'input keyevent KEYCODE_HOME' 10)
}

function Try-Connect([string]$serial) {
    $r = Adb @('connect', $serial) 15
    $st = Adb @('-s', $serial, 'get-state') 8
    if ($st -match 'unauthorized' -or $r -match 'unauthorized|failed to authenticate') { return 'unauthorized' }
    if ($st -match '^device') { return 'ok' }
    return 'fail'
}

function Device-Summary {
    $p = Sh-Many @('settings get global device_name', 'getprop ro.product.model', 'getprop ro.build.version.release', 'pm list packages com.google.android.apps.tv.launcherx', "pm list packages $Wajhati")
    $name = $p[0]
    if (-not $name -or $name -eq 'null') { $name = $p[1] }
    $kind = 'Android TV'
    if ($p[3] -match 'launcherx') { $kind = 'Google TV' }
    return @{ serial = $script:serial; name = $name; model = $p[1]; android = $p[2]; kind = $kind; ip = ($script:serial -replace ':\d+$', '')
        wajhati = ($p[4] -match ('package:' + [regex]::Escape($Wajhati) + '(\s|$)')) }
}

# ---------------- سجل التغييرات (كل تغيير على التلفزيون يرجع بزر) ----------------

function Add-Change([string]$id, [string]$label, [string]$sub, $undo) {
    $keep = @($script:state.changes | Where-Object { $_.id -ne $id -or $_.serial -ne $script:serial })
    $old = @($script:state.changes | Where-Object { $_.id -eq $id -and $_.serial -eq $script:serial }) | Select-Object -First 1
    # أول قيمة قبل أي تغيير هي اللي نرجّع لها
    if ($old) { $undo = $old.undo }
    $script:state.changes = @(@{ id = $id; label = $label; sub = $sub; at = (Get-Date).ToString('s'); serial = $script:serial; undo = $undo }) + $keep
    Save-State
}

function Drop-Change([string]$id) {
    $script:state.changes = @($script:state.changes | Where-Object { $_.id -ne $id -or $_.serial -ne $script:serial })
    Save-State
}

function Undo-Change([string]$id) {
    $c = @($script:state.changes | Where-Object { $_.id -eq $id -and $_.serial -eq $script:serial }) | Select-Object -First 1
    if (-not $c) { return $false }
    $u = $c.undo
    switch ($id) {
        'speed' { Set-Setting global window_animation_scale ([string]$u.win); Set-Setting global transition_animation_scale ([string]$u.trans) }
        'bloat' { foreach ($p in @($u.disabled)) { if ($p -match '^[A-Za-z][\w.]*$') { [void](Sh "pm enable $p" 20) } } }
        'dream' { Set-Setting secure screensaver_components ([string]$u.comp); if ([string]$u.enabled -ne '') { Set-Setting secure screensaver_enabled ([string]$u.enabled) } }
        'net' { Set-Setting global private_dns_mode ([string]$u.dnsMode); Set-Setting global private_dns_specifier ([string]$u.dnsSpec); Set-Setting global wifi_scan_always_enabled ([string]$u.scan) }
        'stockoff' {
            foreach ($p in @($u.disabled)) { if ($p -match '^[A-Za-z][\w.]*$') { [void](Sh "pm enable $p" 20) } }
            if ([string]$u.orig -match '^[\w.]+/[\w.$]+$') { [void](Sh "cmd package set-home-activity $($u.orig)" 20) }
            [void](Sh 'input keyevent KEYCODE_HOME' 10)
        }
        'home' {
            Set-Setting secure enabled_accessibility_services ([string]$u.acc)
            if ([string]$u.accOn -ne '') { Set-Setting secure accessibility_enabled ([string]$u.accOn) }
            if ([string]$u.orig -match '^[\w.]+/[\w.$]+$') {
                [void](Sh "cmd role add-role-holder android.app.role.HOME $(([string]$u.orig -split '/')[0])" 20)
                [void](Sh "cmd package set-home-activity $($u.orig)" 20)
            }
            [void](Sh 'input keyevent KEYCODE_HOME' 10)
        }
    }
    Drop-Change $id
    return $true
}

# ---------------- قراءات الأداء والشبكة ----------------

function Perf-Read {
    $p = Sh-Many @('cat /proc/meminfo', 'top -b -n 1 | head -4', 'df -k /data | tail -1', 'settings get global window_animation_scale',
        'settings get global transition_animation_scale', 'pm list packages -d', 'pm list packages', 'settings get secure screensaver_components')
    $mem = @{}
    foreach ($k in 'MemTotal', 'MemAvailable') { $m = [regex]::Match($p[0], "$k\:\s+(\d+)"); if ($m.Success) { $mem[$k] = [int64]$m.Groups[1].Value } }
    $cpu = $null
    $m = [regex]::Match($p[1], '(\d+)%cpu.*?(\d+)%idle')
    if ($m.Success) { $cpu = [math]::Round(100 * (1 - [double]$m.Groups[2].Value / [double]$m.Groups[1].Value)) }
    $disk = @($p[2] -split '\s+')
    $bloat = @($Bloat | Where-Object { $p[6] -match ('package:' + [regex]::Escape($_.pkg) + '(\s|$)') })
    $off = @($bloat | Where-Object { $p[5] -match ('package:' + [regex]::Escape($_.pkg) + '(\s|$)') })
    return @{
        memAvail = [int]($mem.MemAvailable / 1024); memTotal = [int]($mem.MemTotal / 1024); cpu = $cpu
        diskFree = $(if ($disk.Count -ge 4) { [int64]$disk[3] * 1024 } else { $null }); diskTotal = $(if ($disk.Count -ge 2) { [int64]$disk[1] * 1024 } else { $null })
        speed = ($p[3] -eq '0.5' -and $p[4] -eq '0.5')
        bloat = @($bloat | ForEach-Object { $_.name }); bloatOff = ($bloat.Count -gt 0 -and $off.Count -eq $bloat.Count)
        wajhati = ($p[6] -match ('package:' + [regex]::Escape($Wajhati) + '(\s|$)')); dream = ($p[7] -match [regex]::Escape($Wajhati))
    }
}

function Net-Read([bool]$Ping) {
    $cmds = @('dumpsys wifi | grep -m1 "mWifiInfo SSID"', 'ip route get 1.1.1.1', 'settings get global private_dns_mode', 'settings get global private_dns_specifier', 'settings get global wifi_scan_always_enabled')
    if ($Ping) { $cmds += 'ping -c 10 -i 0.3 -W 2 1.1.1.1' }
    $p = Sh-Many $cmds 45
    $r = @{ rssi = $null; link = $null; freq = $null; iface = ''; ip = ''; dnsMode = $p[2]; dnsSpec = $p[3]; scan = $p[4]; loss = $null; avg = $null; jitter = $null }
    if ($p[0] -match 'RSSI: (-?\d+)') { $r.rssi = [int]$Matches[1] }
    if ($p[0] -match 'Link speed: (\d+)Mbps') { $r.link = [int]$Matches[1] }
    if ($p[0] -match 'Frequency: (\d+)MHz') { $r.freq = [int]$Matches[1] }
    if ($p[1] -match 'dev (\S+)') { $r.iface = $Matches[1] }
    if ($p[1] -match 'src (\S+)') { $r.ip = $Matches[1] }
    if ($Ping) {
        if ($p[5] -match '(\d+)% packet loss') { $r.loss = [int]$Matches[1] }
        if ($p[5] -match '= [\d.]+/([\d.]+)/[\d.]+/([\d.]+)') { $r.avg = [double]$Matches[1]; $r.jitter = [double]$Matches[2] }
    }
    return $r
}

# الـ DNS الآمن يمشي على المنفذ 853: نتأكد من الكمبيوتر (نفس الراوتر) إنه مفتوح قبل ما نغيّر شيء
function Test-SecureDns {
    try {
        $c = New-Object Net.Sockets.TcpClient
        $ok = $c.ConnectAsync('1.1.1.1', 853).Wait(3000) -and $c.Connected
        $c.Close()
        return $ok
    } catch { return $false }
}

# ---------------- الطلبات ----------------

function Body($ctx) {
    $sr = New-Object IO.StreamReader($ctx.Request.InputStream, [Text.Encoding]::UTF8)
    $t = $sr.ReadToEnd()
    if (-not $t) { return @{} }
    return (To-Hash ($t | ConvertFrom-Json))
}

function Scan-Adb {
    $list = @()
    $seen = @{}
    foreach ($line in ((Adb @('devices', '-l') 15) -split "`r?`n")) {
        if ($line -notmatch '^(\S+)\s+(device|unauthorized|offline)\b(.*)$') { continue }
        $serial = $Matches[1]; $st = $Matches[2]; $rest = $Matches[3]
        $model = ''
        if ($rest -match 'model:(\S+)') { $model = $Matches[1] -replace '_', ' ' }
        $ip = ''
        if ($serial -match '^(\d+\.\d+\.\d+\.\d+):\d+$') { $ip = $Matches[1] }
        $name = ''
        if ($st -eq 'device') { $name = (Adb @('-s', $serial, 'shell', 'settings get global device_name') 8).Trim(); if ($name -eq 'null') { $name = '' } }
        $list += @{ serial = $serial; ip = $ip; state = $st; model = $model; name = $name; usb = ($ip -eq '') }
        if ($ip) { $seen[$ip] = $true }
    }
    foreach ($ip in [ShashaFinder]::ScanPort(5555, 900)) {
        if (-not $seen[$ip]) { $list += @{ serial = "$ip`:5555"; ip = $ip; state = 'open'; model = ''; name = ''; usb = $false } }
    }
    return , $list
}

function Scan-Cast {
    return , @([ShashaFinder]::Mdns(2600) | ForEach-Object {
            @{ ip = $_.Ip; name = $_.Name; model = $_.Model; tv = $_.IsTv; cast = $_.IsCast; adbPort = $_.AdbPort; pairPort = $_.PairPort; legacy = $_.Legacy }
        })
}

function Scan-Dlna {
    $mine = @([ShashaFinder]::LocalAddresses() | ForEach-Object { $_.ToString() })
    $byIp = @{}
    foreach ($r in [ShashaFinder]::Ssdp(2500)) {
        $ip = $r[0]
        if ($mine -contains $ip) { continue }
        if ($r[3] -match 'InternetGatewayDevice|WANConnection|WFADevice|WANIPConnection') { continue }
        if (-not $byIp[$ip]) { $byIp[$ip] = @{ ip = $ip; server = $r[2]; locs = @() } }
        $byIp[$ip].locs += $r[1]
    }
    $out = @()
    foreach ($d in $byIp.Values) {
        $info = $null
        foreach ($loc in ($d.locs | Select-Object -Unique | Select-Object -First 3)) {
            try {
                # الأجهزة ما ترسل charset غالباً، وInvoke-WebRequest يفترض Latin-1: نقرا البايتات بـ UTF-8
                [xml]$x = [Text.Encoding]::UTF8.GetString((Invoke-WebRequest -UseBasicParsing -Uri $loc -TimeoutSec 2).RawContentStream.ToArray())
                $dev = $x.root.device
                if ($dev.deviceType -match 'InternetGatewayDevice') { $info = 'skip'; break }
                $info = @{ name = [string]$dev.friendlyName; maker = [string]$dev.manufacturer; model = [string]$dev.modelName; type = [string]$dev.deviceType }
                if ($info.type -match 'MediaRenderer|tv|dial') { break }
            } catch {}
        }
        if ($info -eq 'skip') { continue }
        if (-not $info) { $info = @{ name = ''; maker = ''; model = ''; type = '' } }
        $info.ip = $d.ip
        $info.server = $d.server
        $out += $info
    }
    return , $out
}

function Download-Tool([string]$what) {
    [void](New-Item -ItemType Directory -Force -Path $Tools)
    $zip = Join-Path $Tools "$what.zip"
    if ($what -eq 'adb') {
        Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip
    } else {
        $rel = Invoke-RestMethod -Uri 'https://api.github.com/repos/Genymobile/scrcpy/releases/latest' -Headers @{ 'User-Agent' = 'Shasha' }
        $asset = @($rel.assets | Where-Object { $_.name -match 'win64.*\.zip$' })[0]
        if (-not $asset) { throw 'ما لقيت نسخة scrcpy لويندوز' }
        Invoke-WebRequest -UseBasicParsing -Uri $asset.browser_download_url -OutFile $zip
    }
    Expand-Archive -Path $zip -DestinationPath $Tools -Force
    Remove-Item $zip -Force -ErrorAction SilentlyContinue
}

# مشغّل شاشة IPTV: آخر إصدار من GitHub (نسأل مرة كل 10 دقائق)
$script:iptvRel = $null
function Iptv-Release {
    if ($script:iptvRel -and ((Get-Date) - $script:iptvRel.at).TotalMinutes -lt 10) { return $script:iptvRel }
    $rel = Invoke-RestMethod -Uri 'https://api.github.com/repos/saadharazi/shasha/releases/latest' -Headers @{ 'User-Agent' = 'Shasha' } -TimeoutSec 12
    $asset = @($rel.assets | Where-Object { $_.name -eq 'shasha.apk' })[0]
    if (-not $asset) { throw 'ما لقيت ملف التطبيق في آخر إصدار' }
    $script:iptvRel = @{ version = ([string]$rel.tag_name -replace '^v', ''); size = [int64]$asset.size; url = [string]$asset.browser_download_url; at = (Get-Date) }
    return $script:iptvRel
}

function Tool-Sizes {
    $r = @{ adb = $null; scrcpy = $null }
    try { $r.adb = [int64](Invoke-WebRequest -UseBasicParsing -Method Head -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -TimeoutSec 8).Headers['Content-Length'] } catch {}
    try {
        $rel = Invoke-RestMethod -Uri 'https://api.github.com/repos/Genymobile/scrcpy/releases/latest' -Headers @{ 'User-Agent' = 'Shasha' } -TimeoutSec 8
        $r.scrcpy = [int64](@($rel.assets | Where-Object { $_.name -match 'win64.*\.zip$' })[0].size)
    } catch {}
    return $r
}

function Api([string]$path, $ctx) {
    switch ($path) {
        'ping' { return @{ ok = $true } }

        'state' {
            $adb = Find-Exe 'adbPath' 'adb.exe'
            $dev = $null
            if ($adb) {
                if (-not $script:serial -and $S.autoConnect -and $script:state.last.serial) {
                    try { if ((Try-Connect ([string]$script:state.last.serial)) -eq 'ok') { $script:serial = [string]$script:state.last.serial } } catch {}
                }
                if ($script:serial) { try { Need-Device; $dev = Device-Summary } catch { $dev = @{ serial = $script:serial; offline = $true; name = [string]$script:state.last.name; ip = ($script:serial -replace ':\d+$', '') } } }
            }
            return @{ version = $Version; adb = $adb; scrcpy = (Find-Exe 'scrcpyPath' 'scrcpy.exe'); settings = $S; device = $dev; last = $script:state.last
                changes = @($script:state.changes | Where-Object { $_.serial -eq $script:serial } | ForEach-Object { @{ id = $_.id; label = $_.label; sub = $_.sub; at = $_.at } })
                installs = @($script:state.installs | Select-Object -First 8); launcher = $script:state.launcher; backups = $BackupDir }
        }

        'scan' {
            $b = Body $ctx
            switch ([string]$b.method) {
                'adb' { return @{ list = (Scan-Adb) } }
                'cast' { return @{ list = (Scan-Cast) } }
                'dlna' { return @{ list = (Scan-Dlna) } }
            }
            throw 'طريقة بحث غير معروفة'
        }

        'connect' {
            $b = Body $ctx
            $serial = [string]$b.serial
            if ($serial -notmatch '^[\w.\-]+(:\d{1,5})?$') { throw 'عنوان غير صحيح' }
            if ($serial -match '^\d+\.\d+\.\d+\.\d+$') { $serial += ':5555' }
            $r = 'ok'
            if ($serial -match ':\d+$') { $r = Try-Connect $serial }
            elseif ((Adb @('-s', $serial, 'get-state') 8) -notmatch '^device') { $r = 'fail' }
            if ($r -ne 'ok') { return @{ result = $r } }
            $script:serial = $serial
            $d = Device-Summary
            $script:state.last = @{ serial = $serial; name = $d.name; ip = $d.ip }
            Save-State
            return @{ result = 'ok'; device = $d }
        }

        'pair' {
            $b = Body $ctx
            $addr = [string]$b.addr; $code = [string]$b.code
            if ($addr -notmatch '^\d+\.\d+\.\d+\.\d+:\d{1,5}$' -or $code -notmatch '^\d{6}$') { throw 'اكتب كود الإقران (6 أرقام)' }
            $r = Adb @('pair', $addr, $code) 30
            return @{ ok = ($r -match 'Successfully paired'); out = $r }
        }

        'overview' {
            Need-Device
            $p = Sh-Many @('settings get global device_name', 'getprop ro.product.model', 'getprop ro.product.manufacturer', 'getprop ro.build.version.release',
                'wm size', 'cat /proc/meminfo | head -3', 'df -k /data | tail -1', 'echo $(getprop ro.soc.manufacturer) $(getprop ro.soc.model)"|"$(getprop ro.board.platform)"|"$(nproc)', 'ip route get 1.1.1.1',
                'dumpsys wifi | grep -m1 "mWifiInfo SSID"', "dumpsys package $Wajhati | grep -m1 versionName", "dumpsys package $IptvPkg | grep -m1 versionName",
                'settings get secure enabled_accessibility_services', 'pm list packages com.google.android.apps.tv.launcherx', 'settings get global development_settings_enabled') 60
            $r = @{ name = $p[0]; model = $p[1]; maker = $p[2]; android = $p[3]; serial = $script:serial }
            if (-not $r.name -or $r.name -eq 'null') { $r.name = $p[1] }
            $r.kind = $(if ($p[13] -match 'launcherx') { 'Google TV' } else { 'Android TV' })
            if ($p[4] -match '(\d+)x(\d+)') { $r.size = "$($Matches[1]) × $($Matches[2])" }
            if ($p[5] -match 'MemTotal:\s+(\d+)') { $r.memTotal = [int]([int64]$Matches[1] / 1024) }
            if ($p[5] -match 'MemAvailable:\s+(\d+)') { $r.memAvail = [int]([int64]$Matches[1] / 1024) }
            $disk = @($p[6] -split '\s+')
            if ($disk.Count -ge 4) { $r.diskTotal = [int64]$disk[1] * 1024; $r.diskFree = [int64]$disk[3] * 1024 }
            $chip = @($p[7] -split '\|')
            $r.chip = $(if ($chip[0].Trim()) { $chip[0].Trim() } else { $chip[1] })
            if ($chip.Count -ge 3) { $r.cores = [int]$chip[2] }
            if ($p[8] -match 'dev (\S+)') { $r.iface = $Matches[1] }
            if ($p[8] -match 'src (\S+)') { $r.ip = $Matches[1] }
            if ($p[9] -match 'RSSI: (-?\d+)') { $r.rssi = [int]$Matches[1] }
            if ($p[9] -match 'Frequency: (\d+)MHz') { $r.freq = [int]$Matches[1] }
            if ($p[10] -match 'versionName=(\S+)') { $r.wajhati = $Matches[1] }
            if ($p[11] -match 'versionName=(\S+)') { $r.iptv = $Matches[1] }
            $r.watcher = ($p[12] -match [regex]::Escape($Wajhati))
            $r.dev = ($p[14] -eq '1')
            return $r
        }

        'wajhati/info' {
            Need-Device
            $ver = Version-Of $Wajhati
            $apk = Wajhati-Apk
            $r = @{ installed = $ver; apk = [bool]$apk; apkSize = $(if ($apk) { (Get-Item -LiteralPath $apk).Length } else { $null }); home = $false; watcher = $false
                strong = (@($script:state.changes | Where-Object { $_.id -eq 'stockoff' -and $_.serial -eq $script:serial }).Count -gt 0) -or ((Old-Disabled).Count -gt 0) }
            if ($ver) {
                $r.home = Is-Ours (Resolve-Home)
                $r.watcher = ((Get-Setting secure enabled_accessibility_services) -match [regex]::Escape($WajhatiWatcher))
            }
            return $r
        }

        'wajhati/install' {
            Need-Device
            $apk = Wajhati-Apk
            if (-not $apk) { throw 'ما لقينا wajhati.apk جنب الأداة. حطّه في نفس المجلد وجرّب مرة ثانية.' }
            $r = Adb @('-s', $script:serial, 'install', '-r', '-g', $apk) 240
            $ok = $r -match '(?m)^Success'
            $msg = @($r -split "`r?`n" | Where-Object { $_.Trim() -ne '' })[-1]
            $script:state.installs = @(@{ name = 'واجهتي'; size = (Get-Item -LiteralPath $apk).Length; at = (Get-Date).ToString('s'); ok = [bool]$ok; msg = $msg }) + @($script:state.installs | Select-Object -First 19)
            Save-State
            if ($ok) { [void](Adb @('-s', $script:serial, 'shell', 'am', 'start', '-n', $WajhatiHome) 20) }
            return @{ ok = [bool]$ok; msg = $msg; version = (Version-Of $Wajhati) }
        }

        # زر الرئيسية يفتح واجهتي: بالطريقة الرسمية لو التلفزيون يسمح، وإلا عن طريق خدمة واجهتي
        'wajhati/home' {
            Need-Wajhati
            $orig = Resolve-Home
            $u = @{ orig = $(if (-not (Is-Ours $orig) -and $orig -notmatch 'ResolverActivity') { $orig } else { '' })
                acc = (Get-Setting secure enabled_accessibility_services); accOn = (Get-Setting secure accessibility_enabled) }
            [void](Sh "cmd role add-role-holder android.app.role.HOME $Wajhati" 20)
            [void](Sh "cmd package set-home-activity $WajhatiHome" 20)
            $how = 'role'
            if (-not (Is-Ours (Resolve-Home))) {
                $acc = $u.acc
                $new = $(if (-not $acc) { $WajhatiWatcher } elseif ($acc -match [regex]::Escape($WajhatiWatcher)) { $acc } else { "$acc`:$WajhatiWatcher" })
                Set-Setting secure enabled_accessibility_services $new
                Set-Setting secure accessibility_enabled '1'
                Wajhati-Cfg @{ override = $true }
                $how = 'watcher'
            }
            Add-Change 'home' 'واجهتي الواجهة الرئيسية' $(if ($how -eq 'role') { 'زر الرئيسية يفتحها' } else { 'عن طريق خدمة واجهتي، زر الرئيسية يفتحها' }) $u
            [void](Sh 'input keyevent KEYCODE_HOME' 10)
            return @{ ok = $true; how = $how }
        }

        # خيار قوي: نعطّل الواجهة الأصلية (ما ننحذف، وترجع من «رجّع الواجهة الأصلية»)
        'wajhati/strong' {
            Need-Wajhati
            $orig = Resolve-Home
            $done = @()
            for ($i = 0; $i -lt 3; $i++) {
                $cur = Resolve-Home
                $pkg = ($cur -split '/')[0]
                if ($pkg -eq $Wajhati) { break }
                if ($cur -match 'ResolverActivity') { [void](Sh "cmd package set-home-activity $WajhatiHome" 20); continue }
                # لو adb رجّع رسالة بدل اسم حزمة نوقف (ما نعطّل شي غلط)
                if ($pkg -notmatch '^[A-Za-z][\w.]*$' -or $pkg -match 'settings') { break }
                if ((Sh "pm disable-user --user 0 $pkg" 20) -match 'disabled') { $done += $pkg }
            }
            if ($done.Count) {
                Add-Change 'stockoff' 'عطّلنا الواجهة الأصلية' ($done -join '، ') @{ disabled = $done; orig = $(if (-not (Is-Ours $orig) -and $orig -notmatch 'ResolverActivity') { $orig } else { '' }) }
            }
            [void](Sh 'input keyevent KEYCODE_HOME' 10)
            return @{ ok = (Is-Ours (Resolve-Home)); disabled = $done }
        }

        # ترجع الواجهة الأصلية؛ لو التغيير مو من هذي الأداة (الأداة القديمة مثلاً) نرجّع اللي نعرفه
        'wajhati/restore' {
            Need-Device
            Restore-Home
            return @{ ok = $true; home = (Resolve-Home) }
        }

        'wajhati/uninstall' {
            Need-Device
            Restore-Home
            [void](Undo-Change 'dream')
            if ((Get-Setting secure screensaver_components) -match [regex]::Escape($Wajhati)) {
                $o = Old-State
                $comp = $(if ($o -and $o.dream) { [string]$o.dream.comp } else { '' })
                Set-Setting secure screensaver_components $(if ($comp -match '^[\w.]+/[\w.$]+$') { $comp } else { '' })
            }
            $r = Adb @('-s', $script:serial, 'uninstall', $Wajhati) 60
            [void](Sh 'input keyevent KEYCODE_HOME' 10)
            return @{ ok = ($r -match 'Success'); msg = $r }
        }

        'launcher/intro' { Need-Wajhati; Wajhati-Cfg @{ replayIntro = $true } $true; return @{ ok = $true } }
        'launcher/reset' { Need-Wajhati; Wajhati-Cfg @{ resetLayout = $true } $true; return @{ ok = $true } }

        # صورة من الكمبيوتر خلفية لواجهتي
        'launcher/wallpaper' {
            Need-Wajhati
            $ext = [IO.Path]::GetExtension([Uri]::UnescapeDataString([string]$ctx.Request.Headers['X-File-Name'])).ToLower()
            if ($ext -eq '.jpeg') { $ext = '.jpg' }
            if (@('.jpg', '.png', '.webp') -notcontains $ext) { throw 'اختر صورة JPG أو PNG أو WEBP' }
            if ($ctx.Request.ContentLength64 -gt 25MB) { throw 'الصورة أكبر من 25 ميقا. اختر صورة أصغر.' }
            $dir = Join-Path $env:TEMP 'shasha-upload'
            [void](New-Item -ItemType Directory -Force -Path $dir)
            $file = Join-Path $dir ('wajhati-wallpaper' + $ext)
            $fs = [IO.File]::Create($file)
            try { $ctx.Request.InputStream.CopyTo($fs) } finally { $fs.Close() }
            [void](Adb @('-s', $script:serial, 'shell', 'am', 'start', '-n', $WajhatiHome) 20)
            $own = "/sdcard/Android/data/$Wajhati/files"
            [void](Sh "mkdir -p $own; rm -f $own/wajhati-wallpaper.* /sdcard/Pictures/wajhati-wallpaper.*" 10)
            $r1 = Adb @('-s', $script:serial, 'push', $file, "$own/wajhati-wallpaper$ext") 120
            $r2 = Adb @('-s', $script:serial, 'push', $file, "/sdcard/Pictures/wajhati-wallpaper$ext") 120
            Remove-Item $file -Force -ErrorAction SilentlyContinue
            [void](Sh "pm grant $Wajhati android.permission.READ_MEDIA_IMAGES; pm grant $Wajhati android.permission.READ_EXTERNAL_STORAGE" 10)
            if ($r1 -notmatch 'pushed' -and $r2 -notmatch 'pushed') { throw "ما قدرنا نرفع الصورة: $r1" }
            Wajhati-Cfg @{ theme = 'custom' } $true
            $script:state.launcher.theme = 'custom'
            Save-State
            return @{ ok = $true }
        }

        'launcher/apply' {
            Need-Device
            if (-not (Has-Package $Wajhati)) { throw 'واجهتي مو مثبّتة على هذا التلفزيون' }
            $b = Body $ctx
            $cfg = @{ style = [string]$b.style; theme = [string]$b.theme; name = ([string]$b.name).Trim(); clock24 = ([string]$b.clock -eq '24') }
            if (@('wake', 'boot', 'off') -contains [string]$b.intro) { $cfg.intro = [string]$b.intro }
            if ($null -ne $b.inputs) { $cfg.inputs = [bool]$b.inputs }
            $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(($cfg | ConvertTo-Json -Compress)))
            $r = Adb @('-s', $script:serial, 'shell', 'am', 'start', '-n', $WajhatiHome, '--es', 'cfg', $b64, '--ez', 'reload', 'true') 30
            if ($r -match 'Error') { throw $r }
            $script:state.launcher = @{ style = $cfg.style; theme = $cfg.theme; name = $cfg.name; clock = [string]$b.clock; intro = [string]$cfg.intro; inputs = $cfg.inputs }
            Save-State
            return @{ ok = $true }
        }

        'install' {
            Need-Device
            $name = [IO.Path]::GetFileName([Uri]::UnescapeDataString([string]$ctx.Request.Headers['X-File-Name'])) -replace '[^\w.\- ()\[\]؀-ۿ]', '_'
            if ($name -notmatch '\.apk$') { throw 'اختر ملف ينتهي بـ .apk' }
            $dir = Join-Path $env:TEMP 'shasha-upload'
            [void](New-Item -ItemType Directory -Force -Path $dir)
            $file = Join-Path $dir $name
            $fs = [IO.File]::Create($file)
            try { $ctx.Request.InputStream.CopyTo($fs) } finally { $fs.Close() }
            $size = (Get-Item $file).Length
            $r = Adb @('-s', $script:serial, 'install', '-r', $file) 300
            Remove-Item $file -Force -ErrorAction SilentlyContinue
            $ok = $r -match '(?m)^Success'
            $msg = @($r -split "`r?`n" | Where-Object { $_.Trim() -ne '' })[-1]
            $script:state.installs = @(@{ name = $name; size = $size; at = (Get-Date).ToString('s'); ok = [bool]$ok; msg = $msg }) + @($script:state.installs | Select-Object -First 19)
            Save-State
            return @{ ok = [bool]$ok; msg = $msg }
        }

        'mirror' {
            Need-Device
            $exe = Find-Exe 'scrcpyPath' 'scrcpy.exe'
            if (-not $exe) { throw 'noscrcpy' }
            $b = Body $ctx
            $size = [int]$b.size
            if (@(720, 960, 1280) -notcontains $size) { $size = 720 }
            $rate = @{ 720 = '2M'; 960 = '4M'; 1280 = '6M' }[$size]
            # scrcpy يستخدم نفس adb حقنا (نسختين مختلفتين يطفّون خادم بعض)
            $env:ADB = $script:adb
            $a = @($exe, '-s', $script:serial, '--max-size', [string]$size, '--max-fps', '30', '--video-bit-rate', $rate, '--window-title', 'شاشة: عرض التلفزيون')
            if (-not $b.audio) { $a += '--no-audio' }
            if (-not $b.control) { $a += '--no-control' }
            # عن طريق cmd مخفي (مثل scrcpy-noconsole.vbs): ما تطلع نافذة سوداء
            Start-Process -FilePath 'cmd.exe' -ArgumentList ('/c "' + (($a | ForEach-Object { Quote-Arg $_ }) -join ' ') + '"') -WorkingDirectory (Split-Path $exe) -WindowStyle Hidden
            return @{ ok = $true }
        }

        'perf' { Need-Device; return (Perf-Read) }

        'perf/set' {
            Need-Device
            $b = Body $ctx
            $on = [bool]$b.on
            switch ([string]$b.id) {
                'speed' {
                    if ($on) {
                        $u = @{ win = (Get-Setting global window_animation_scale); trans = (Get-Setting global transition_animation_scale) }
                        Set-Setting global window_animation_scale '0.5'
                        Set-Setting global transition_animation_scale '0.5'
                        Add-Change 'speed' 'حركات أسرع' 'فتح التطبيقات والنوافذ بنص المدة' $u
                    } elseif (-not (Undo-Change 'speed')) { Set-Setting global window_animation_scale ''; Set-Setting global transition_animation_scale '' }
                }
                'bloat' {
                    $installed = Sh 'pm list packages' 20
                    $found = @($Bloat | Where-Object { $installed -match ('package:' + [regex]::Escape($_.pkg) + '(\s|$)') })
                    if ($on) {
                        $done = @()
                        foreach ($x in $found) { if ((Sh "pm disable-user --user 0 $($x.pkg)" 20) -match 'disabled') { $done += $x.pkg } }
                        Add-Change 'bloat' 'وقّف تطبيقات عرض المحلات' (($found | ForEach-Object { $_.name }) -join '، ') @{ disabled = $done }
                    } elseif (-not (Undo-Change 'bloat')) { foreach ($x in $found) { [void](Sh "pm enable $($x.pkg)" 20) } }
                }
                'dream' {
                    if (-not (Has-Package $Wajhati)) { throw 'واجهتي مو مثبّتة على هذا التلفزيون' }
                    if ($on) {
                        $u = @{ comp = (Get-Setting secure screensaver_components); enabled = (Get-Setting secure screensaver_enabled) }
                        Set-Setting secure screensaver_components $WajhatiDream
                        Set-Setting secure screensaver_enabled '1'
                        Add-Change 'dream' 'شاشة توقف واجهتي' 'الساعة والصلاة وصورك بدل شاشة التوقف الأصلية' $u
                    } elseif (-not (Undo-Change 'dream')) { Set-Setting secure screensaver_components '' }
                }
                default { throw 'خيار غير معروف' }
            }
            return (Perf-Read)
        }

        'net' { Need-Device; $b = Body $ctx; return (Net-Read ([bool]$b.ping)) }

        'net/fix' {
            Need-Device
            $n = Net-Read $false
            $u = @{ dnsMode = $n.dnsMode; dnsSpec = $n.dnsSpec; scan = $n.scan }
            Set-Setting global wifi_scan_always_enabled '0'
            $dns = Test-SecureDns
            if ($dns) {
                Set-Setting global private_dns_specifier 'one.one.one.one'
                Set-Setting global private_dns_mode 'hostname'
            }
            Add-Change 'net' 'تحسين الاتصال' $(if ($dns) { 'DNS سريع، ووقف بحث الواي فاي الدائم' } else { 'وقف بحث الواي فاي الدائم' }) $u
            # تغيير الـ DNS يخلّي التلفزيون يعيد فحص الشبكة: نتأكد إن الإنترنت شغال، وإلا نرجّعه
            $ok = $null
            if ($dns) {
                for ($i = 0; $i -lt 4 -and $null -eq $ok; $i++) {
                    Start-Sleep -Seconds 3
                    try {
                        $r = Sh 'ping -c 3 -W 3 one.one.one.one' 25
                        if ($r -match '[1-9]\d* received') { $ok = $true } elseif ($r -notmatch 'error:|offline|closed|no devices') { $ok = $false }
                    } catch {}
                    if ($null -eq $ok) { try { [void](Try-Connect $script:serial) } catch {} }
                }
                if ($ok -eq $false) {
                    Set-Setting global private_dns_mode $u.dnsMode
                    Set-Setting global private_dns_specifier $u.dnsSpec
                }
            }
            return @{ dns = $dns; internet = $ok; net = (Net-Read $false) }
        }

        'undo' {
            Need-Device
            $b = Body $ctx
            return @{ ok = (Undo-Change ([string]$b.id)) }
        }

        'backup/list' {
            $items = @()
            if (Test-Path $BackupDir) {
                $items = @(Get-ChildItem -Directory $BackupDir | Sort-Object Name -Descending | ForEach-Object {
                        $files = @(Get-ChildItem -File -Recurse $_.FullName -ErrorAction SilentlyContinue)
                        @{ name = $_.Name; path = $_.FullName; size = [int64](($files | Measure-Object Length -Sum).Sum); images = @($files | Where-Object { $_.Extension -match 'jpe?g|png|webp' }).Count; valid = (Test-Path (Join-Path $_.FullName 'wajhati-backup.json')) }
                    })
            }
            $has = $false
            if ($script:serial) { try { Need-Device; $has = Has-Package $Wajhati } catch {} }
            return @{ dir = $BackupDir; list = $items; wajhati = $has }
        }

        'backup/new' {
            Need-Device
            if (-not (Has-Package $Wajhati)) { throw 'واجهتي مو مثبّتة على هذا التلفزيون' }
            $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('{"export":true}'))
            [void](Adb @('-s', $script:serial, 'shell', 'am', 'start', '-n', $WajhatiHome, '--es', 'cfg', $b64) 30)
            Start-Sleep -Seconds 2
            $dest = Join-Path $BackupDir (Get-Date -Format 'yyyy-MM-dd HH-mm')
            [void](New-Item -ItemType Directory -Force -Path $dest)
            $r = Adb @('-s', $script:serial, 'pull', "/sdcard/Android/data/$Wajhati/files/.", $dest) 300
            if (-not (Test-Path (Join-Path $dest 'wajhati-backup.json'))) { Remove-Item -Recurse -Force $dest -ErrorAction SilentlyContinue; throw "ما قدرنا ناخذ النسخة: $r" }
            return @{ ok = $true; path = $dest }
        }

        'backup/restore' {
            Need-Device
            if (-not (Has-Package $Wajhati)) { throw 'واجهتي مو مثبّتة على هذا التلفزيون' }
            $b = Body $ctx
            $src = Join-Path $BackupDir ([IO.Path]::GetFileName([string]$b.name))
            if (-not (Test-Path (Join-Path $src 'wajhati-backup.json'))) { throw 'النسخة ناقصة' }
            $own = "/sdcard/Android/data/$Wajhati/files"
            [void](Sh "mkdir -p $own" 10)
            $r = Adb @('-s', $script:serial, 'push', (Join-Path $src '.'), $own) 300
            if ($r -notmatch 'pushed') { throw "ما قدرنا نرفع النسخة: $r" }
            $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('{"importFile":true}'))
            [void](Adb @('-s', $script:serial, 'shell', 'am', 'start', '-n', $WajhatiHome, '--es', 'cfg', $b64) 30)
            return @{ ok = $true }
        }

        'open' {
            $b = Body $ctx
            $target = $(if ($b.what -eq 'backups') { $BackupDir } else { Join-Path $BackupDir ([IO.Path]::GetFileName([string]$b.name)) })
            [void](New-Item -ItemType Directory -Force -Path $target)
            Start-Process explorer.exe -ArgumentList ('"' + $target + '"')
            return @{ ok = $true }
        }

        'settings' {
            $b = Body $ctx
            foreach ($k in 'adb', 'cast', 'dlna', 'autoConnect') { if ($null -ne $b[$k]) { $S[$k] = [bool]$b[$k] } }
            foreach ($k in 'adbPath', 'scrcpyPath') {
                if ($null -ne $b[$k]) {
                    $v = ([string]$b[$k]).Trim().Trim('"')
                    if ($v -and -not (Test-Path -LiteralPath $v -PathType Leaf)) { throw "ما لقينا الملف: $v" }
                    $S[$k] = $v
                }
            }
            Save-State
            return @{ settings = $S; adb = (Find-Exe 'adbPath' 'adb.exe'); scrcpy = (Find-Exe 'scrcpyPath' 'scrcpy.exe') }
        }

        'iptv/info' {
            Need-Device
            $installed = Version-Of $IptvPkg
            $r = @{ installed = $installed; latest = $null; size = $null }
            try { $rel = Iptv-Release; $r.latest = $rel.version; $r.size = $rel.size } catch { $r.offline = $true }
            return $r
        }

        'iptv/install' {
            Need-Device
            $rel = Iptv-Release
            $dir = Join-Path $DataDir 'downloads'
            [void](New-Item -ItemType Directory -Force -Path $dir)
            $file = Join-Path $dir ('shasha-' + $rel.version + '.apk')
            if (-not (Test-Path $file) -or (Get-Item $file).Length -ne $rel.size) {
                Invoke-WebRequest -UseBasicParsing -Uri $rel.url -OutFile $file -TimeoutSec 120
                if ((Get-Item $file).Length -ne $rel.size) { Remove-Item $file -Force; throw 'التحميل ما كمل، جرّب مرة ثانية' }
            }
            $r = Adb @('-s', $script:serial, 'install', '-r', $file) 300
            $ok = $r -match '(?m)^Success'
            $msg = @($r -split "`r?`n" | Where-Object { $_.Trim() -ne '' })[-1]
            if ($msg -match 'UPDATE_INCOMPATIBLE') { $msg = 'النسخة اللي على التلفزيون موقّعة بمفتاح ثاني. احذفها من التلفزيون وجرّب مرة ثانية.' }
            $script:state.installs = @(@{ name = "شاشة IPTV $($rel.version)"; size = $rel.size; at = (Get-Date).ToString('s'); ok = [bool]$ok; msg = $msg }) + @($script:state.installs | Select-Object -First 19)
            Save-State
            return @{ ok = [bool]$ok; msg = $msg; version = (Version-Of $IptvPkg) }
        }

        'tools/sizes' { return (Tool-Sizes) }
        'tools/get' {
            $b = Body $ctx
            if (@('adb', 'scrcpy') -notcontains [string]$b.what) { throw 'أداة غير معروفة' }
            Download-Tool ([string]$b.what)
            return @{ adb = (Find-Exe 'adbPath' 'adb.exe'); scrcpy = (Find-Exe 'scrcpyPath' 'scrcpy.exe') }
        }
    }
    throw 'notfound'
}

# ---------------- الخادم ----------------

function Send($ctx, [int]$code, [byte[]]$bytes, [string]$type) {
    $res = $ctx.Response
    $res.StatusCode = $code
    $res.ContentType = $type
    $res.Headers['Cache-Control'] = 'no-store'
    $res.Headers['X-Content-Type-Options'] = 'nosniff'
    $res.ContentLength64 = $bytes.Length
    $res.OutputStream.Write($bytes, 0, $bytes.Length)
    $res.OutputStream.Close()
}

function Send-Json($ctx, $obj, [int]$code = 200) {
    Send $ctx $code ([Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 8 -Compress))) 'application/json; charset=utf-8'
}

$Errors = @{
    noadb = 'برنامج adb مو موجود. حمّله من الإعدادات أو حدّد مكانه.'
    noscrcpy = 'برنامج عرض الشاشة (scrcpy) مو موجود. حمّله من الإعدادات أو حدّد مكانه.'
    nodevice = 'ما فيه تلفزيون متصل. ابحث عن شاشتك واخترها.'
}

function Serve-File($ctx, [string]$file, [string]$type, [string]$token) {
    if (-not (Test-Path $file)) { Send $ctx 404 ([Text.Encoding]::UTF8.GetBytes('not found')) 'text/plain'; return }
    $text = [IO.File]::ReadAllText($file, [Text.Encoding]::UTF8)
    if ($token) { $text = $text.Replace('{{TOKEN}}', $token) }
    Send $ctx 200 ([Text.Encoding]::UTF8.GetBytes($text)) $type
}

# منفذ ثابت عشان النافذة تتذكر الوضع والصفحة (كل منفذ عند المتصفح موقع لحاله)، وأي منفذ فاضي لو كان مشغول
$port = 47631
try { $l = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $port); $l.Start(); $l.Stop() }
catch { $l = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0); $l.Start(); $port = $l.LocalEndpoint.Port; $l.Stop() }
# الرمز يمنع أي موقع ثاني في المتصفح من إنه يطلب من الأداة شيء (ما يقدر يقرا الصفحة اللي فيها الرمز)
$token = [guid]::NewGuid().ToString('N')
$http = New-Object Net.HttpListener
# localhost بس: الطلبات اللي جايه باسم ثاني (مثل DNS rebinding) يرفضها HttpListener لحاله
$http.Prefixes.Add("http://localhost:$port/")
$http.Start()
$url = "http://localhost:$port/"

# SHASHA_NO_WINDOW=1 يشغّل الخادم بس (للتجربة من متصفح ثاني)
$edge = $null
# نافذة تطبيق (--app) من أي متصفح Chromium موجود: Edge أول، وبعده Chrome وBrave
if (-not $env:SHASHA_NO_WINDOW) {
    $edge = @(foreach ($n in 'msedge.exe', 'chrome.exe', 'brave.exe') {
            foreach ($h in 'HKLM:', 'HKCU:') { try { (Get-ItemProperty "$h\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\$n" -ErrorAction Stop).'(default)' } catch {} }
        }) + @("${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe", "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe") |
        Where-Object { $_ -and (Test-Path $_.Trim('"')) } | Select-Object -First 1
    if ($edge) { $edge = $edge.Trim('"') }
}
if ($env:SHASHA_NO_WINDOW) { Write-Host $url }
elseif ($edge) {
    Start-Process -FilePath $edge -ArgumentList @("--app=$url", ('--user-data-dir="' + (Join-Path $DataDir 'window') + '"'), '--no-first-run', '--no-default-browser-check', '--window-size=1320,860')
} else {
    Start-Process $url
}

# الأداة تقفل لما تنقفل النافذة: الصفحة ترسل «باي»، وإذا ما وصل، نقفل بعد دقيقتين بدون أي طلب
$lastSeen = Get-Date
$byeAt = $null
while ($http.IsListening) {
    $task = $http.GetContextAsync()
    while (-not $task.Wait(500)) {
        $now = Get-Date
        if (($byeAt -and $now -gt $byeAt) -or ($now - $lastSeen).TotalSeconds -gt 120) { $http.Stop(); break }
    }
    if (-not $http.IsListening) { break }
    $ctx = $task.Result
    $lastSeen = Get-Date
    $byeAt = $null
    $path = $ctx.Request.Url.AbsolutePath
    try {
        if ($path -eq '/' -or $path -eq '/index.html') { Serve-File $ctx (Join-Path $Here 'ui\index.html') 'text/html; charset=utf-8' $token; continue }
        if ($path -eq '/favicon.svg') { Serve-File $ctx (Join-Path $Here 'ui\favicon.svg') 'image/svg+xml' $null; continue }
        # معاينة واجهتي: من مجلد الموقع لو الأداة داخل المشروع، وإلا من الموقع نفسه
        if ($path -eq '/preview/' -or $path -eq '/assets/prefs.js') {
            $local = Join-Path $Here ('..\docs' + ($path -replace '/$', '/index.html').Replace('/', '\'))
            if (Test-Path $local) { Serve-File $ctx $local $(if ($path -match '\.js$') { 'text/javascript; charset=utf-8' } else { 'text/html; charset=utf-8' }) $null }
            else { $ctx.Response.Redirect($Site + $path + $ctx.Request.Url.Query); $ctx.Response.Close() }
            continue
        }
        if ($path -eq '/api/bye') {
            if ($ctx.Request.QueryString['t'] -eq $token) { $byeAt = (Get-Date).AddSeconds(4) }
            Send $ctx 204 @() 'text/plain'
            continue
        }
        if ($path -notmatch '^/api/(.+)$') { Send $ctx 404 ([Text.Encoding]::UTF8.GetBytes('not found')) 'text/plain'; continue }
        $name = $Matches[1]
        if ($ctx.Request.Headers['X-Shasha'] -ne $token -or $ctx.Request.HttpMethod -ne 'POST') { Send-Json $ctx @{ error = 'forbidden' } 403; continue }
        try {
            Send-Json $ctx (Api $name $ctx)
        } catch {
            $m = $_.Exception.Message
            if ($m -eq 'notfound') { Send-Json $ctx @{ error = 'notfound' } 404 }
            elseif ($Errors[$m]) { Send-Json $ctx @{ error = $Errors[$m]; code = $m } 409 }
            else { Send-Json $ctx @{ error = $m } 500 }
        }
    } catch {
        try { $ctx.Response.Abort() } catch {}
    }
}
