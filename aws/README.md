# aws/ — AI layer (EC2 + Bedrock + S3)

The AI layer runs as a FastAPI service on **EC2** (the account grants EC2 + S3 + Bedrock by default;
Lambda / API Gateway are not used). It exposes `/ai/structure-note`, `/ai/narrate`, `/ai/ask`, `/pdf`.

- `ai-service/` — the FastAPI app (`app.py`), Bedrock wrapper (`bedrock.py`), S3 store (`s3_store.py`),
  `requirements.txt`, `Dockerfile`, `.env.example`.
- `infra/` — `iam-instance-role-policy.json`, `ec2-user-data.sh`, and setup steps in `README.md`.

Build order: `plan/01-day1.md` (AI-1 launch box, AI-2 service skeleton, AI-3 stub) →
`plan/02-day2.md` (AI-4/AI-5 real Bedrock structuring) → `plan/03-day3.md` (AI-6 narrate, AI-7 PDF→S3, AI-8 ask).
