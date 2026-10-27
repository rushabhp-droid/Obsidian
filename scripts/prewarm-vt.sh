#!/usr/bin/env bash
# Pre-warms Redis vt:cache:* (docs/protocol.md) so the demo never waits on
# VirusTotal's free tier (4 requests/min). Pass URLs and SHA-256 hashes exactly
# as they will be scanned, or put one per line in scripts/demo-indicators.txt.
#   VT_API_KEY=... scripts/prewarm-vt.sh http://testsafebrowsing.appspot.com/s/phishing.html
# An unknown URL is submitted; run the script again a minute later to cache it.
# Check that every demo URL shows 3 or more detections.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${VT_API_KEY:?set VT_API_KEY (see .env.example)}"
[ -f .env ] && set -a && . ./.env && set +a

indicators=("$@")
if [ ${#indicators[@]} -eq 0 ] && [ -f scripts/demo-indicators.txt ]; then
  mapfile -t indicators < <(grep -v '^\s*\(#\|$\)' scripts/demo-indicators.txt)
fi
[ ${#indicators[@]} -gt 0 ] || { echo "usage: $0 URL|SHA256 ..." >&2; exit 2; }

for value in "${indicators[@]}"; do
  if [[ "$value" =~ ^[A-Fa-f0-9]{64}$ ]]; then
    id=$(printf '%s' "$value" | tr 'A-F' 'a-f'); path="files/$id"; category=MALWARE
  else
    id=$(printf '%s' "$value" | base64 -w0 | tr '+/' '-_' | tr -d '='); path="urls/$id"; category=PHISHING
  fi
  status=$(curl -s -o /tmp/vt-$$.json -w '%{http_code}' -H "x-apikey: $VT_API_KEY" "https://www.virustotal.com/api/v3/$path")
  if [ "$status" = 200 ]; then
    json=$(python3 -I -c '
import json, sys, datetime
stats = json.load(open(sys.argv[1]))["data"]["attributes"]["last_analysis_stats"]
m, s = stats.get("malicious", 0), stats.get("suspicious", 0)
verdict = "MALICIOUS" if m >= 3 else "SUSPICIOUS" if (m or s) else "CLEAN"
print(json.dumps({"verdict": verdict, "malicious": m, "suspicious": s,
                  "category": None if verdict == "CLEAN" else sys.argv[2],
                  "checkedAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")}))
' /tmp/vt-$$.json "$category")
    docker compose exec -T redis redis-cli SET "vt:cache:$id" "$json" EX 86400 >/dev/null
    echo "cached  $value  $json"
  elif [ "$status" = 404 ] && [ "$category" = PHISHING ]; then
    curl -s -o /dev/null -H "x-apikey: $VT_API_KEY" --data-urlencode "url=$value" https://www.virustotal.com/api/v3/urls
    echo "submitted $value (run again in a minute)"
  else
    echo "HTTP $status for $value" >&2
  fi
  sleep 16 # stay under 4 requests a minute
done
rm -f /tmp/vt-$$.json
