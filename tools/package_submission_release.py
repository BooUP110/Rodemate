"""Sign the normal Production release with a persistent, local contest identity.

Requires Python 3, JDK keytool and Android build-tools 35.0.1. Run Gradle first.
This tool never installs an APK, uploads anything, or changes the Git index.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import secrets
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = ROOT / "development/submission/private-signing/signing.json"
UNSIGNED = ROOT / "app/build/outputs/apk/production/release/app-production-release-unsigned.apk"
OUTPUT = ROOT / "apk/BooUP_카펫_release.apk"


def run(args, env=None):
    result = subprocess.run([str(x) for x in args], cwd=ROOT, env=env,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        # Do not echo signing credentials, command lines, or arbitrary provider errors.
        raise RuntimeError(f"{Path(str(args[0])).name} failed (exit {result.returncode})")
    return result.stdout.decode("utf-8", errors="replace")


def require_private(path):
    relative = path.resolve().relative_to(ROOT).as_posix()
    git = ["git", "-c", f"safe.directory={ROOT.as_posix()}", "--no-optional-locks"]
    if run(git + ["ls-files", "--", relative]).strip():
        raise RuntimeError("Signing material must never be tracked")
    run(git + ["check-ignore", "--no-index", "--", relative])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--init-signing", action="store_true", help="Create a local key only if none exists")
    args = parser.parse_args()
    sdk_env = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk_env:
        raise RuntimeError("Set ANDROID_HOME to the installed Android SDK")
    build_tools = Path(sdk_env) / "build-tools/35.0.1"
    keytool = shutil.which("keytool")
    java = shutil.which("java")
    if not keytool or not java:
        raise RuntimeError("JDK keytool and java are required")
    config_path = DEFAULT_CONFIG
    require_private(config_path)
    if not config_path.exists():
        if not args.init_signing:
            raise RuntimeError("Local signing config missing; use --init-signing once, then back it up")
        config_path.parent.mkdir(parents=True, exist_ok=True)
        key_path = config_path.parent / "carpet-contest-release.p12"
        require_private(key_path)
        if key_path.exists():
            raise RuntimeError("Existing key without config: recover credentials; do not replace the key")
        config = {"keystore": key_path.relative_to(ROOT).as_posix(), "alias": "carpet-contest-release",
                  "password": secrets.token_urlsafe(48)}
        env = dict(os.environ, CARPET_SIGNING_PASSWORD=config["password"])
        run([keytool, "-genkeypair", "-keystore", key_path, "-storetype", "PKCS12",
             "-storepass:env", "CARPET_SIGNING_PASSWORD", "-alias", config["alias"],
             "-keyalg", "RSA", "-keysize", "3072", "-sigalg", "SHA256withRSA", "-validity", "10000",
             "-dname", "CN=BooUP Carpet Submission, OU=Contest, O=BooUP, C=KR"], env)
        # Exclusive creation: never replace an existing identity/config.
        with config_path.open("x", encoding="utf-8") as f:
            json.dump(config, f, indent=2)
            f.write("\n")
    config = json.loads(config_path.read_text(encoding="utf-8"))
    key_path = (ROOT / config["keystore"]).resolve()
    require_private(key_path)
    if not UNSIGNED.is_file():
        raise RuntimeError("Build :app:assembleProductionRelease without internal/acceptance flags first")
    env = dict(os.environ, CARPET_SIGNING_PASSWORD=config["password"])
    aapt = build_tools / ("aapt.exe" if os.name == "nt" else "aapt")
    align = build_tools / ("zipalign.exe" if os.name == "nt" else "zipalign")
    signer = [java, "-jar", build_tools / "lib/apksigner.jar"]
    metadata = run([aapt, "dump", "badging", UNSIGNED.relative_to(ROOT)])
    if "package: name='io.booup.roadmate'" not in metadata or "application-debuggable" in metadata:
        raise RuntimeError("Expected non-debuggable normal Production release")
    with zipfile.ZipFile(UNSIGNED) as z:
        names = z.namelist()
        dex = b"".join(z.read(n) for n in names if n.endswith(".dex"))
        forbidden = [b"DemoVehicleAdapter", b"CareDemoScenarios", b"DemoJourneyVisitSimulator",
                     b"Phase86Instrumentation", b"ClosureInstrumentation", b"TestKeys", b"PetRestTest",
                     b"PetPlaceSearchTest", b"PET_OWNER_FIXED", b"NEXT_PET_FIXED"]
        if any(marker in dex for marker in forbidden) or any("collab/" in n or "pass-worlds/" in n for n in names):
            raise RuntimeError("Demo, test or internal collab payload detected")
    OUTPUT.parent.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="sign-", dir=config_path.parent.parent) as temp:
        aligned = Path(temp) / "aligned.apk"
        signed = Path(temp) / "signed.apk"
        run([align, "-p", "-f", "4", UNSIGNED.relative_to(ROOT), aligned.relative_to(ROOT)])
        run(signer + ["sign", "--ks", key_path, "--ks-key-alias", config["alias"],
                      "--ks-pass", "env:CARPET_SIGNING_PASSWORD", "--key-pass", "env:CARPET_SIGNING_PASSWORD",
                      "--out", signed, aligned], env)
        verified = run(signer + ["verify", "--verbose", "--print-certs", signed])
        run([align, "-c", "-p", "4", signed.relative_to(ROOT)])
        digest_match = re.search(r"certificate SHA-256 digest: ([0-9a-fA-F]+)", verified)
        if not digest_match:
            raise RuntimeError("Certificate digest not returned")
        if OUTPUT.exists() and OUTPUT.read_bytes() != signed.read_bytes():
            backup = config_path.parent.parent / "previous-apks"
            backup.mkdir(exist_ok=True)
            old_hash = hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
            shutil.copy2(OUTPUT, backup / f"{old_hash}.apk")
        shutil.copyfile(signed, OUTPUT)
    report = {"path": OUTPUT.relative_to(ROOT).as_posix(), "package": "io.booup.roadmate",
              "bytes": OUTPUT.stat().st_size, "sha256": hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
              "certificate_sha256": digest_match.group(1), "alias": config["alias"],
              "signed": True, "debuggable": False, "zipalign": "PASS",
              "install_verification": "CLEAN_INSTALL_NOT_RUN",
              "badging": metadata.splitlines(), "signature_verification": verified.splitlines(),
              "forbidden_payload_markers": [], "source_unsigned_sha256": hashlib.sha256(UNSIGNED.read_bytes()).hexdigest()}
    (config_path.parent.parent / "signed-apk.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    # Public output contains certificate metadata only, never credentials or private keys.
    print(json.dumps({k: report[k] for k in ["path", "package", "bytes", "sha256", "certificate_sha256",
                                           "alias", "signed", "debuggable", "install_verification"]}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
