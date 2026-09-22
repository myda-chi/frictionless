#!/usr/bin/env bash
# Deliverable S4 — prove the model call works from THIS machine before anyone needs it.
# Key lives in the GitLab vault (link in the repo). Run this in hour one, not at 2am.
#
#   OPENAI_API_KEY=sk-... ./scripts/smoke-openai.sh
set -euo pipefail

: "${OPENAI_API_KEY:?set OPENAI_API_KEY first — see the vault link in the GitLab repo}"

echo "calling the API…"
response=$(curl -sS -X POST https://api.openai.com/v1/chat/completions \
  -H "Authorization: Bearer ${OPENAI_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"reply with the single word: frictionless"}],"max_tokens":5}')

if echo "$response" | grep -qi "frictionless"; then
  echo "OK — the key works from this machine."
else
  echo "FAILED. Raw response:" >&2
  echo "$response" >&2
  exit 1
fi
