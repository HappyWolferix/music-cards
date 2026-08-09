#!/bin/sh
# Checks every spotify:track:<id> link in data/songs.csv against Spotify's
# public oEmbed endpoint (no API key needed). Reports rows whose track is
# missing, malformed, or unreachable.
#
# Usage: tools/check_spotify_links.sh [path-to-csv]
set -u
cd "$(dirname "$0")/.."

csv="${1:-data/songs.csv}"
fail=0
checked=0

while IFS=';' read -r title artist year link; do
    case "$title" in
        \#*|"") continue ;;
    esac

    case "$link" in
        spotify:track:*)
            id="${link#spotify:track:}"
            ;;
        *)
            echo "MALFORMED LINK: \"$title\" by $artist -> '$link'"
            fail=1
            continue
            ;;
    esac

    checked=$((checked + 1))
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 \
        "https://open.spotify.com/oembed?url=spotify:track:${id}")

    if [ "$code" != "200" ]; then
        echo "BROKEN LINK ($code): \"$title\" by $artist -> $link"
        fail=1
    fi
done < "$csv"

echo "Checked $checked links."
if [ "$fail" -ne 0 ]; then
    echo "Some links are broken or malformed."
    exit 1
fi
echo "All links OK."
