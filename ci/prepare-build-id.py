"""Allocate YYYYMMDDNN from Shanghai workflow day/order and already published build numbers."""
import datetime as dt
import json
import os
import re
import urllib.parse
import urllib.request
from pathlib import Path
from zoneinfo import ZoneInfo

BASE_URL = 'https://apk.frp.whoyang.cn/xcertplay/release/'
FILE_PATTERN = re.compile(r'xcertplay-\d{1,4}\.\d{1,4}\.\d{1,4}B-release-(\d{10})\.apk')
ARTIFACT_PATTERN = re.compile(r'(?:personal|unsigned)-apks-automotive-release-(\d{10})-[0-9a-f]{40}')

def valid_build_id(value):
    try:
        return (bool(re.fullmatch(r'\d{10}', value)) and
                dt.datetime.strptime(value[:8], '%Y%m%d').strftime('%Y%m%d') == value[:8] and
                1 <= int(value[8:]) <= 99 and 2000010101 <= int(value) <= 2100000000)
    except (ValueError, TypeError):
        return False

def allocate(day, ordinal, known):
    sequences = [int(value[-2:]) for value in known if valid_build_id(value) and value.startswith(day)]
    sequence = max(ordinal, max(sequences, default=0) + 1)
    candidate = day + f'{sequence:02d}'
    if not valid_build_id(candidate):
        raise ValueError('Daily build sequence must be 01..99')
    return candidate

def read_json(url, token=None, maximum=4 * 1024 * 1024):
    headers = {'Accept': 'application/json', 'Cache-Control': 'no-cache'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=20) as response:
        body = response.read(maximum + 1)
    if len(body) > maximum:
        raise ValueError('Response too large')
    return json.loads(body)

def main():
    repository = os.environ['GITHUB_REPOSITORY']
    token = os.environ['GITHUB_TOKEN']
    run_id = int(os.environ['GITHUB_RUN_ID'])
    api = 'https://api.github.com/repos/' + repository
    current = read_json(api + '/actions/runs/' + str(run_id), token)
    started = dt.datetime.fromisoformat(current['created_at'].replace('Z', '+00:00'))
    shanghai = ZoneInfo('Asia/Shanghai')
    day = started.astimezone(shanghai).strftime('%Y%m%d')
    beginning = started.astimezone(shanghai).replace(hour=0, minute=0, second=0, microsecond=0)
    query = urllib.parse.urlencode({'created': '>=' + beginning.astimezone(dt.timezone.utc).isoformat(), 'per_page': 100})
    runs = read_json(api + '/actions/workflows/android.yml/runs?' + query, token)['workflow_runs']
    ordinal = sum(1 for run in runs if int(run['id']) <= run_id and
                  dt.datetime.fromisoformat(run['created_at'].replace('Z', '+00:00')).astimezone(shanghai).strftime('%Y%m%d') == day)
    if ordinal < 1:
        raise ValueError('Current workflow is absent from its build-day history')
    known = []
    for artifact in read_json(api + '/actions/artifacts?per_page=100', token)['artifacts']:
        match = ARTIFACT_PATTERN.fullmatch(artifact['name'])
        if match:
            known.append(match[1])
    try:
        listing = read_json(BASE_URL + '?json', maximum=512 * 1024)
        for item in listing['paths']:
            match = FILE_PATTERN.fullmatch(item.get('name', ''))
            if match and item.get('path_type') == 'File':
                known.append(match[1])
    except Exception:
        print('Update directory unavailable; retaining GitHub build history ordering.')
    build_id = allocate(day, ordinal, known)
    with Path(os.environ['GITHUB_ENV']).open('a') as output:
        output.write('PERSONAL_BUILD_ID=' + build_id + '\n')
    with Path(os.environ['GITHUB_OUTPUT']).open('a') as output:
        output.write('build_id=' + build_id + '\n')
    print('Personal build identifier: ' + build_id)

if __name__ == '__main__':
    main()
