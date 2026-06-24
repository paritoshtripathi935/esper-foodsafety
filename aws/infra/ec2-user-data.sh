#!/bin/bash
# EC2 user-data: install deps and start the AI service on port 8000.
# Runs as root on first boot (Amazon Linux 2023).
# Access: AWS Session Manager (no SSH port needed).
set -euxo pipefail

# ── system deps ───────────────────────────────────────────────────────────────
dnf install -y python3.12 python3.12-pip git

# ── pull service code (adjust repo URL / branch as needed) ───────────────────
APP_DIR=/opt/ai-service
git clone --depth 1 https://github.com/<your-org>/<your-repo>.git /tmp/repo
cp -r /tmp/repo/aws/ai-service "$APP_DIR"

# ── python deps ───────────────────────────────────────────────────────────────
python3.12 -m pip install --no-cache-dir -r "$APP_DIR/requirements.txt"

# ── .env (fill in before launch, or inject via SSM Parameter Store) ──────────
# cp /tmp/.env "$APP_DIR/.env"

# ── systemd service ───────────────────────────────────────────────────────────
cat > /etc/systemd/system/ai-service.service << 'SERVICE'
[Unit]
Description=Esper AI service (FastAPI)
After=network.target

[Service]
WorkingDirectory=/opt/ai-service
ExecStart=/usr/bin/python3.12 -m uvicorn app:app --host 0.0.0.0 --port 8000
Restart=always
EnvironmentFile=-/opt/ai-service/.env

[Install]
WantedBy=multi-user.target
SERVICE

systemctl daemon-reload
systemctl enable --now ai-service
