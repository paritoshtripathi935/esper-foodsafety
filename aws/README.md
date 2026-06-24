# aws/ — AI layer (EC2 + Bedrock + S3)

The AI layer runs as a FastAPI service on **EC2** (the account grants EC2 + S3 + Bedrock by default;
Lambda / API Gateway are not used). It exposes `/ai/structure-note`, `/ai/narrate`, `/ai/ask`, `/pdf`.

- `ai-service/` — the FastAPI app (`app.py`), Bedrock wrapper (`bedrock.py`), S3 store (`s3_store.py`),
  `requirements.txt`, `Dockerfile`, `.env.example`.
- `infra/` — `iam-instance-role-policy.json`, `ec2-user-data.sh`.

---

## AI-1: Launch EC2 (manual — AWS console)

Access method: **AWS Session Manager** (no SSH port or key pair needed).

### IAM instance role
Create a role with:
1. `iam-instance-role-policy.json` — inline policy (Bedrock + S3 + SSM permissions)
2. **OR** attach the AWS-managed policy `AmazonSSMManagedInstanceCore` for Session Manager +
   add the Bedrock/S3 inline statements separately.

### Launch
- AMI: Amazon Linux 2023
- Instance type: `t3.small`
- Region: `us-east-1` (Bedrock Claude models available here)
- Attach the IAM role above
- Security group: port **8000** open to demo network (or `0.0.0.0/0` for hackathon)
- **No SSH key pair needed** — Session Manager handles access
- Paste `ec2-user-data.sh` into the User data field (update the `git clone` URL first)

### Create S3 bucket
Bucket name must start with your team prefix. Tags are required at create time.
```
aws s3api create-bucket --bucket <team>-safetemp-haccp --region us-east-1
aws s3api put-bucket-tagging --bucket <team>-safetemp-haccp \
  --tagging 'TagSet=[{Key=Team,Value=<team>},{Key=UseFor,Value=Hackathon-2026-June}]'
```

### Connect via Session Manager
```bash
aws ssm start-session --target <instance-id>
# or use the AWS console: EC2 → Connect → Session Manager
```

---

## Deploy / configure the service

Once connected via Session Manager:

```bash
# If user-data ran correctly, the service is already running. Check:
sudo systemctl status ai-service

# Set env vars (edit /opt/ai-service/.env):
sudo tee /opt/ai-service/.env << 'EOF'
AWS_REGION=us-east-1
# Bedrock cross-region inference profile (the `us.` prefix is required).
BEDROCK_MODEL_ID=us.anthropic.claude-sonnet-4-6
# Bucket name must start with your team prefix.
S3_BUCKET=<team>-safetemp-haccp
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_SERVICE_KEY=<service-role-key>
EOF

sudo systemctl restart ai-service
sudo journalctl -u ai-service -f   # watch logs

# Verify from inside the box:
curl http://localhost:8000/health
```

### Then update repo env files with the EC2 public DNS:
```
# android/local.properties
ai.api.base=http://<EC2_PUBLIC_IP>:8000

# frontend/.env
VITE_AI_API_BASE=http://<EC2_PUBLIC_IP>:8000
```

### Run the severity test harness:
```bash
AI_API_BASE=http://<EC2_PUBLIC_IP>:8000 python3 aws/ai-service/test_samples.py
```

---

## Build order

`plan/01-day1.md` (AI-1 launch box, AI-2 service skeleton, AI-3 stub) →
`plan/02-day2.md` (AI-4/AI-5 real Bedrock structuring) → `plan/03-day3.md` (AI-6 narrate, AI-7 PDF→S3, AI-8 ask).
