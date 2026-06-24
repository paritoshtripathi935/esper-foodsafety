#!/usr/bin/env bash
#
# Deploy the SafeTemp dashboard into the FastAPI AI service on EC2.
#
# Builds the Vite app and copies the output into aws/ai-service/static/, which
# the FastAPI app serves same-origin on port 8000 (see app.py static block).
#
# Run this ON the EC2 instance (via SSM) from anywhere in the repo:
#     ./frontend/deploy.sh
#
# Prereqs (one-time on EC2):
#   - node + npm installed   (curl -fsSL https://rpm.nodesource.com/setup_20.x | sudo bash - && sudo dnf install -y nodejs)
#   - frontend/.env present   (VITE_SUPABASE_URL, VITE_SUPABASE_ANON_KEY; VITE_AI_API_BASE left blank for same-origin)
#   - systemd unit 'safetemp-ai' running the FastAPI service
#
set -euo pipefail

# Resolve repo paths relative to this script, so it works from any CWD.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
STATIC_DIR="$REPO_ROOT/aws/ai-service/static"
SERVICE="${SAFETEMP_SERVICE:-safetemp-ai}"

echo "→ Repo:     $REPO_ROOT"
echo "→ Frontend: $FRONTEND_DIR"
echo "→ Static:   $STATIC_DIR"

# 1. Pull latest code (skip with --no-pull if you've already pulled / have local changes)
if [[ "${1:-}" != "--no-pull" ]]; then
  echo "→ Pulling latest…"
  git -C "$REPO_ROOT" pull --ff-only
fi

# 2. Guard: .env must exist or the build ships without Supabase config
if [[ ! -f "$FRONTEND_DIR/.env" ]]; then
  echo "✗ $FRONTEND_DIR/.env is missing. Create it with VITE_SUPABASE_URL and"
  echo "  VITE_SUPABASE_ANON_KEY (leave VITE_AI_API_BASE blank for same-origin)."
  exit 1
fi

# 3. Install deps (npm ci when lockfile matches, else npm install)
echo "→ Installing deps…"
cd "$FRONTEND_DIR"
npm ci || npm install

# 4. Build — VITE_AI_API_BASE blank ⇒ relative API calls ⇒ same-origin
echo "→ Building…"
VITE_AI_API_BASE='' npm run build

# 5. Swap the build into the FastAPI static dir
echo "→ Publishing to $STATIC_DIR…"
rm -rf "$STATIC_DIR"
cp -r "$FRONTEND_DIR/dist" "$STATIC_DIR"

# 6. Sanity-check the bundle baked in the Supabase config
if grep -rq "supabase.co" "$STATIC_DIR/assets" 2>/dev/null; then
  echo "  ✓ Supabase URL present in bundle"
else
  echo "  ⚠ Supabase URL NOT found in bundle — check frontend/.env"
fi

# 7. Restart the service
echo "→ Restarting $SERVICE…"
sudo systemctl restart "$SERVICE"
sleep 2

# 8. Verify
CODE="$(curl -sS -o /dev/null -w '%{http_code}' http://localhost:8000/ || true)"
echo "→ GET / → HTTP $CODE"
if [[ "$CODE" == "200" ]]; then
  echo "✓ Deployed. Dashboard live at http://<public-ip>:8000/"
else
  echo "✗ Service did not return 200. Check: sudo journalctl -u $SERVICE -n 30 --no-pager"
  exit 1
fi
