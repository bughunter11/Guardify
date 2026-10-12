#!/usr/bin/env python3
"""Guardify: GitHub pe pehli release ka poora setup, ek command me.

Chalane ka tareeka (Guardify project ke root me, jahan gradlew hai):
    python3 release_guardify.py                # poori pehli release
    python3 release_guardify.py --dry-run      # sirf files banao, git/gh/build kuch nahi
    python3 release_guardify.py --screenshots  # screenshots/ ki images README me daalo aur push karo

Ye karta hai:
  1. Checks (git, gh login, keystore, signing)
  2. .gitignore, LICENSE, README.md banata hai
  3. Links.kt me GitHub link bharta hai (Settings me "Source code" row dikhe)
  4. Release APK build + signature verify (debug key se sign ho to ruk jata hai)
  5. Secret leak check (keystore/password repo me na jaye)
  6. Pura source code GitHub pe push
  7. v<version> release banata hai, APK attach karke
"""
import datetime
import hashlib
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path.cwd()
APP_GRADLE = ROOT / "app/build.gradle.kts"
LINKS = ROOT / "app/src/main/java/com/bughunter11/guardify/ui/Links.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
KEYPROPS = ROOT / "keystore.properties"
BACKUP = ROOT / ".release_backup"
SHOTS = ROOT / "screenshots"

GI_LINES = [
    "*.iml", ".gradle/", "local.properties", "build/", "app/build/", ".idea/",
    "keystore.properties", "*.jks", "*.keystore",
    "*.apk", "dist/",
    "project_dump.txt", ".support_patch_backup/", ".release_backup/",
]

FORBIDDEN = [
    r"(^|/)keystore\.properties$", r"\.jks$", r"\.keystore$", r"(^|/)local\.properties$",
    r"(^|/)project_dump\.txt$", r"\.apk$", r"(^|/)build/", r"(^|/)\.gradle/",
    r"^\.support_patch_backup/", r"^\.release_backup/", r"^dist/",
]

TOPICS = ["android", "firewall", "kotlin", "jetpack-compose", "material-you",
          "data-usage", "vpnservice", "no-root"]

MARK_START = "<!-- SCREENSHOTS:START -->"
MARK_END = "<!-- SCREENSHOTS:END -->"


# ---------------------------------------------------------------- helpers
def die(msg):
    print(f"\nError: {msg}")
    sys.exit(1)


def run(cmd, check=True, capture=False, **kw):
    """Command chalao. capture=True to output return karo, warna live dikhao."""
    if capture:
        r = subprocess.run(cmd, capture_output=True, text=True, **kw)
        if check and r.returncode != 0:
            die(f"{' '.join(cmd)}\n{r.stderr.strip() or r.stdout.strip()}")
        return r.stdout.strip()
    r = subprocess.run(cmd, **kw)
    if check and r.returncode != 0:
        die(f"{' '.join(cmd)} fail hua (code {r.returncode}).")
    return r.returncode


def ask(prompt, default=None):
    suffix = f" [{default}]" if default is not None else ""
    val = input(f"{prompt}{suffix}: ").strip()
    return val or (default if default is not None else "")


def yes(prompt, default=True):
    d = "Y/n" if default else "y/N"
    val = input(f"{prompt} ({d}): ").strip().lower()
    if not val:
        return default
    return val in ("y", "yes", "haan", "h")


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def read_props(path):
    props = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            props[k.strip()] = v.strip()
    return props


def gradle_value(name):
    m = re.search(rf'{name}\s*=\s*"?([^"\n]+)"?', APP_GRADLE.read_text(encoding="utf-8"))
    return m.group(1).strip() if m else None


# ---------------------------------------------------------------- file generators
def write_gitignore():
    gi = ROOT / ".gitignore"
    existing = gi.read_text(encoding="utf-8").splitlines() if gi.exists() else []
    have = {l.strip() for l in existing}
    add = [l for l in GI_LINES if l not in have]
    if add:
        with open(gi, "a", encoding="utf-8") as f:
            if existing and existing[-1].strip():
                f.write("\n")
            f.write("\n".join(add) + "\n")
    return len(add)


def write_license(holder):
    year = datetime.date.today().year
    text = f"""MIT License

Copyright (c) {year} {holder}

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
"""
    (ROOT / "LICENSE").write_text(text, encoding="utf-8")


def links_const(name):
    if not LINKS.exists():
        return None
    m = re.search(rf'const val {name}\s*=\s*"([^"]*)"', LINKS.read_text(encoding="utf-8"))
    return m.group(1) if m and m.group(1) else None


def build_readme(user, repo, version, has_license):
    manifest = MANIFEST.read_text(encoding="utf-8")
    no_internet = "android.permission.INTERNET" not in manifest
    has_support = LINKS.exists() and "object Support" in LINKS.read_text(encoding="utf-8")
    channel, group = links_const("CHANNEL"), links_const("GROUP")

    privacy = (
        "- **No `INTERNET` permission.** The app cannot send your data anywhere. Everything stays on your phone.\n"
        if no_internet else
        "- Rules and usage data stay on your phone.\n"
    )
    community = ""
    if channel or group:
        community = "\n## Community\n"
        if channel:
            community += f"- Updates: {channel}\n"
        if group:
            community += f"- Help and feedback: {group}\n"
    support = ""
    if has_support:
        support = "\n## Support\nIf Guardify is useful to you, you can support the project from **Settings → About → Support** inside the app.\n"
    lic = "\n## License\n[MIT](LICENSE)\n" if has_license else ""

    return f"""# Guardify

Per-app firewall and data tracker for Android. No root, no server, everything runs locally.
Built with Material 3, Jetpack Compose and Material You dynamic colors.

{MARK_START}
{MARK_END}

## Features

**Automation**
- **Data limits:** monthly mobile data cap for every app, with a billing cycle day you can set. When an app crosses its limit it is blocked on mobile data, and it unblocks itself in the next cycle.
- **Schedules:** time, days, network and apps (All, Category, or Pick apps). Rules that cross midnight work too (for example 11 PM to 6 AM). Ready-made presets: Night block and Study time.
- **Background data:** an app gets internet only while it is open. After you close it, internet is cut after a short grace delay. Needs Usage access permission.
- **Roaming mode:** one switch to give mobile data only to essential apps while you travel. WiFi is never touched. With "Auto" on, it turns on when your network starts roaming and turns off when you leave. If you pick no essential apps, Guardify suggests them from your phone (dialer, SMS, maps, WhatsApp, UPI, cab apps). Newly installed apps are blocked automatically.

**Quick controls and alerts**
- **Quick Settings tiles:** "Mobile data off" (block every app on mobile data in one tap) and "Focus". Long-press a tile to open the quick controls sheet.
- **Home screen widget** (Jetpack Glance, Material You colors): the same two buttons in a small (2x1) and a large (3x2) size.
- **Block all mobile data:** system apps stay out by default (change it with "Include system apps"), and "Keep these apps online" lets chosen apps through.
- **Quick focus:** blocks the apps you pick, or Social + Games + Video by default, for 15 minutes to 2 hours.
- **Data alerts:** a notification when an app crosses your limit today, or uses 50 MB or more within 10 minutes, with "Block mobile" and "Ignore today" actions.

All rules are enforced by the firewall service, even when the Guardify app is closed.

## Install

1. Download `Guardify-v{version}.apk` from the [latest release](https://github.com/{user}/{repo}/releases/latest).
2. Open it and allow "Install unknown apps" for your browser or file manager when Android asks.
3. Open Guardify and allow the VPN request. This is Android's local VPN API that the firewall uses.

**Requires Android 10 (API 29) or newer.**

The release APK is signed with the developer's own key. You can check that a download is genuine by comparing its SHA-256 with the value in the release notes.

## Permissions

| Permission | Why |
|---|---|
| VPN (`BIND_VPN_SERVICE`) | Local VPN used to allow or block each app. No traffic leaves your phone through it. |
| Usage access (`PACKAGE_USAGE_STATS`) | Detect which app is open, for the background data feature. |
| Notifications | Data alerts, roaming mode and new-app alerts. |
| Query all packages | List the installed apps so you can set rules for them. |
| Receive boot completed | Bring the firewall back after a reboot. |

## Privacy
{privacy}- No accounts, no analytics, no ads.
{support}{community}
## Build from source

```bash
git clone https://github.com/{user}/{repo}.git
cd {repo}
./gradlew assembleRelease
```

The APK is created at `app/build/outputs/apk/release/app-release.apk`.

Without a keystore the release build is signed with the debug key, which is fine for testing. To sign with your own key, create `keystore.properties` in the project root (never commit it, it is already in `.gitignore`):

```properties
storeFile=/path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

On Termux you may also need an `android.aapt2FromMavenOverride` line in `gradle.properties`.

## Tech

Kotlin, Jetpack Compose, Material 3, Jetpack Glance, Android `VpnService`.
{lic}"""


def build_notes(version, apk_name, apk_sha, cert_sha):
    cert_line = f"\nSigning certificate SHA-256: `{cert_sha}`" if cert_sha else ""
    return f"""## Guardify v{version}

First public release.

### Highlights
- Per-app firewall for mobile data and WiFi, no root needed
- Monthly data limits per app
- Schedules, including rules that cross midnight
- Background data control and roaming mode
- Quick Settings tiles and a home screen widget
- Data alerts when an app uses too much
- Material 3 with Material You dynamic colors

### Install
Download **{apk_name}**, open it, and allow installs from this source if Android asks. Requires Android 10 or newer.

### Verify
`{apk_name}` SHA-256: `{apk_sha}`{cert_line}
"""


def update_readme_gallery(readme_text):
    imgs = sorted(p for p in SHOTS.glob("*") if p.suffix.lower() in (".png", ".jpg", ".jpeg", ".webp"))
    if not imgs:
        return None, 0
    cells = "\n".join(
        f'  <img src="screenshots/{p.name}" width="30%" alt="Screenshot {i}">'
        for i, p in enumerate(imgs, 1)
    )
    block = f'{MARK_START}\n<p align="center">\n{cells}\n</p>\n{MARK_END}'
    pat = re.compile(re.escape(MARK_START) + r".*?" + re.escape(MARK_END), re.S)
    if not pat.search(readme_text):
        return None, 0
    return pat.sub(lambda _: block, readme_text, count=1), len(imgs)


# ---------------------------------------------------------------- safety
def safety_check():
    """Git me jane wali files me secrets to nahi? Dikhe to rok do."""
    tracked = run(["git", "ls-files"], capture=True).splitlines()
    bad = [f for f in tracked if any(re.search(p, f) for p in FORBIDDEN)]
    if bad:
        die("Ye files git me add ho gayi hain, upload nahi honi chahiye:\n  " + "\n  ".join(bad))

    if KEYPROPS.exists():
        props = read_props(KEYPROPS)
        for key in ("storePassword", "keyPassword"):
            val = props.get(key, "")
            if len(val) >= 4:
                r = subprocess.run(["git", "grep", "--cached", "-F", "-q", "--", val],
                                   capture_output=True, text=True)
                if r.returncode == 0:
                    die(f"Tumhara {key} kisi tracked file ke andar mila (hardcode hai). "
                        "Pehle usse hatao, tab upload karo.")
    print("OK   Safety check: koi secret file ya password git me nahi.")


# ---------------------------------------------------------------- modes
def screenshots_mode():
    readme = ROOT / "README.md"
    if not readme.exists() or not (ROOT / ".git").exists():
        die("Pehle release_guardify.py (bina option) chala ke repo banao.")
    if not SHOTS.is_dir():
        die("screenshots/ folder nahi hai. Images wahan daalo (png/jpg/webp).")
    new_text, n = update_readme_gallery(readme.read_text(encoding="utf-8"))
    if new_text is None:
        die("screenshots/ me images nahi mili, ya README me SCREENSHOTS markers nahi hain.")
    readme.write_text(new_text, encoding="utf-8")
    run(["git", "add", "README.md", "screenshots"])
    if subprocess.run(["git", "diff", "--cached", "--quiet"]).returncode == 0:
        print("Kuch naya nahi mila, README pehle se updated hai.")
        return
    run(["git", "commit", "-m", "Add screenshots"])
    run(["git", "push"])
    print(f"Ho gaya. {n} screenshot(s) README me daal ke push kar diye.")


def main():
    args = sys.argv[1:]
    dry = "--dry-run" in args

    if not APP_GRADLE.is_file() or not (ROOT / "gradlew").is_file():
        die("Ye script Guardify ke root folder me chalao (jahan gradlew hai).")

    if "--screenshots" in args:
        screenshots_mode()
        return

    print("=== Guardify: GitHub pehli release ===\n")

    # ---- preflight
    if not dry:
        for tool in ("git", "gh"):
            if not shutil.which(tool):
                die(f"{tool} install nahi hai. Chalao: pkg install git gh")
        if subprocess.run(["gh", "auth", "status"], capture_output=True).returncode != 0:
            die("GitHub login nahi hai. Pehle chalao: gh auth login")
        user = run(["gh", "api", "user", "-q", ".login"], capture=True)
    else:
        user = ask("GitHub username (dry-run)", "bughunter11")

    if not dry and (ROOT / ".git").exists() and run(["git", "remote"], capture=True):
        die("Ye folder pehle se GitHub se juda hai. Nayi release ke liye tag alag se banao.")

    if not KEYPROPS.exists():
        die("keystore.properties nahi mili. Bina isse release APK debug key se sign hoga. "
            "Pehle apni keystore set karo.")

    version = gradle_value("versionName") or "1.0"
    tag = f"v{version}"
    print(f"GitHub user : {user}")
    print(f"Version     : {version}  (tag {tag})\n")

    repo = ask("Repo ka naam", "Guardify")
    private = ask("Public ya private? (public/private)", "public").lower() == "private"
    want_license = yes("MIT license lagana hai? (koi bhi use/modify kar sakega)", True)
    holder = ask("License me naam", "Raj") if want_license else ""
    desc = ask("Repo description",
               "Per-app firewall and data tracker for Android. No root, no server.")

    if not dry and not subprocess.run(["git", "config", "user.name"], capture_output=True, text=True).stdout.strip():
        run(["git", "config", "--global", "user.name", ask("Git naam", user)])
    if not dry and not subprocess.run(["git", "config", "user.email"], capture_output=True, text=True).stdout.strip():
        run(["git", "config", "--global", "user.email", ask("Git email")])

    if not dry:
        exists = subprocess.run(["gh", "repo", "view", f"{user}/{repo}"], capture_output=True).returncode == 0
        if exists:
            die(f"{user}/{repo} GitHub pe pehle se hai. Doosra naam chuno ya usse delete karo.")

    print(f"\nRepo     : {user}/{repo} ({'private' if private else 'public'})")
    print(f"License  : {'MIT' if want_license else 'koi nahi'}")
    if not yes("Sab sahi hai? Shuru karein?", True):
        die("Cancel kiya.")

    # ---- files
    BACKUP.mkdir(exist_ok=True)
    old_readme = ROOT / "README.md"
    if old_readme.exists():
        shutil.copy2(old_readme, BACKUP / "README.md")
    n = write_gitignore()
    print(f"OK   .gitignore ({n} nayi lines)")
    if want_license:
        write_license(holder)
        print("OK   LICENSE (MIT)")
    SHOTS.mkdir(exist_ok=True)
    (SHOTS / ".gitkeep").touch()

    # ---- Links.kt GitHub link
    links_changed = False
    if LINKS.exists():
        txt = LINKS.read_text(encoding="utf-8")
        url = f"https://github.com/{user}/{repo}"
        new, c = re.subn(r'(const val GITHUB\s*=\s*)""', rf'\1"{url}"', txt, count=1)
        if c:
            shutil.copy2(LINKS, BACKUP / "Links.kt")
            LINKS.write_text(new, encoding="utf-8")
            links_changed = True
            print("OK   Links.kt me GitHub link set (Settings me Source code row dikhegi)")

    (ROOT / "README.md").write_text(build_readme(user, repo, version, want_license), encoding="utf-8")
    print("OK   README.md")

    if dry:
        print("\n--dry-run: yahin ruk gaya. README.md, LICENSE, .gitignore dekh lo.")
        print("Purani README ka backup .release_backup/ me hai.")
        return

    # ---- build
    apk_src = ROOT / "app/build/outputs/apk/release/app-release.apk"
    if links_changed or not apk_src.exists() or yes("Fresh release build karein? (recommended)", True):
        print("\nBuild chal raha hai, thoda time lagega...\n")
        run(["./gradlew", "clean", "assembleRelease"])
    if not apk_src.exists():
        die(f"APK nahi bani: {apk_src}")

    # ---- signature verify
    cert_sha = None
    if shutil.which("apksigner"):
        out = run(["apksigner", "verify", "--print-certs", str(apk_src)], capture=True)
        if "Android Debug" in out:
            die("APK abhi bhi DEBUG key se sign hai. keystore.properties ka storeFile path check karo. "
                "Kuch upload nahi hua.")
        m = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]+)", out)
        cert_sha = m.group(1).lower() if m else None
        print(f"OK   APK tumhari apni key se sign hai ({cert_sha[:16]}...)" if cert_sha else "OK   APK signed")
    else:
        print("WARN apksigner nahi mila, signature check skip. (APK Builder tool me manually check kar lena.)")
        if not yes("Phir bhi aage badhein?", False):
            die("Cancel kiya.")

    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    apk_name = f"Guardify-v{version}.apk"
    apk = dist / apk_name
    shutil.copy2(apk_src, apk)
    apk_sha = sha256_file(apk)
    notes = dist / "release_notes.md"
    notes.write_text(build_notes(version, apk_name, apk_sha, cert_sha), encoding="utf-8")

    # ---- git
    if not (ROOT / ".git").exists():
        run(["git", "init", "-q"])
    run(["git", "add", "-A"])
    safety_check()
    run(["git", "commit", "-q", "-m", f"Guardify v{version}: initial release"])
    run(["git", "branch", "-M", "main"])

    print("\nGitHub pe repo ban raha hai aur code push ho raha hai...")
    run(["gh", "repo", "create", f"{user}/{repo}",
         "--private" if private else "--public",
         "--description", desc, "--source=.", "--remote=origin", "--push"])
    for t in TOPICS:
        run(["gh", "repo", "edit", f"{user}/{repo}", "--add-topic", t], check=False,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    print("\nRelease ban rahi hai, APK attach ho rahi hai...")
    run(["gh", "release", "create", tag, str(apk),
         "--repo", f"{user}/{repo}", "--title", f"Guardify v{version}",
         "--notes-file", str(notes)])

    base = f"https://github.com/{user}/{repo}"
    print("\n=== Ho gaya ===")
    print(f"Repo    : {base}")
    print(f"Release : {base}/releases/tag/{tag}")
    print("\nAb dhyan rakho:")
    print(f"  1. {base} khol ke check karo ki keystore.properties wahan nahi hai.")
    print("  2. Screenshots: images screenshots/ folder me daalo, fir chalao:")
    print("       python3 release_guardify.py --screenshots")
    print("  3. Release APK me GitHub link already hai. Is APK ko hi install karke test kar lo.")


if __name__ == "__main__":
    try:
        main()
    except (KeyboardInterrupt, EOFError):
        print("\nCancel kiya.")
        sys.exit(1)
