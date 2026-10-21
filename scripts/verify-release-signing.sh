#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C
cd "$(dirname "$0")/.."
expected=$(tr -d '[:space:]:' < signing/release-certificate.sha256 | tr '[:upper:]' '[:lower:]')
[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || { echo "Invalid pinned signing fingerprint" >&2; exit 1; }
check_fingerprint() {
  local actual
  actual=$(printf '%s' "$1" | tr -d '[:space:]:' | tr '[:upper:]' '[:lower:]')
  [[ "$actual" == "$expected" ]] || {
    echo "Signing certificate mismatch: refusing to build or publish with a different key." >&2
    echo "Expected: $expected; actual: $actual" >&2
    exit 1
  }
}
case "${1:-}" in
  keystore)
    : "${KEYSTORE_PATH:?Existing keystore required}" "${KEYSTORE_PASSWORD:?Existing password required}" "${KEY_ALIAS:?Existing alias required}"
    test -s "$KEYSTORE_PATH"
    certificate="${RUNNER_TEMP:?}/homa-release-certificate.der"
    keytool -exportcert -alias "$KEY_ALIAS" -keystore "$KEYSTORE_PATH" \
      -storepass:env KEYSTORE_PASSWORD -file "$certificate"
    check_fingerprint "$(sha256sum "$certificate" | cut -d ' ' -f 1)"
    rm -f "$certificate"
    ;;
  artifacts)
    apk="${2:?APK required}"; aab="${3:?AAB required}"
    test -s "$apk"; test -s "$aab"
    apksigner=$(find "${ANDROID_HOME:?}/build-tools" -name apksigner | sort -V | tail -1)
    apk_report=$("$apksigner" verify --verbose --print-certs "$apk")
    printf '%s\n' "$apk_report"
    # Newer Android tools label signers by SDK range rather than signer number.
    apk_fingerprints=$(printf '%s\n' "$apk_report" | awk '/certificate SHA-256 digest:/ { print $NF }' | sort -u)
    check_fingerprint "$apk_fingerprints"
    printf '%s\n' "$apk_report" | grep -q '^Verified using v1 scheme .*: true$'
    printf '%s\n' "$apk_report" | grep -q '^Verified using v2 scheme .*: true$'
    jarsigner -verify "$aab"
    aab_report=$(keytool -J-Duser.language=en -J-Duser.country=US -printcert -jarfile "$aab")
    check_fingerprint "$(printf '%s\n' "$aab_report" | awk '/SHA256:/ { print $2; exit }')"
    if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
      printf '### Signing identity verified\n- APK and AAB match the pinned repository key: `%s`\n- APK signatures: V1 and V2 verified\n' "$expected" >> "$GITHUB_STEP_SUMMARY"
    fi
    ;;
  *) echo "Usage: $0 keystore | artifacts APK AAB" >&2; exit 2 ;;
esac
echo "Pinned release signing identity verified: $expected"
