"""Check package identity, launcher label and pinned APK signing certificate before upload."""
import json
import os
from pathlib import Path
import re
import subprocess

sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '36.0.0'
expected = Path('signing/personal-certificate.sha256').read_text().strip()
metadata = []
signed = bool(os.environ.get('ANDROID_KEYSTORE_PATH'))
for module in ('mobile', 'automotive'):
    files = list(Path(f'{module}/build/outputs/apk/debug').glob('*.apk'))
    if len(files) != 1:
        raise SystemExit(f'Expected one {module} APK; got {len(files)}')
    apk = files[0]
    badging = subprocess.check_output([str(sdk / 'aapt'), 'dump', 'badging', str(apk)], text=True)
    package = re.search(r"^package: name='([^']+)'", badging, re.M).group(1)
    if package != 'com.whoyang233.xcertplay':
        raise SystemExit(f'Wrong application ID: {package}')
    if "application-label:'xcertplay 个人版'" not in badging:
        raise SystemExit('Personal launcher label is missing')
    if signed:
        verification = subprocess.check_output([str(sdk / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
        certs = re.findall(r'^Signer #[0-9]+ certificate SHA-256 digest: (\w+)$', verification, re.M)
        if certs != [expected]:
            raise SystemExit(f'{module} APK signer does not match the pinned certificate')
    else:
        if subprocess.run([str(sdk / 'apksigner'), 'verify', str(apk)], capture_output=True).returncode == 0:
            raise SystemExit('Bootstrap APK unexpectedly signed; refusing unpinned signatures')
    metadata.append({'file': str(apk), 'package': package, 'signed': signed, 'certificate_sha256': expected if signed else None})
Path('personal-apk-verification.json').write_text(json.dumps(metadata, indent=2) + '\n')
print(json.dumps(metadata, indent=2))
