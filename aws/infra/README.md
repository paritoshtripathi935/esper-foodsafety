# AWS Infra — EC2-hosted AI service

AWS access granted by default: **EC2 + S3 + Bedrock**. No Lambda / API Gateway / SAM needed.

## 1. S3 bucket
```
aws s3 mb s3://haccp-reports-<suffix> --region us-east-1
```

## 2. IAM instance role
Create a role for EC2 with `iam-instance-role-policy.json` attached, then attach it to the instance.
If role attachment is blocked, skip this and put an IAM access key in `aws/ai-service/.env` instead.

## 3. Launch EC2
- AMI: Amazon Linux 2023 · type: t3.small · region: a Bedrock region (e.g. us-east-1)
- Attach the instance role from step 2
- Security group: inbound SSH (your IP) + TCP 8000 (app) from the demo network
- User data: paste `ec2-user-data.sh`

## 4. Verify Bedrock from the box
```
aws sts get-caller-identity
aws bedrock list-foundation-models --region us-east-1 | grep -i claude
```
If no Claude model is listed, request model access in the Bedrock console (usually already on).

## 5. Run the service
`ec2-user-data.sh` clones the repo, installs deps, and runs uvicorn on :8000.
Health check from your laptop: `curl http://<ec2-public-dns>:8000/health`.

## TLS / Android cleartext
- Demo-simple: leave the service on plain http:8000. Add a network-security-config exception for the
  EC2 host in the kiosk app, and serve the dashboard over local http (avoids browser mixed-content).
- Polished: front uvicorn with Caddy (auto-TLS) on a real domain pointing at the instance.

## Env (aws/ai-service/.env)
```
AWS_REGION=us-east-1
BEDROCK_MODEL_ID=anthropic.claude-... (a model id granted in your region)
S3_BUCKET=haccp-reports-<suffix>
SUPABASE_URL=...
SUPABASE_SERVICE_KEY=...   # service-role key, for /ai/ask log queries — keep server-side only
```
