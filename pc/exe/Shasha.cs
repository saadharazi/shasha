// Shasha.exe: ملف واحد فيه أداة شاشة (سكربت PowerShell والواجهة). يفكّها في LOCALAPPDATA ويشغّلها بدون نافذة سوداء.
// يتبنى بـ build.sh (مترجم C# اللي يجي مع ويندوز، بدون تثبيت شيء).
using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Windows.Forms;

[assembly: AssemblyTitle("Shasha")]
[assembly: AssemblyDescription("Shasha: Android TV tool")]
[assembly: AssemblyProduct("Shasha")]
[assembly: AssemblyCompany("Shasha")]
[assembly: AssemblyCopyright("(c) 2026 Shasha")]
[assembly: AssemblyVersion("1.3.0.0")]
[assembly: AssemblyFileVersion("1.3.0.0")]

static class Launcher
{
    const string Version = "1.3";
    static readonly string[] Files = { "Shasha.ps1", "ui/index.html", "ui/favicon.svg" };

    [STAThread]
    static int Main()
    {
        try
        {
            string data = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Shasha");
            string app = Path.Combine(data, "app-" + Version);
            Assembly me = Assembly.GetExecutingAssembly();
            foreach (string f in Files)
            {
                string dest = Path.Combine(app, f.Replace('/', '\\'));
                Directory.CreateDirectory(Path.GetDirectoryName(dest));
                using (Stream s = me.GetManifestResourceStream(f))
                using (MemoryStream m = new MemoryStream())
                {
                    s.CopyTo(m);
                    byte[] b = m.ToArray();
                    if (!File.Exists(dest) || !Same(File.ReadAllBytes(dest), b)) File.WriteAllBytes(dest, b);
                }
            }
            string ps = Path.Combine(Environment.SystemDirectory, @"WindowsPowerShell\v1.0\powershell.exe");
            ProcessStartInfo p = new ProcessStartInfo(ps, "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File \"" + Path.Combine(app, "Shasha.ps1") + "\"");
            p.UseShellExecute = false;
            p.CreateNoWindow = true;
            // مجلد الـ exe: لو هو مجلد واجهتي، الأداة تستخدم wajhati.apk وadb وscrcpy اللي فيه
            p.EnvironmentVariables["SHASHA_BASE"] = AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\');
            p.EnvironmentVariables["SHASHA_TOOLS"] = Path.Combine(data, "tools");
            Process.Start(p);
            return 0;
        }
        catch (Exception e)
        {
            MessageBox.Show("ما قدرنا نشغّل شاشة:\n" + e.Message, "شاشة", MessageBoxButtons.OK, MessageBoxIcon.Error,
                MessageBoxDefaultButton.Button1, MessageBoxOptions.RtlReading | MessageBoxOptions.RightAlign);
            return 1;
        }
    }

    static bool Same(byte[] a, byte[] b)
    {
        if (a.Length != b.Length) return false;
        for (int i = 0; i < a.Length; i++) if (a[i] != b[i]) return false;
        return true;
    }
}
