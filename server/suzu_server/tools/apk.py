"""APK reverse-engineering tools — decompile, recompile, sign."""

from __future__ import annotations

import asyncio
import os
import shutil
from typing import Any

from ..config import settings
from .base import ToolContext, ToolResult, ToolSpec, resolve_in_workspace


async def _run_cmd(cmd: list[str], cwd: str | None = None, timeout: int = 600) -> tuple[int, str]:
    proc = await asyncio.create_subprocess_exec(
        *cmd,
        cwd=cwd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
    )
    try:
        out_bytes = await asyncio.wait_for(proc.stdout.read() if proc.stdout else b"", timeout=timeout)
        rc = await asyncio.wait_for(proc.wait(), timeout=timeout)
    except TimeoutError:
        proc.kill()
        await proc.wait()
        return 124, f"timeout after {timeout}s"
    return rc, out_bytes.decode("utf-8", errors="replace")


async def _decompile(ctx: ToolContext, args: dict[str, Any]) -> ToolResult:
    apk = args.get("apk_path")
    out = args.get("output_dir", "decompiled")
    engine = args.get("engine", "apktool")  # apktool | jadx
    if not isinstance(apk, str):
        return ToolResult(output="error: missing `apk_path`", is_error=True)
    try:
        apk_p = resolve_in_workspace(ctx.workspace, apk)
        out_p = resolve_in_workspace(ctx.workspace, out)
    except ValueError as exc:
        return ToolResult(output=f"error: {exc}", is_error=True)
    if not apk_p.exists():
        return ToolResult(output=f"error: APK not found: {apk_p}", is_error=True)
    if out_p.exists():
        shutil.rmtree(out_p)
    out_p.parent.mkdir(parents=True, exist_ok=True)

    if ctx.on_preview:
        await ctx.on_preview("apk_decompile", {"phase": "start", "engine": engine, "apk": str(apk_p)})

    if engine == "jadx":
        cmd = [str(settings.jadx_bin), "-d", str(out_p), str(apk_p)]
    else:
        cmd = ["java", "-jar", str(settings.apktool_jar), "d", "-f", "-o", str(out_p), str(apk_p)]
    rc, log = await _run_cmd(cmd, timeout=900)

    if ctx.on_preview:
        await ctx.on_preview("apk_decompile", {"phase": "end", "exit_code": rc})

    return ToolResult(
        output=f"engine={engine}\nexit={rc}\n{log[-4000:]}",
        is_error=(rc != 0),
        metadata={"output_dir": str(out_p), "engine": engine},
    )


async def _recompile(ctx: ToolContext, args: dict[str, Any]) -> ToolResult:
    src = args.get("source_dir")
    out = args.get("output_apk", "recompiled.apk")
    if not isinstance(src, str):
        return ToolResult(output="error: missing `source_dir`", is_error=True)
    try:
        src_p = resolve_in_workspace(ctx.workspace, src)
        out_p = resolve_in_workspace(ctx.workspace, out)
    except ValueError as exc:
        return ToolResult(output=f"error: {exc}", is_error=True)
    if not src_p.exists() or not src_p.is_dir():
        return ToolResult(output=f"error: source dir not found: {src_p}", is_error=True)

    cmd = ["java", "-jar", str(settings.apktool_jar), "b", "-f", "-o", str(out_p), str(src_p)]
    rc, log = await _run_cmd(cmd, timeout=900)
    return ToolResult(
        output=f"recompile exit={rc}\n{log[-4000:]}",
        is_error=(rc != 0),
        metadata={"output_apk": str(out_p)},
    )


async def _sign(ctx: ToolContext, args: dict[str, Any]) -> ToolResult:
    apk = args.get("apk_path")
    keystore = args.get("keystore")  # if omitted, generate debug.keystore
    if not isinstance(apk, str):
        return ToolResult(output="error: missing `apk_path`", is_error=True)
    try:
        apk_p = resolve_in_workspace(ctx.workspace, apk)
    except ValueError as exc:
        return ToolResult(output=f"error: {exc}", is_error=True)
    if not apk_p.exists():
        return ToolResult(output=f"error: APK not found: {apk_p}", is_error=True)

    # Ensure we have a keystore
    if keystore:
        try:
            ks_p = resolve_in_workspace(ctx.workspace, keystore)
        except ValueError as exc:
            return ToolResult(output=f"error: {exc}", is_error=True)
        ks_pass = args.get("keystore_password", "android")
        key_alias = args.get("key_alias", "androiddebugkey")
        key_pass = args.get("key_password", ks_pass)
    else:
        ks_p = ctx.workspace / "debug.keystore"
        ks_pass = "android"
        key_alias = "androiddebugkey"
        key_pass = "android"
        if not ks_p.exists():
            gen_cmd = [
                "keytool",
                "-genkeypair",
                "-v",
                "-keystore", str(ks_p),
                "-alias", key_alias,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "10000",
                "-storepass", ks_pass,
                "-keypass", key_pass,
                "-dname", "CN=Suzu Debug, OU=Suzu, O=Suzu, L=Local, S=Local, C=ID",
            ]
            rc, log = await _run_cmd(gen_cmd, timeout=120)
            if rc != 0:
                return ToolResult(output=f"keytool failed:\n{log}", is_error=True)

    # Align then sign
    aligned = apk_p.with_suffix(".aligned.apk")
    align_cmd = [str(settings.zipalign_bin), "-f", "-p", "4", str(apk_p), str(aligned)]
    rc, log = await _run_cmd(align_cmd, timeout=120)
    if rc != 0:
        return ToolResult(output=f"zipalign failed:\n{log}", is_error=True)

    signed = apk_p.with_suffix(".signed.apk")
    sign_cmd = [
        str(settings.apksigner_bin), "sign",
        "--ks", str(ks_p),
        "--ks-pass", f"pass:{ks_pass}",
        "--ks-key-alias", key_alias,
        "--key-pass", f"pass:{key_pass}",
        "--out", str(signed),
        str(aligned),
    ]
    rc2, log2 = await _run_cmd(sign_cmd, timeout=120)
    os.remove(aligned)
    if rc2 != 0:
        return ToolResult(output=f"apksigner failed:\n{log2}", is_error=True)
    return ToolResult(
        output=f"signed APK: {signed}\nkeystore: {ks_p}",
        metadata={"signed_apk": str(signed), "keystore": str(ks_p)},
    )


APK_DECOMPILE = ToolSpec(
    name="apk_decompile",
    description=(
        "Decompile an APK into resources + smali (apktool) or Java pseudocode (jadx). "
        "Use apktool when you want to modify and recompile; use jadx when you just want to read the code."
    ),
    input_schema={
        "type": "object",
        "properties": {
            "apk_path": {"type": "string"},
            "output_dir": {"type": "string", "description": "Where to write the result (default 'decompiled')"},
            "engine": {"type": "string", "enum": ["apktool", "jadx"], "description": "Default 'apktool'"},
        },
        "required": ["apk_path"],
    },
    executor=_decompile,
)

APK_RECOMPILE = ToolSpec(
    name="apk_recompile",
    description="Recompile an apktool-decompiled directory back into an APK.",
    input_schema={
        "type": "object",
        "properties": {
            "source_dir": {"type": "string"},
            "output_apk": {"type": "string"},
        },
        "required": ["source_dir"],
    },
    executor=_recompile,
)

APK_SIGN = ToolSpec(
    name="apk_sign",
    description=(
        "Zipalign + sign an APK. If no keystore is provided, a debug keystore is generated. "
        "Outputs `<name>.signed.apk` alongside the input."
    ),
    input_schema={
        "type": "object",
        "properties": {
            "apk_path": {"type": "string"},
            "keystore": {"type": "string", "description": "Path to existing keystore (optional)"},
            "keystore_password": {"type": "string"},
            "key_alias": {"type": "string"},
            "key_password": {"type": "string"},
        },
        "required": ["apk_path"],
    },
    executor=_sign,
)

APK_TOOLS = [APK_DECOMPILE, APK_RECOMPILE, APK_SIGN]


__all__ = ["APK_TOOLS"]
