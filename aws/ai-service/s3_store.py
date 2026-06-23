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
