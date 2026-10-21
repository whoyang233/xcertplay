"""Publish an already verified personal automotive APK without reading signing keys or rebuilding."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

repo = os.environ['GH_REPO']
run_id = os.environ['VERIFIED_RUN_ID']
tag = os.environ['PERSONAL_RELEASE_TAG']
if not run_id.isdecimal() or not re.fullmatch(r'personal-[A-Za-z0-9_.-]{1,95}', tag):
    raise SystemExit('A verified run ID and a personal- release tag are required')

def gh(*args):
    return subprocess.check_output(['gh', *args], text=True)

run = json.loads(gh('api', f'repos/{repo}/actions/runs/{run_id}'))
if (run['status'], run['conclusion'], run['head_branch'], run['head_repository']['full_name']) != (
    'completed', 'success', 'personal-dev', repo,
) or run['path'] != '.github/workflows/android.yml':
    raise SystemExit('Only a successful personal-dev Android build from this repository may be published')
sha = run['head_sha']
config = Path('automotive/build.gradle.kts').read_text()
version = re.search(r'versionName\s*=\s*"([^"]+)"', config).group(1) + 'B'
code = int(re.search(r'versionCode\s*=\s*(\d+)', config).group(1))
certificate = Path('signing/personal-certificate.sha256').read_text().strip()
sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '36.0.0'
with tempfile.TemporaryDirectory(prefix='verified-personal-apk-') as temporary:
    root = Path(temporary)
    gh('run', 'download', run_id, '--repo', repo, '--name', f'personal-apks-{sha}', '--dir', str(root/'apk'))
    gh('run', 'download', run_id, '--repo', repo, '--name', f'validation-reports-{sha}', '--dir', str(root/'reports'))
    apks = list((root/'apk').rglob('*.apk'))
    records = list((root/'apk').rglob('personal-apk-verification.json'))
    if len(apks) != 1 or len(records) != 1 or apks[0].name != 'automotive-debug.apk':
        raise SystemExit('Expected one automotive APK and its verification metadata')
    metadata = json.loads(records[0].read_text())
    if len(metadata) != 1 or any(metadata[0].get(k) != v for k, v in {
        'package': 'com.whoyang233.xcertplay', 'signed': True,
        'version_name': version, 'version_code': code, 'certificate_sha256': certificate,
        'web_display_enabled': False,
    }.items()):
        raise SystemExit('Personal APK verification metadata does not match')
    apk = apks[0]
    verified = subprocess.check_output([str(sdk/'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
    if re.findall(r'^Signer #[0-9]+ certificate SHA-256 digest: (\w+)$', verified, re.M) != [certificate]:
        raise SystemExit('APK does not match the fixed signing identity')
    badging = subprocess.check_output([str(sdk/'aapt'), 'dump', 'badging', str(apk)], text=True)
    package = re.search(r"^package: name='([^']+)'", badging, re.M).group(1)
    if package != 'com.whoyang233.xcertplay' or f"versionName='{version}'" not in badging or f"versionCode='{code}'" not in badging:
        raise SystemExit('APK manifest identity does not match')
    if "application-label:'xcertplay 个人版'" not in badging:
        raise SystemExit('Personal app label does not match')
    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    for report in (root/'reports').rglob('TEST-*.xml'):
        if 'test-results' not in report.parts:
            continue
        suite = ET.parse(report).getroot()
        for key in counts:
            counts[key] += int(suite.attrib.get(key, 0))
    if counts['tests'] == 0 or any(counts[k] for k in ('failures', 'errors', 'skipped')):
        raise SystemExit('Unit test reports are incomplete or unsuccessful')
    lint_reports = list((root/'reports').rglob('lint-results-debug.xml'))
    if len(lint_reports) != 2 or any(issue.attrib['severity'] in ('Error', 'Fatal') for p in lint_reports for issue in ET.parse(p).getroot().iter('issue')):
        raise SystemExit('Lint reports are incomplete or unsuccessful')
    filename = f'xcertplay-personal-automotive-{version}.apk'
    output = root/filename
    apk.rename(output)
    checksum = hashlib.sha256(output.read_bytes()).hexdigest()
    sums = root/'SHA256SUMS.txt'
    sums.write_text(f'{checksum}  {filename}\n')
    notes = root/'release-notes.md'
    notes.write_text(f'''xcertplay 个人版 {version}，仅提供 automotive 车载 APK。

- BYD 默认车标与名称，保留已有自定义图标和名称；自定义大图最多以 192×192 发送和预览。
- 点击 CarPlay 车标返回车机默认桌面，保持会话运行；拒绝过期请求，桌面不可用时不断开。
- 包含设置布局修复、移除重复用途绑定（保留 0–20 编号通道与导航独立音量）、音视频 0 ms 档位和 Web 渲染完整移除。
- 保留非全屏切换保护、启动缩放、Wi-Fi 恢复/信道策略及只读 BYD ADB 数据。

包名 `{package}`，版本 {version} / {code}，固定签名与此前个人版一致，可覆盖安装。

源码：`{sha}`
构建与验证：https://github.com/{repo}/actions/runs/{run_id}
{counts['tests']} 项测试通过，无失败、错误或跳过；Lint 无错误。车机桌面切换和路由仍需实车验收。

APK SHA-256：`{checksum}`
BYD 图标及迁移来源说明见源码 `docs/BYD_CAR_ICON.zh-CN.md`。
''')
    release = subprocess.run(['gh','release','view',tag,'--repo',repo,'--json','isDraft,targetCommitish'], capture_output=True, text=True)
    if release.returncode == 0:
        current = json.loads(release.stdout)
        if not current['isDraft'] or current['targetCommitish'] != sha:
            raise SystemExit('Refusing to replace an existing published release or a different source commit')
    else:
        gh('release','create',tag,'--repo',repo,'--target',sha,'--draft','--prerelease','--latest=false',
           '--title',f'xcertplay 个人版 {version} · BYD 车标与返回桌面','--notes-file',str(notes))
    gh('release','upload',tag,str(output),str(sums),'--repo',repo,'--clobber')
    gh('release','edit',tag,'--repo',repo,'--draft=false','--prerelease','--latest=false','--notes-file',str(notes))
    result = dict(source_commit=sha, build_run=int(run_id), tag=tag, apk_sha256=checksum,
                  apk_bytes=output.stat().st_size, certificate_sha256=certificate, unit_tests=counts,
                  download_url=f'https://github.com/{repo}/releases/download/{tag}/{filename}')
    Path('publication-verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(result,ensure_ascii=False))
