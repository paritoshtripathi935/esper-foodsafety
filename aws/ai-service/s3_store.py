import os
import boto3

_client = None


def _get_client():
    global _client
    if _client is None:
        _client = boto3.client(
            "s3",
            region_name=os.environ.get("AWS_REGION", "us-east-1"),
        )
    return _client


def list_objects(prefix: str = "haccp/", limit: int = 100) -> list[dict]:
    """List PDFs under prefix. Returns newest first.
    Each item: {key, size, last_modified (ISO), site_id, date}"""
    bucket = os.environ["S3_BUCKET"]
    resp = _get_client().list_objects_v2(
        Bucket=bucket, Prefix=prefix, MaxKeys=limit,
    )
    items = []
    for o in resp.get("Contents", []):
        key = o["Key"]
        # haccp/<site_id>/<date>.pdf
        parts = key.split("/")
        site_id = parts[1] if len(parts) > 1 else ""
        date = parts[2].replace(".pdf", "") if len(parts) > 2 else ""
        items.append({
            "key": key,
            "size": o["Size"],
            "last_modified": o["LastModified"].isoformat(),
            "site_id": site_id,
            "date": date,
        })
    items.sort(key=lambda x: x["last_modified"], reverse=True)
    return items


def presign_get(key: str, expires_seconds: int = 600) -> str:
    """Return a time-limited signed URL for downloading the object."""
    bucket = os.environ["S3_BUCKET"]
    return _get_client().generate_presigned_url(
        "get_object",
        Params={"Bucket": bucket, "Key": key},
        ExpiresIn=expires_seconds,
    )


def put_object(key: str, data: bytes, content_type: str = "application/pdf") -> dict:
    """Upload bytes to S3 and return {"bucket": ..., "key": ...}."""
    bucket = os.environ["S3_BUCKET"]
    _get_client().put_object(
        Bucket=bucket,
        Key=key,
        Body=data,
        ContentType=content_type,
    )
    return {"bucket": bucket, "key": key}
