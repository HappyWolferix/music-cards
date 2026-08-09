#!/bin/sh
# Validates the AI docs: every relative markdown link in AGENTS.md files and
# docs/ must point at an existing file, and every path listed in
# docs/ai/manifest.yml must exist.
set -u
cd "$(dirname "$0")/.."
fail=0

check_links() {
    file="$1"
    dir=$(dirname "$file")
    # extract (target) parts of [text](target) links, keep relative paths only
    grep -o '](\([^)#]*\))' "$file" 2>/dev/null | sed 's/^](//; s/)$//' | while read -r target; do
        case "$target" in
            http://*|https://*|mailto:*|"") continue ;;
        esac
        if [ ! -e "$dir/$target" ] && [ ! -e "$target" ]; then
            echo "BROKEN LINK in $file: $target"
            touch .validate_failed
        fi
    done
}

rm -f .validate_failed
for f in AGENTS.md */AGENTS.md docs/*.md docs/*/*.md docs/*/*/*.md; do
    [ -f "$f" ] && check_links "$f"
done

# every path-looking entry in the manifest must exist
if [ -f docs/ai/manifest.yml ]; then
    grep -oE '(docs|core|app|cards|tools|data)/[A-Za-z0-9_/.-]+' docs/ai/manifest.yml | sort -u | while read -r p; do
        if [ ! -e "$p" ]; then
            echo "MISSING PATH in manifest.yml: $p"
            touch .validate_failed
        fi
    done
fi

if [ -f .validate_failed ]; then
    rm -f .validate_failed
    exit 1
fi
echo "AI docs OK"
