"""Decode the existing personal key. Never generate a key in CI or fall back to a debug key."""
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess

bundle = os.environ.get('PERSONAL_ANDROID_SIGNING', '')
unsigned = os.environ.get('ALLOW_UNSIGNED_BOOTSTRAP', '') == 'true'
output = Path(os.environ['GITHUB_OUTPUT'])
github_env = Path(os.environ['GITHUB_ENV'])
if not bundle:
    if not unsigned:
        raise SystemExit('Missing PERSONAL_ANDROID_SIGNING. Add the saved signing bundle in repository Actions secrets. No random signing key will be used.')
    with output.open('a') as stream:
        stream.write('available=false\nartifact_prefix=unsigned-apks\n')
    with github_env.open('a') as stream:
        stream.write('UNSIGNED_BUILD_ARGUMENT=-Pxcertplay.allowUnsigned=true\n')
    print('Explicit unsigned bootstrap/PR build. APKs must be signed with the pinned key before installation.')
    raise SystemExit(0)

try:
    data = json.loads(bundle)
    values = {
        'ANDROID_KEYSTORE_PASSWORD': data['store_password'],
        'ANDROID_KEY_ALIAS': data['key_alias'],
        'ANDROID_KEY_PASSWORD': data['key_password'],
    }
    if not all(isinstance(v, str) and v and '\n' not in v and '\r' not in v for v in values.values()):
        raise ValueError('Invalid signing fields')
    binary = base64.b64decode(data['keystore_base64'], validate=True)
except (KeyError, ValueError, TypeError):
    raise SystemExit('PERSONAL_ANDROID_SIGNING is invalid; restore the original saved bundle.') from None
# Register derived secrets before any subsequent subprocess can report them.
for value in values.values():
    print(f'::add-mask::{value}')

keystore = Path(os.environ['RUNNER_TEMP']) / 'xcertplay-personal.p12'
keystore.write_bytes(binary)
keystore.chmod(0o600)
env = os.environ.copy()
env.update(values)
certificate = subprocess.check_output([
    'keytool', '-exportcert', '-keystore', str(keystore), '-storetype', 'PKCS12',
    '-storepass:env', 'ANDROID_KEYSTORE_PASSWORD', '-alias', values['ANDROID_KEY_ALIAS'],
], env=env)
expected = Path('signing/personal-certificate.sha256').read_text().strip()
if hashlib.sha256(certificate).hexdigest() != expected:
    raise SystemExit('Signing certificate mismatch. Refusing to publish an APK with a different key.')
with github_env.open('a') as stream:
    for name, value in {**values, 'ANDROID_KEYSTORE_PATH': str(keystore), 'UNSIGNED_BUILD_ARGUMENT': ''}.items():
        stream.write(f'{name}={value}\n')
with output.open('a') as stream:
    stream.write('available=true\nartifact_prefix=personal-apks\n')
print(f'Fixed personal certificate verified: {expected}')
