import json
import os
import boto3

_client = None


def _get_client():
    global _client
    if _client is None:
        _client = boto3.client(
            "bedrock-runtime",
            region_name=os.environ.get("AWS_REGION", "us-east-1"),
        )
    return _client


def invoke(prompt: str, system: str = "", max_tokens: int = 1024) -> str:
    """Send a prompt to the configured Claude model and return the text response."""
    model_id = os.environ["BEDROCK_MODEL_ID"]
    client = _get_client()

    body = {
        "anthropic_version": "bedrock-2023-05-31",
        "max_tokens": max_tokens,
        "messages": [{"role": "user", "content": prompt}],
    }
    if system:
        body["system"] = system

    response = client.invoke_model(
        modelId=model_id,
        contentType="application/json",
        accept="application/json",
        body=json.dumps(body),
    )
    result = json.loads(response["body"].read())
    return result["content"][0]["text"]


def invoke_with_tool(prompt: str, system: str, tool: dict, max_tokens: int = 1024) -> dict:
    """Invoke Claude with a single tool and return the tool-use result dict."""
    model_id = os.environ["BEDROCK_MODEL_ID"]
    client = _get_client()

    body = {
        "anthropic_version": "bedrock-2023-05-31",
        "max_tokens": max_tokens,
        "system": system,
        "tools": [tool],
        "tool_choice": {"type": "tool", "name": tool["name"]},
        "messages": [{"role": "user", "content": prompt}],
    }

    response = client.invoke_model(
        modelId=model_id,
        contentType="application/json",
        accept="application/json",
        body=json.dumps(body),
    )
    result = json.loads(response["body"].read())
    for block in result.get("content", []):
        if block.get("type") == "tool_use":
            return block["input"]
    raise ValueError("No tool_use block in Bedrock response")
