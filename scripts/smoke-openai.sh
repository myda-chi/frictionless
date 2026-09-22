#!/usr/bin/env bash
# Deliverable S4 — prove the model call works from THIS machine before anyone needs it.
#
#   ./scripts/smoke-openai.sh
#
# Reads the key the same way the plugin does: OPENAI_API_KEY, else ~/.frictionless/openai-key.
set -euo pipefail

key="${OPENAI_API_KEY:-}"
if [ -z "$key" ] && [ -f "$HOME/.frictionless/openai-key" ]; then
  key="$(cat "$HOME/.frictionless/openai-key")"
fi
: "${key:?no key found — set OPENAI_API_KEY or write ~/.frictionless/openai-key}"

echo "calling the API…"
response=$(curl -sS -X POST https://api.openai.com/v1/chat/completions \
  -H "Authorization: Bearer ${key}" \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"Say hello."}],"max_tokens":16}')

# Assert the transport, not the model's obedience. The first version of this script demanded the
# model echo one specific word; it replied with a synonym and the script reported FAILED on a
# perfectly working key. What S4 needs to know is that the request is authenticated and a completion
# comes back — anything about *what* the model says is the agent's problem, and the agent checks it
# by running the test rather than by trusting the text.
python3 - "$response" <<'PY'
import json, sys
try:
    data = json.loads(sys.argv[1])
except json.JSONDecodeError:
    sys.exit(f"FAILED: response was not JSON:\n{sys.argv[1][:500]}")

if "error" in data:
    sys.exit(f"FAILED: {data['error'].get('message', data['error'])}")

content = (data.get("choices") or [{}])[0].get("message", {}).get("content")
if not content:
    sys.exit(f"FAILED: no completion in the response:\n{json.dumps(data)[:500]}")

print(f"OK — the key works from this machine (model replied: {content.strip()!r})")
PY
