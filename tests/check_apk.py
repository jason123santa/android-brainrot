#!/usr/bin/env python3
"""Check legacy packaging invariants; not a substitute for device testing."""
import hashlib
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
    signatures = [n for n in names if n.startswith('META-INF/') and n.endswith('.SF')]
    assert signatures and b'SHA1-Digest' in apk.read(signatures[0])
    assert any(n.startswith('META-INF/') and n.endswith('.RSA') for n in names)
manifest = ET.parse(ROOT / 'AndroidManifest.xml').getroot()
android = '{http://schemas.android.com/apk/res/android}'
assert manifest.find('uses-sdk').get(android + 'minSdkVersion') == '3'
assert manifest.find('uses-sdk').get(android + 'maxSdkVersion') is None
assert not manifest.findall('uses-permission')
compiled_manifest = subprocess.check_output(['aapt', 'dump', 'xmltree', str(APK), 'AndroidManifest.xml'], text=True)
assert 'android:minSdkVersion' in compiled_manifest and '(type 0x10)0x3' in compiled_manifest
assert 'E: uses-permission' not in compiled_manifest
print('PASS: API 3 manifest, DEX 035 integrity, no native ABI restriction, uncompressed model, SHA-1 JAR signature')
print('APK SHA-256:', hashlib.sha256(APK.read_bytes()).hexdigest())
