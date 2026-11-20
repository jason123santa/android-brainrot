#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check legacy packaging invariants; not a substitute for device testing."""
import hashlib
import os
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / 'dist/brainrot-legacy-debug.apk'
with zipfile.ZipFile(APK) as apk:
    names = apk.namelist()
    assert not any(name.startswith('lib/') or name.endswith('.so') for name in names)
    assert [name for name in names if name.endswith('.dex')] == ['classes.dex']
    dex = apk.read('classes.dex')
    assert dex[:8] == b'dex\n035\x00'
    assert dex[12:32] == hashlib.sha1(dex[32:]).digest()
    assert struct.unpack('<I', dex[8:12])[0] == zlib.adler32(dex[12:]) & 0xffffffff
    assert apk.getinfo('assets/model.bin').compress_type == zipfile.ZIP_STORED
    assert apk.read('assets/model.bin') == (ROOT / 'assets/model.bin').read_bytes()
    for document in ['LICENSE', 'NOTICE', 'MODEL_LICENSE.md', 'THIRD_PARTY_NOTICES.md',
                     'licenses/OpenAI-GPT2-LICENSE.txt']:
        assert apk.read('assets/legal/' + document) == (ROOT / document).read_bytes()
    assert apk.read('assets/legal/upstream-licenses.json') == (ROOT / 'docs/upstream-licenses.json').read_bytes()
    signatures = [n for n in names if n.startswith('META-INF/') and n.endswith('.SF')]
    assert signatures and b'SHA1-Digest' in apk.read(signatures[0])
    assert any(n.startswith('META-INF/') and n.endswith('.RSA') for n in names)
manifest = ET.parse(ROOT / 'AndroidManifest.xml').getroot()
android = '{http://schemas.android.com/apk/res/android}'
assert manifest.find('uses-sdk').get(android + 'minSdkVersion') == '3'
assert manifest.find('uses-sdk').get(android + 'maxSdkVersion') is None
assert not manifest.findall('uses-permission')
screens = manifest.find('supports-screens')
assert screens is not None
for attribute in ['smallScreens', 'normalScreens', 'largeScreens', 'xlargeScreens', 'anyDensity', 'resizeable']:
    assert screens.get(android + attribute) == 'true'
activity = manifest.find('application/activity')
assert activity.get(android + 'windowSoftInputMode') == 'adjustResize|stateAlwaysHidden'
assert activity.get(android + 'resizeableActivity') == 'true'
assert activity.get(android + 'maxAspectRatio') == '0.0'
aapt = os.environ.get('AAPT', 'aapt')
compiled_manifest = subprocess.check_output([aapt, 'dump', 'xmltree', str(APK), 'AndroidManifest.xml'], text=True)
assert 'android:minSdkVersion' in compiled_manifest and '(type 0x10)0x3' in compiled_manifest
assert 'E: uses-permission' not in compiled_manifest
assert 'android:windowSoftInputMode' in compiled_manifest and '(type 0x11)0x13' in compiled_manifest
assert 'android:resizeableActivity' in compiled_manifest and 'android:maxAspectRatio' in compiled_manifest
badging = subprocess.check_output([aapt, 'dump', 'badging', str(APK)], text=True)
assert "supports-any-density: 'true'" in badging
screen_line = next(line for line in badging.splitlines() if line.startswith('supports-screens:'))
for size in ['small', 'normal', 'large', 'xlarge']:
    assert "'" + size + "'" in screen_line
print('PASS: API 3 minimum, DEX 035 integrity, no native ABI restriction, model/signature/notices, density/screen/keyboard declarations')
print('APK SHA-256:', hashlib.sha256(APK.read_bytes()).hexdigest())
