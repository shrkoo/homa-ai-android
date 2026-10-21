#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C
cd "$(dirname "$0")/.."
expected=$(tr -d '[:space:]:' < signing/release-certificate.sha256 | tr '[:upper:]' '[:lower:]')
[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || { echo "Invalid signing fingerprint" >&2; exit 1; }
check_fingerprint() {
  local actual; actual=$(printf '%s' "$1" | tr -d '[:space:]:' | tr '[:upper:]' '[:lower:]')
  if [[ "$actual" != "$expected" ]]; then
    echo "Signing certificate mismatch; publication refused. Expected: $expected; actual: $actual" >&2
    exit 1
  fi
}
report_fingerprint() { awk '/certificate SHA-256 digest:/ { print $NF }' | sort -u; }
case "${1:-}" in
  keystore)
    : "${KEYSTORE_PATH:?Keystore required}" "${KEYSTORE_PASSWORD:?Password required}" "${KEY_ALIAS:?Alias required}"
    test -s "$KEYSTORE_PATH"
    certificate="${RUNNER_TEMP:?}/homa-release-certificate.der"
    keytool -exportcert -alias "$KEY_ALIAS" -keystore "$KEYSTORE_PATH" -storepass:env KEYSTORE_PASSWORD -file "$certificate"
    check_fingerprint "$(sha256sum "$certificate" | cut -d ' ' -f 1)"
    rm -f "$certificate"
    ;;
  artifacts)
    apk="${2:?APK required}"; aab="${3:?AAB required}"
    test -s "$apk"; test -s "$aab"
    apksigner=$(find "${ANDROID_HOME:?}/build-tools" -name apksigner | sort -V | tail -1)
    v2_report=$("$apksigner" verify --verbose --print-certs "$apk")
    v1_report=$("$apksigner" verify --min-sdk-version 23 --max-sdk-version 23 --verbose --print-certs "$apk")
    printf '%s\n%s\n' "$v2_report" "$v1_report"
    check_fingerprint "$(printf '%s\n' "$v2_report" | report_fingerprint)"
    check_fingerprint "$(printf '%s\n' "$v1_report" | report_fingerprint)"
    printf '%s\n' "$v2_report" | grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true'
    printf '%s\n' "$v1_report" | grep -F 'Verified using v1 scheme (JAR signing): true'
    jarsigner -verify "$aab"
    aab_report=$(keytool -J-Duser.language=en -J-Duser.country=US -printcert -jarfile "$aab")
    check_fingerprint "$(printf '%s\n' "$aab_report" | awk '/SHA256:/ { print $2; exit }')"
    if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
      printf '### Release signing verified\n- APK V1, APK V2 and AAB match the existing repository key: `%s`\n' "$expected" >> "$GITHUB_STEP_SUMMARY"
    fi
    ;;
  *) echo "Usage: $0 keystore | artifacts APK AAB" >&2; exit 2 ;;
esac
echo "Pinned release signing identity verified: $expected"
