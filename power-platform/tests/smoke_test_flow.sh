#!/usr/bin/env bash
# End-to-end smoke test for the deployed "SCA Android HTTP Entry" flow.
#
# Prerequisites (macOS):  brew install azure-cli jq
#   az login --tenant "$SCA_TENANT_ID"      # sign in as a user allowed to trigger the flow
#   export SCA_FLOW_URL='https://...'       # OAuth-protected trigger URL (must NOT contain sig=)
#   export SCA_TEST_IMAGE=./sample.jpg      # a JPEG screenshot <= 2576 px on the long edge
#   export SCA_TEST_PACKAGE=com.schoolproject.adtestbed
#
# The script never prints the bearer token, the image, or request bodies.
set -euo pipefail

fail() { echo "SMOKE-FAIL [$1] $2" >&2; exit 1; }

command -v az >/dev/null || fail SMOKE-DEP "az not installed"
command -v jq >/dev/null || fail SMOKE-DEP "jq not installed"
command -v python3 >/dev/null || fail SMOKE-DEP "python3 not installed"
: "${SCA_FLOW_URL:?set SCA_FLOW_URL}"
: "${SCA_TEST_IMAGE:?set SCA_TEST_IMAGE}"
: "${SCA_TEST_PACKAGE:=com.schoolproject.adtestbed}"
[[ "$SCA_FLOW_URL" == https://* ]] || fail SMOKE-CONFIG "SCA_FLOW_URL must be https"
[[ "$SCA_FLOW_URL" != *"sig="* ]] || fail SMOKE-CONFIG "SCA_FLOW_URL contains a SAS signature; switch the trigger to Entra authentication"
[[ -f "$SCA_TEST_IMAGE" ]] || fail SMOKE-CONFIG "image not found: $SCA_TEST_IMAGE"

TOKEN="$(az account get-access-token --resource 'https://service.flow.microsoft.com/' --query accessToken -o tsv)"
[[ -n "$TOKEN" ]] || fail SMOKE-AUTH "could not obtain a token"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

read -r W H < <(python3 - "$SCA_TEST_IMAGE" <<'PY'
import struct, sys
data = open(sys.argv[1], "rb").read()
if data[:2] != b"\xff\xd8":
    sys.exit("only JPEG supported by this smoke test")
i = 2
while i < len(data):
    if data[i] != 0xFF:
        i += 1
        continue
    marker = data[i + 1]
    if marker in (0xC0, 0xC1, 0xC2):
        h, w = struct.unpack(">HH", data[i + 5:i + 9])
        print(w, h)
        break
    seg_len = struct.unpack(">H", data[i + 2:i + 4])[0]
    i += 2 + seg_len
PY
)

post() { # $1 body file, $2 output file -> prints HTTP status
  curl -sS -o "$2" -w '%{http_code}' --max-time 115 \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -H "x-ms-client-request-id: $3" \
    --data-binary "@$1" "$SCA_FLOW_URL"
}

make_body() { # $1 requestId $2 sessionId $3 hash $4 action $5 out
  python3 - "$SCA_TEST_IMAGE" "$1" "$2" "$3" "$4" "$W" "$H" "$SCA_TEST_PACKAGE" > "$5" <<'PY'
import base64, json, sys
img, rid, sid, h, action, w, hh, pkg = sys.argv[1:9]
print(json.dumps({
    "requestId": rid, "sessionId": sid,
    "screenshotBase64": base64.b64encode(open(img, "rb").read()).decode(),
    "mimeType": "image/jpeg", "screenshotWidth": int(w), "screenshotHeight": int(hh),
    "screenshotHash": h, "foregroundPackage": pkg, "requestedAction": action}))
PY
}

SESSION="$(python3 -c 'import uuid;print(uuid.uuid4())')"
HASH="$(python3 -c 'import secrets;print(secrets.token_hex(8))')"

# 1) happy path: valid request returns a schema-conformant document
R1="$(python3 -c 'import uuid;print(uuid.uuid4())')"
make_body "$R1" "$SESSION" "$HASH" FIND_AD_CLOSE "$WORK/b1.json"
S1="$(post "$WORK/b1.json" "$WORK/r1.json" "$R1")"
[[ "$S1" == 200 ]] || fail SMOKE-1 "expected 200, got $S1: $(jq -c '{action,warnings}' "$WORK/r1.json" 2>/dev/null)"
jq -e --arg r "$R1" --arg h "$HASH" '
  .requestId == $r and .screenshotHash == $h and
  (.action | IN("CLOSE","WAIT","NO_ACTION","UNSAFE","ERROR")) and
  (.confidence >= 0 and .confidence <= 1) and
  ((.action == "CLOSE" and .target != null and .confidence >= 0.9) or (.action != "CLOSE" and .target == null)) and
  (.warnings | type == "array")' "$WORK/r1.json" >/dev/null || fail SMOKE-1 "response failed contract checks"
echo "SMOKE-1 ok: action=$(jq -r .action "$WORK/r1.json")"

# 2) idempotent replay: same requestId returns the stored document
S2="$(post "$WORK/b1.json" "$WORK/r2.json" "$R1")"
[[ "$S2" == 200 ]] || fail SMOKE-2 "expected 200 on replay, got $S2"
diff <(jq -S . "$WORK/r1.json") <(jq -S . "$WORK/r2.json") >/dev/null || fail SMOKE-2 "replay differs from original"
echo "SMOKE-2 ok: idempotent replay"

# 3) duplicate hash in same session with a new requestId is rejected
R3="$(python3 -c 'import uuid;print(uuid.uuid4())')"
make_body "$R3" "$SESSION" "$HASH" FIND_AD_CLOSE "$WORK/b3.json"
S3="$(post "$WORK/b3.json" "$WORK/r3.json" "$R3")"
[[ "$S3" == 409 ]] || fail SMOKE-3 "expected 409, got $S3"
jq -e '.action == "ERROR" and (.warnings | index("DUPLICATE_SCREENSHOT"))' "$WORK/r3.json" >/dev/null || fail SMOKE-3 "missing DUPLICATE_SCREENSHOT"
echo "SMOKE-3 ok: duplicate hash rejected"

# 4) unsupported requestedAction is rejected without calling the model
R4="$(python3 -c 'import uuid;print(uuid.uuid4())')"
make_body "$R4" "$SESSION" "$(python3 -c 'import secrets;print(secrets.token_hex(8))')" CLICK_AD "$WORK/b4.json"
S4="$(post "$WORK/b4.json" "$WORK/r4.json" "$R4")"
[[ "$S4" == 400 ]] || fail SMOKE-4 "expected 400, got $S4"
jq -e '.action == "ERROR" and (.warnings | index("UNSUPPORTED_ACTION"))' "$WORK/r4.json" >/dev/null || fail SMOKE-4 "missing UNSUPPORTED_ACTION"
echo "SMOKE-4 ok: unsupported action rejected"

# 5) unauthenticated call is refused by the platform
S5="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 30 -H 'Content-Type: application/json' --data-binary "@$WORK/b4.json" "$SCA_FLOW_URL")"
[[ "$S5" == 401 || "$S5" == 403 ]] || fail SMOKE-5 "expected 401/403 without token, got $S5"
echo "SMOKE-5 ok: anonymous call refused ($S5)"

echo "ALL SMOKE TESTS PASSED"
