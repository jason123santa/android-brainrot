#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check native-theme/sizing contracts; this is NOT a visual Android UI test."""
import os
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parents = {
        'values': '@android:style/Theme.NoTitleBar',
        'values-v11': '@android:style/Theme.Holo.Light',
        'values-night-v11': '@android:style/Theme.Holo',
        'values-v14': '@android:style/Theme.DeviceDefault.Light.NoActionBar',
        'values-night-v14': '@android:style/Theme.DeviceDefault.NoActionBar',
    }
    for directory, parent in parents.items():
        style = ET.parse(ROOT / 'res' / directory / 'styles.xml').getroot().find('style')
        assert style.get('name') == 'AppTheme' and style.get('parent') == parent
        items = {item.get('name'): item.text for item in style.findall('item')}
        expected = {'android:windowNoTitle': 'true'}
        if directory in ['values-v11', 'values-night-v11']:
            expected['android:windowActionBar'] = 'false'
        assert items == expected, 'Only native window decorations should be overridden'
    activity = (ROOT / 'src/org/brainrot/legacy/ChatActivity.java').read_text(encoding='utf-8')
    for custom_style in ['GradientDrawable', 'StateListDrawable', 'setBackground', 'setTextColor',
                         'setHintTextColor', 'setTextSize', 'setTypeface', 'setMinWidth', 'setMinHeight']:
        assert custom_style not in activity, 'Custom widget styling was reintroduced'
    assert 'send.setPadding(' not in activity and 'input.setPadding(' not in activity
    assert 'TextAppearance_Small' in activity and 'TextAppearance_Medium' in activity
    build = ROOT / 'build/host-ui'
    build.mkdir(parents=True, exist_ok=True)
    subprocess.run([os.environ.get('JAVAC', 'javac'), '-encoding', 'UTF-8', '-source', '1.5',
                    '-target', '1.5', '-d', str(build), str(ROOT / 'src/org/brainrot/legacy/UiLayout.java'),
                    str(ROOT / 'tests/UiLayoutCheck.java')], check=True)
    subprocess.run([os.environ.get('JAVA', 'java'), '-cp', str(build),
                    'org.brainrot.legacy.UiLayoutCheck'], check=True)
    print('PASS: platform-native themes, no custom widget styling, sizing policy')
    print('Real layout, native widget measurements and keyboard behavior still need device testing.')


if __name__ == '__main__':
    main()
