#!/usr/bin/env python3
"""Local read-only jCodeMunch-compatible MCP server using stdlib only."""
import json, os, re, sys
from pathlib import Path

IGNORE = {".git", "node_modules", "target", "dist", "build", ".vite", ".idea", "__pycache__"}
TEXT_EXT = {".java", ".kt", ".ts", ".tsx", ".js", ".jsx", ".vue", ".py", ".go", ".rs", ".cs", ".sql", ".xml", ".yml", ".yaml", ".json", ".md", ".css", ".html"}
root = Path(os.environ.get("JCODEMUNCH_ROOT", os.getcwd())).resolve()
texts = {}

def rel(path): return path.relative_to(root).as_posix()
def scan():
    global texts
    texts = {}
    if not root.exists(): return
    for path in root.rglob("*"):
        if not path.is_file() or any(part in IGNORE for part in path.parts) or path.suffix.lower() not in TEXT_EXT: continue
        try: texts[rel(path)] = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError): pass

def symbols(source):
    result = []
    for line_no, line in enumerate(source.splitlines(), 1):
        clean = line.strip()
        if not clean or len(clean) >= 300: continue
        if re.search(r"\b(class|interface|enum|struct|def|function|func|fn)\s+[A-Za-z_$][\w$]*", clean) or re.search(r"\b[A-Za-z_$][\w$]*\s*\([^;{}]*\)\s*(\{|=>)?", clean):
            match = re.search(r"\b(?:class|interface|enum|struct|def|function|func|fn)\s+([A-Za-z_$][\w$]*)|\b([A-Za-z_$][\w$]*)\s*\(", clean)
            result.append({"name": next((x for x in (match.groups() if match else ()) if x), clean[:80]), "line": line_no, "source": clean})
    return result

def search_text(query, limit=200):
    rx = re.compile(query, re.I)
    matches = []
    for path, source in texts.items():
        for line_no, line in enumerate(source.splitlines(), 1):
            if rx.search(line): matches.append({"path": path, "line": line_no, "text": line.strip()[:500]})
    return matches[:limit]

def call(name, args):
    global root
    if name == "resolve_repo": return {"path": str(root), "exists": root.exists(), "indexedFiles": len(texts)}
    if name == "index_folder":
        root = Path(args.get("path", args.get("folder", str(root)))).expanduser().resolve(); scan()
        return {"root": str(root), "indexedFiles": len(texts)}
    if name == "get_repo_outline": return {"root": str(root), "directories": sorted({str(Path(p).parent) for p in texts}), "fileCount": len(texts)}
    if name == "get_file_tree":
        prefix = args.get("path", "").strip("/")
        return {"files": [p for p in sorted(texts) if not prefix or p == prefix or p.startswith(prefix + "/")]}
    if name == "get_file_outline":
        path = args.get("path", ""); source = texts.get(path, "")
        return {"path": path, "lines": len(source.splitlines()), "symbols": symbols(source)}
    if name == "search_text": return {"query": args.get("query", args.get("text", "")), "matches": search_text(args.get("query", args.get("text", "")), args.get("limit", 200))}
    if name == "search_symbols":
        query = args.get("query", args.get("name", "")).lower(); matches = []
        for path, source in texts.items():
            for item in symbols(source):
                if query in item["name"].lower() or query in item["source"].lower(): matches.append({"path": path, **item})
        return {"query": query, "matches": matches[:args.get("limit", 200)]}
    if name == "get_symbol_source":
        path, symbol = args.get("path", ""), args.get("symbol", args.get("name", "")); lines = texts.get(path, "").splitlines()
        for index, line in enumerate(lines):
            if re.search(r"\b" + re.escape(symbol) + r"\b", line): return {"path": path, "symbol": symbol, "startLine": index + 1, "source": "\n".join(lines[max(0, index-3):index+30])}
        return {"path": path, "symbol": symbol, "source": ""}
    if name in {"find_references", "find_importers", "get_blast_radius"}:
        target = args.get("symbol", args.get("path", args.get("name", ""))); needle = Path(target).stem if "/" in target or "\\" in target else target
        matches = search_text(re.escape(needle)) if needle else []
        return {"target": target, "matches": matches, "count": len(matches), "note": "Textual read-only estimate; validate semantic relationships before editing."}
    raise ValueError("Unknown tool: " + name)

TOOLS = ["resolve_repo", "index_folder", "get_repo_outline", "get_file_tree", "get_file_outline", "search_symbols", "get_symbol_source", "search_text", "find_references", "find_importers", "get_blast_radius"]
scan()
for raw in sys.stdin:
    msg_id = None
    try:
        msg = json.loads(raw); msg_id = msg.get("id"); method = msg.get("method")
        if method == "initialize": result = {"protocolVersion": "2024-11-05", "capabilities": {"tools": {}}, "serverInfo": {"name": "jcodemunch", "version": "0.1.0"}}
        elif method == "tools/list": result = {"tools": [{"name": n, "description": "Read-only jCodeMunch repository tool", "inputSchema": {"type": "object", "additionalProperties": True}} for n in TOOLS]}
        elif method == "tools/call":
            params = msg.get("params", {}); result = {"content": [{"type": "text", "text": json.dumps(call(params.get("name"), params.get("arguments", {})), ensure_ascii=False)}]}
        else: result = {}
        if msg_id is not None: print(json.dumps({"jsonrpc": "2.0", "id": msg_id, "result": result}, ensure_ascii=False), flush=True)
    except Exception as exc:
        if msg_id is not None: print(json.dumps({"jsonrpc": "2.0", "id": msg_id, "error": {"code": -32603, "message": str(exc)}}, ensure_ascii=False), flush=True)
