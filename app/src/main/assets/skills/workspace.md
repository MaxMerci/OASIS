---
name: workspace
description: Working with workspace files through run_command - Android shell limits, available utilities, the input/ folder, cheap in-place editing with sed.
---

# Workspace

`run_command` runs `/system/bin/sh -c <command>` (Android mksh + toybox). Working directory and `HOME` are the workspace root.

## Limits

- Stay inside the workspace, use relative paths. Absolute paths outside it, `..` and `~` are rejected before running.
- Android app sandbox, no root: no `su`, no package manager, no access to `/sdcard` or other apps' data.
- Files in the workspace can't be executed directly (Android forbids exec from app data): `./run.sh` fails, `sh run.sh` works.
- No stdin and no TTY: interactive programs (`vi`, `top`, `less`) hang until killed.
- A call is killed after 30 s, output is cut to 16000 chars.
- Each call is a new shell: `cd` and variables don't persist. Chain with `&&`.
- No network tools (curl/wget). Use `web_search` for the web.

## Utilities

The set depends on the Android version: `toybox` lists its commands, `which X` checks one. Usually present:

- files: `ls cp mv rm mkdir rmdir touch ln stat du file find realpath`
- text: `cat echo printf head tail wc grep sed cut tr sort uniq diff cmp paste xargs nl od xxd`
- archives: `tar gzip gunzip zcat unzip`
- misc: `base64 md5sum sha256sum date seq expr test env`

No `python`, `awk` only on newer versions - check before using.

## input/

Files the user attaches are saved to `input/`; their paths are in the system prompt. `input/` is wiped when the user sends new files. To keep a file, copy or move it out first: `mkdir -p docs && mv input/report.pdf docs/`.

## Reading

Don't print big files whole: `wc -l f`, `head -n 50 f`, `sed -n '100,160p' f`, `grep -n pattern f`.

## Editing

Never rewrite a whole file to change part of it - edit in place with `sed -i`:

- replace: `sed -i 's/old/new/g' f` (text with `/` - another delimiter: `s|a/b|c/d|g`)
- only on line 12: `sed -i '12s/old/new/' f`
- delete lines: `sed -i '5,8d' f`, `sed -i '/pattern/d' f`
- insert before line 3: `sed -i '3i\new line' f`, after: `sed -i '3a\new line' f`
- append to the end: `echo 'text' >> f`
- new file or long content: `cat > f <<'EOF'` ... `EOF` (heredoc bodies are not path-checked, any HTML/code is fine)

Check the result with `sed -n` or `grep -n`.

To give a file to the user, call `link_file` with its path after it is written.
