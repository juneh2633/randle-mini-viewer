#!/usr/bin/env bash
# Keep the test exit code while collecting screenshots for failed runs too.
status=0
gradle -p android --no-daemon :app:connectedDebugAndroidTest || status=$?
adb pull /data/local/tmp/randle-pip-smoke.png android/app/build/reports/pip-smoke.png || true
adb pull /data/local/tmp/randle-floating-smoke.png android/app/build/reports/floating-smoke.png || true
adb pull /data/local/tmp/randle-floating-input.png android/app/build/reports/floating-input.png || true
exit "$status"
