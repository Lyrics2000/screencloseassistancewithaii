#!/usr/bin/env python3
"""
Migration 001 - Screen Close Assistant (SCA) Dataverse data layer.

Creates (idempotently) inside the ScreenCloseAssistant solution:
  * publisher  "ScreenCloseAssistantPublisher" (prefix sca, option-value prefix 72700)
  * solution   "ScreenCloseAssistant"
  * table      sca_analysisrequest  (one row per analysis request, NEVER the image)
  * alt key    sca_key_requestid          (sca_requestid)             -> idempotency
  * alt key    sca_key_session_hash       (sca_sessionid, sca_screenshothash) -> duplicate-hash rejection

ADR-002 (see docs/adr/ADR-002-dataverse-for-idempotency.md): Dataverse alternate keys are
enforced by a unique SQL index, so two concurrent flow runs inserting the same
(sessionId, screenshotHash) cannot both succeed. That is the race-safe primitive the flow
relies on; a "list rows then create" check alone would be a TOCTOU race.

Usage (macOS / Linux):
  az login --tenant <TENANT_ID>
  export SCA_DATAVERSE_URL="https://<org>.crm.dynamics.com"
  python3 001_create_analysis_request.py            # migrate up
  python3 001_create_analysis_request.py --status   # show what exists
  python3 001_create_analysis_request.py --down     # delete the table (destroys audit rows)

Requires: Python 3.9+, Azure CLI 2.50+ (only to obtain an access token). Standard library only.
The signed-in user needs the System Customizer or System Administrator role in the environment.
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import random
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from typing import Any, Optional

LOG = logging.getLogger("sca.migration.001")

API_PATH = "/api/data/v9.2"
LANG = 1033
PUBLISHER_UNIQUE_NAME = "ScreenCloseAssistantPublisher"
PUBLISHER_PREFIX = "sca"
PUBLISHER_OPTION_PREFIX = 72700
SOLUTION_UNIQUE_NAME = "ScreenCloseAssistant"
TABLE_SCHEMA = "sca_AnalysisRequest"
TABLE_LOGICAL = "sca_analysisrequest"
MAX_ATTEMPTS = 6


class MigrationError(Exception):
    """Error with a stable code so failures can be grepped in CI logs."""

    def __init__(self, code: str, message: str, cause: Optional[BaseException] = None):
        super().__init__(f"[{code}] {message}")
        self.code = code
        self.__cause__ = cause


def label(text: str) -> dict[str, Any]:
    return {
        "@odata.type": "Microsoft.Dynamics.CRM.Label",
        "LocalizedLabels": [
            {"@odata.type": "Microsoft.Dynamics.CRM.LocalizedLabel", "Label": text, "LanguageCode": LANG}
        ],
    }


def required_level(value: str) -> dict[str, Any]:
    return {"Value": value, "CanBeChanged": True, "ManagedPropertyLogicalName": "canmodifyrequirementlevelsettings"}


def string_attr(schema: str, display: str, max_len: int, required: bool = False, desc: str = "") -> dict[str, Any]:
    return {
        "@odata.type": "Microsoft.Dynamics.CRM.StringAttributeMetadata",
        "SchemaName": schema,
        "DisplayName": label(display),
        "Description": label(desc or display),
        "RequiredLevel": required_level("ApplicationRequired" if required else "None"),
        "MaxLength": max_len,
        "FormatName": {"Value": "Text"},
    }


def memo_attr(schema: str, display: str, max_len: int, desc: str = "") -> dict[str, Any]:
    return {
        "@odata.type": "Microsoft.Dynamics.CRM.MemoAttributeMetadata",
        "SchemaName": schema,
        "DisplayName": label(display),
        "Description": label(desc or display),
        "RequiredLevel": required_level("None"),
        "MaxLength": max_len,
        "Format": "TextArea",
    }


def int_attr(schema: str, display: str, desc: str = "") -> dict[str, Any]:
    return {
        "@odata.type": "Microsoft.Dynamics.CRM.IntegerAttributeMetadata",
        "SchemaName": schema,
        "DisplayName": label(display),
        "Description": label(desc or display),
        "RequiredLevel": required_level("None"),
        "Format": "None",
        "MinValue": 0,
        "MaxValue": 2147483647,
    }


def decimal_attr(schema: str, display: str, desc: str = "") -> dict[str, Any]:
    return {
        "@odata.type": "Microsoft.Dynamics.CRM.DecimalAttributeMetadata",
        "SchemaName": schema,
        "DisplayName": label(display),
        "Description": label(desc or display),
        "RequiredLevel": required_level("None"),
        "MinValue": 0,
        "MaxValue": 1,
        "Precision": 4,
    }


# Columns added after table creation. The primary name column (sca_name) is created with the table.
COLUMNS: list[dict[str, Any]] = [
    string_attr("sca_RequestId", "Request Id", 64, True, "Client-generated request id (idempotency key)"),
    string_attr("sca_SessionId", "Session Id", 64, True, "Client-generated user-initiated session id"),
    string_attr("sca_ScreenshotHash", "Screenshot Hash", 32, True, "64-bit dHash, 16 lowercase hex chars"),
    string_attr("sca_ForegroundPackage", "Foreground Package", 255, False),
    string_attr("sca_Channel", "Channel", 20, False, "ANDROID_HTTP or COPILOT_TOPIC"),
    string_attr("sca_Status", "Status", 20, True, "PROCESSING, COMPLETED or FAILED"),
    string_attr("sca_Action", "Action", 20, False, "CLOSE, WAIT, NO_ACTION, UNSAFE or ERROR"),
    string_attr("sca_ErrorCode", "Error Code", 64, False),
    string_attr("sca_FlowRunId", "Flow Run Id", 200, False, "workflow()['run']['name'] for trace correlation"),
    string_attr("sca_ModelId", "Model Id", 64, False),
    string_attr("sca_StopReason", "Stop Reason", 40, False),
    decimal_attr("sca_Confidence", "Confidence"),
    int_attr("sca_LatencyMs", "Latency (ms)", "End-to-end core flow latency"),
    int_attr("sca_ModelLatencyMs", "Model Latency (ms)", "Claude call latency"),
    int_attr("sca_InputTokens", "Input Tokens"),
    int_attr("sca_OutputTokens", "Output Tokens"),
    memo_attr("sca_ResponseJson", "Response JSON", 8000, "Final JSON returned to the caller (never contains the image)"),
]

KEYS: list[dict[str, Any]] = [
    {"SchemaName": "sca_key_requestid", "DisplayName": "Request Id key", "KeyAttributes": ["sca_requestid"]},
    {
        "SchemaName": "sca_key_session_hash",
        "DisplayName": "Session + screenshot hash key",
        "KeyAttributes": ["sca_sessionid", "sca_screenshothash"],
    },
]


@dataclass
class Response:
    status: int
    headers: dict[str, str]
    body: Any


class DataverseClient:
    def __init__(self, base_url: str, token: str):
        self.base = base_url.rstrip("/") + API_PATH
        self.token = token

    def request(self, method: str, path: str, body: Optional[dict[str, Any]] = None,
                solution: Optional[str] = None, ok: tuple[int, ...] = (200, 201, 204)) -> Response:
        url = self.base + path
        data = json.dumps(body).encode("utf-8") if body is not None else None
        headers = {
            "Authorization": f"Bearer {self.token}",
            "Accept": "application/json",
            "OData-MaxVersion": "4.0",
            "OData-Version": "4.0",
            "Content-Type": "application/json; charset=utf-8",
            "x-ms-client-request-id": str(uuid.uuid4()),
        }
        if solution:
            headers["MSCRM.SolutionUniqueName"] = solution
        for attempt in range(1, MAX_ATTEMPTS + 1):
            req = urllib.request.Request(url, data=data, method=method, headers=headers)
            try:
                with urllib.request.urlopen(req, timeout=120) as resp:
                    raw = resp.read()
                    parsed = json.loads(raw) if raw else None
                    return Response(resp.status, dict(resp.headers), parsed)
            except urllib.error.HTTPError as err:
                raw = err.read()
                try:
                    parsed = json.loads(raw) if raw else None
                except json.JSONDecodeError:
                    parsed = raw.decode("utf-8", "replace")
                if err.code in ok:
                    return Response(err.code, dict(err.headers), parsed)
                if err.code in (429, 502, 503, 504) and attempt < MAX_ATTEMPTS:
                    retry_after = err.headers.get("Retry-After")
                    delay = float(retry_after) if retry_after else min(30.0, (2 ** attempt) + random.uniform(0, 1))
                    LOG.warning("transient HTTP %s on %s %s; retry %d in %.1fs", err.code, method, path, attempt, delay)
                    time.sleep(delay)
                    continue
                return Response(err.code, dict(err.headers), parsed)
            except urllib.error.URLError as err:
                if attempt < MAX_ATTEMPTS:
                    delay = min(30.0, (2 ** attempt) + random.uniform(0, 1))
                    LOG.warning("network error %s; retry %d in %.1fs", err.reason, attempt, delay)
                    time.sleep(delay)
                    continue
                raise MigrationError("SCA-MIG-NET", f"network failure calling {method} {path}", err)
        raise MigrationError("SCA-MIG-RETRY", f"exhausted retries for {method} {path}")


def get_token(resource: str) -> str:
    try:
        out = subprocess.run(
            ["az", "account", "get-access-token", "--resource", resource, "--query", "accessToken", "-o", "tsv"],
            check=True, capture_output=True, text=True, timeout=60,
        )
    except FileNotFoundError as err:
        raise MigrationError("SCA-MIG-AZ-MISSING", "Azure CLI 'az' not found; install it (brew install azure-cli)", err)
    except subprocess.CalledProcessError as err:
        raise MigrationError("SCA-MIG-AZ-TOKEN", f"az could not get a token: {err.stderr.strip()}", err)
    token = out.stdout.strip()
    if not token:
        raise MigrationError("SCA-MIG-AZ-TOKEN", "empty access token")
    return token


def expect(resp: Response, code: str, what: str) -> Response:
    if resp.status >= 300:
        raise MigrationError(code, f"{what} failed: HTTP {resp.status} {json.dumps(resp.body)[:800]}")
    return resp


def ensure_publisher(c: DataverseClient) -> str:
    q = "/publishers?$select=publisherid,customizationprefix&$filter=uniquename%20eq%20%27" + PUBLISHER_UNIQUE_NAME + "%27"
    found = expect(c.request("GET", q), "SCA-MIG-PUB-READ", "read publisher").body["value"]
    if found:
        if found[0]["customizationprefix"] != PUBLISHER_PREFIX:
            raise MigrationError("SCA-MIG-PUB-PREFIX", "existing publisher has a different prefix")
        LOG.info("publisher exists: %s", found[0]["publisherid"])
        return found[0]["publisherid"]
    body = {
        "uniquename": PUBLISHER_UNIQUE_NAME,
        "friendlyname": "Screen Close Assistant",
        "customizationprefix": PUBLISHER_PREFIX,
        "customizationoptionvalueprefix": PUBLISHER_OPTION_PREFIX,
        "description": "Publisher for the Screen Close Assistant school project",
    }
    resp = expect(c.request("POST", "/publishers", body), "SCA-MIG-PUB-CREATE", "create publisher")
    pid = resp.headers.get("OData-EntityId", "").split("(")[-1].rstrip(")")
    LOG.info("publisher created: %s", pid)
    return pid


def ensure_solution(c: DataverseClient, publisher_id: str) -> None:
    q = "/solutions?$select=solutionid&$filter=uniquename%20eq%20%27" + SOLUTION_UNIQUE_NAME + "%27"
    if expect(c.request("GET", q), "SCA-MIG-SOL-READ", "read solution").body["value"]:
        LOG.info("solution exists")
        return
    body = {
        "uniquename": SOLUTION_UNIQUE_NAME,
        "friendlyname": "Screen Close Assistant",
        "version": "1.0.0.0",
        "publisherid@odata.bind": f"/publishers({publisher_id})",
        "description": "Copilot Studio agent, flows, connector and Dataverse table for SCA",
    }
    expect(c.request("POST", "/solutions", body), "SCA-MIG-SOL-CREATE", "create solution")
    LOG.info("solution created")


def table_exists(c: DataverseClient) -> bool:
    resp = c.request("GET", f"/EntityDefinitions(LogicalName='{TABLE_LOGICAL}')?$select=LogicalName", ok=(200, 404))
    return resp.status == 200


def create_table(c: DataverseClient) -> None:
    if table_exists(c):
        LOG.info("table exists: %s", TABLE_LOGICAL)
        return
    body = {
        "@odata.type": "Microsoft.Dynamics.CRM.EntityMetadata",
        "SchemaName": TABLE_SCHEMA,
        "DisplayName": label("SCA Analysis Request"),
        "DisplayCollectionName": label("SCA Analysis Requests"),
        "Description": label("One row per screenshot analysis request. Screenshots are never stored."),
        "OwnershipType": "UserOwned",
        "IsActivity": False,
        "HasActivities": False,
        "HasNotes": False,
        "Attributes": [
            {
                "@odata.type": "Microsoft.Dynamics.CRM.StringAttributeMetadata",
                "SchemaName": "sca_Name",
                "IsPrimaryName": True,
                "RequiredLevel": required_level("None"),
                "MaxLength": 100,
                "FormatName": {"Value": "Text"},
                "DisplayName": label("Name"),
                "Description": label("Copy of requestId for display"),
            }
        ],
    }
    expect(c.request("POST", "/EntityDefinitions", body, solution=SOLUTION_UNIQUE_NAME),
           "SCA-MIG-TABLE-CREATE", "create table")
    LOG.info("table created: %s", TABLE_LOGICAL)


def existing_attributes(c: DataverseClient) -> set[str]:
    resp = expect(c.request("GET", f"/EntityDefinitions(LogicalName='{TABLE_LOGICAL}')/Attributes?$select=LogicalName"),
                  "SCA-MIG-ATTR-READ", "read attributes")
    return {a["LogicalName"] for a in resp.body["value"]}


def create_columns(c: DataverseClient) -> None:
    have = existing_attributes(c)
    for col in COLUMNS:
        logical = col["SchemaName"].lower()
        if logical in have:
            LOG.info("column exists: %s", logical)
            continue
        expect(c.request("POST", f"/EntityDefinitions(LogicalName='{TABLE_LOGICAL}')/Attributes", col,
                         solution=SOLUTION_UNIQUE_NAME), "SCA-MIG-ATTR-CREATE", f"create column {logical}")
        LOG.info("column created: %s", logical)


def create_keys(c: DataverseClient) -> None:
    resp = expect(c.request("GET", f"/EntityDefinitions(LogicalName='{TABLE_LOGICAL}')/Keys?$select=LogicalName,EntityKeyIndexStatus"),
                  "SCA-MIG-KEY-READ", "read keys")
    have = {k["LogicalName"]: k["EntityKeyIndexStatus"] for k in resp.body["value"]}
    for key in KEYS:
        logical = key["SchemaName"].lower()
        if logical in have:
            LOG.info("key exists: %s (index status %s)", logical, have[logical])
            continue
        body = {
            "@odata.type": "Microsoft.Dynamics.CRM.EntityKeyMetadata",
            "SchemaName": key["SchemaName"],
            "DisplayName": label(key["DisplayName"]),
            "KeyAttributes": key["KeyAttributes"],
        }
        expect(c.request("POST", f"/EntityDefinitions(LogicalName='{TABLE_LOGICAL}')/Keys", body,
                         solution=SOLUTION_UNIQUE_NAME), "SCA-MIG-KEY-CREATE", f"create key {logical}")
        LOG.info("key created: %s (index builds asynchronously)", logical)


def wait_for_keys(c: DataverseClient, timeout_s: int = 600) -> None:
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        resp = expect(c.request("GET", f"/EntityDefinitions(LogicalName='{TABLE_LOGICAL}')/Keys?$select=LogicalName,EntityKeyIndexStatus"),
                      "SCA-MIG-KEY-READ", "read keys")
        statuses = {k["LogicalName"]: k["EntityKeyIndexStatus"] for k in resp.body["value"]}
        LOG.info("key index status: %s", statuses)
        if statuses and all(s == "Active" for s in statuses.values()):
            return
        if any(s == "Failed" for s in statuses.values()):
            raise MigrationError("SCA-MIG-KEY-FAILED", f"key index build failed: {statuses}")
        time.sleep(10)
    raise MigrationError("SCA-MIG-KEY-TIMEOUT", "alternate key indexes not Active after 10 minutes")


def publish(c: DataverseClient) -> None:
    xml = f"<importexportxml><entities><entity>{TABLE_LOGICAL}</entity></entities></importexportxml>"
    expect(c.request("POST", "/PublishXml", {"ParameterXml": xml}), "SCA-MIG-PUBLISH", "publish customizations")
    LOG.info("customizations published")


def down(c: DataverseClient) -> None:
    if not table_exists(c):
        LOG.info("nothing to delete")
        return
    expect(c.request("DELETE", f"/EntityDefinitions(LogicalName='{TABLE_LOGICAL}')"), "SCA-MIG-DOWN", "delete table")
    LOG.info("table deleted")


def status(c: DataverseClient) -> None:
    if not table_exists(c):
        print(json.dumps({"table": TABLE_LOGICAL, "exists": False}))
        return
    keys = c.request("GET", f"/EntityDefinitions(LogicalName='{TABLE_LOGICAL}')/Keys?$select=LogicalName,EntityKeyIndexStatus").body
    print(json.dumps({
        "table": TABLE_LOGICAL,
        "exists": True,
        "columns": sorted(a for a in existing_attributes(c) if a.startswith("sca_")),
        "keys": {k["LogicalName"]: k["EntityKeyIndexStatus"] for k in keys["value"]},
    }, indent=2))


def main() -> int:
    parser = argparse.ArgumentParser(description="SCA Dataverse migration 001")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--down", action="store_true", help="delete the table")
    group.add_argument("--status", action="store_true", help="print current state")
    parser.add_argument("--url", default=os.environ.get("SCA_DATAVERSE_URL"), help="https://<org>.crm.dynamics.com")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

    if not args.url:
        LOG.error("[SCA-MIG-CONFIG] set SCA_DATAVERSE_URL or pass --url")
        return 2
    parsed = urllib.parse.urlparse(args.url)
    if parsed.scheme != "https" or not parsed.netloc.endswith(".dynamics.com"):
        LOG.error("[SCA-MIG-CONFIG] --url must be https://<org>.crm*.dynamics.com")
        return 2

    base = f"https://{parsed.netloc}"
    try:
        client = DataverseClient(base, get_token(base))
        if args.status:
            status(client)
        elif args.down:
            down(client)
        else:
            publisher_id = ensure_publisher(client)
            ensure_solution(client, publisher_id)
            create_table(client)
            create_columns(client)
            publish(client)
            create_keys(client)
            wait_for_keys(client)
            publish(client)
            LOG.info("migration 001 complete")
    except MigrationError as err:
        LOG.exception("migration failed: %s", err)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
