#!/usr/bin/env python3
"""Fills the link and year columns of data/candidates.csv.

Links come from the Spotify Web API (client-credentials search). Years come from
MusicBrainz, whose recording first-release-date is the original release -- unlike
Spotify's album.release_date, which reports the date of whichever edition is on
Spotify (compilations and remasters included).

A row is only written when the match is unambiguous. Everything else lands in the
review file with its candidates, for a human to pick.

Usage:
    tools/fill_candidates.py [--limit N] [--dry-run]

Credentials: SPOTIFY_CLIENT_ID / SPOTIFY_CLIENT_SECRET, from the environment or
from .env.local in the repo root.
"""

import argparse
import base64
import json
import os
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CSV = ROOT / "data" / "candidates.csv"
REVIEW = ROOT / "data" / "candidates-review.txt"
CACHE = Path(os.environ.get("FILL_CACHE", ROOT / "build" / "fill-cache.json"))
UA = "music-cards/1.0 (https://github.com/wolferix/music-cards)"

# MusicBrainz asks for at most one request per second.
MB_DELAY = 1.1


# --------------------------------------------------------------------------- cache
class Cache:
    """Disk-backed memo so reruns cost no API calls."""

    def __init__(self, path):
        self.path = path
        self.data = json.loads(path.read_text()) if path.exists() else {}
        self.dirty = False

    def get(self, key):
        return self.data.get(key)

    def put(self, key, value):
        self.data[key] = value
        self.dirty = True

    def flush(self):
        if self.dirty:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            self.path.write_text(json.dumps(self.data, ensure_ascii=False))
            self.dirty = False


def get_json(url, headers=None, retries=5):
    req = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                return json.loads(r.read())
        except urllib.error.HTTPError as e:
            if e.code in (429, 503) and attempt < retries - 1:
                time.sleep(float(e.headers.get("Retry-After", 2 ** attempt)) + 1)
                continue
            if e.code == 404:
                return None
            raise
        except Exception:
            if attempt < retries - 1:
                time.sleep(2)
                continue
            raise
    return None


# --------------------------------------------------------------------------- matching
def fold(s):
    """Lowercase, strip diacritics and punctuation -- so 'Nič' matches 'Nic'."""
    s = unicodedata.normalize("NFKD", s)
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", " ", s.lower()).strip()


def bare_title(title):
    """Drops trailing parentheticals: 'Song (Live)' -> 'Song'."""
    return re.sub(r"\s*[\(\[].*", "", title).strip() or title


def artists(field):
    """'KALI & I.M.T. SMILE' -> ['KALI', 'I.M.T. SMILE']; feat. is dropped."""
    field = re.sub(r"(?i)\b(?:feat|ft)\b\.?", "&", field)
    return [a.strip() for a in re.split(r"\s*[&,/]\s*|\s+x\s+", field) if a.strip()]


# A hit on one of these means the track is not the original recording.
VARIANT = re.compile(r"(?i)\b(live|remix|cover|karaoke|instrumental|acoustic|"
                     r"remaster(ed)?|demo|edit|version|verze|verzia|naživo)\b")


def score(row_title, row_artists, track):
    """0..100 confidence that `track` is the recording the row means."""
    t_fold = fold(bare_title(row_title))
    hit_fold = fold(bare_title(track["name"]))
    if t_fold == hit_fold:
        title_score = 60
    elif t_fold in hit_fold or hit_fold in t_fold:
        title_score = 40
    else:
        return 0

    hit_artists = {fold(a["name"]) for a in track["artists"]}
    wanted = {fold(a) for a in row_artists}
    if wanted & hit_artists:
        # every named artist present is stronger than just one overlapping
        artist_score = 40 if wanted <= hit_artists else 30
    elif any(any(w in h or h in w for h in hit_artists) for w in wanted):
        artist_score = 20
    else:
        return 0

    penalty = 15 if VARIANT.search(track["name"]) and not VARIANT.search(row_title) else 0
    return title_score + artist_score - penalty


# --------------------------------------------------------------------------- spotify
class Spotify:
    def __init__(self, client_id, client_secret, cache):
        self.cache = cache
        auth = base64.b64encode(f"{client_id}:{client_secret}".encode()).decode()
        req = urllib.request.Request(
            "https://accounts.spotify.com/api/token",
            data=b"grant_type=client_credentials",
            headers={"Authorization": f"Basic {auth}",
                     "Content-Type": "application/x-www-form-urlencoded",
                     "User-Agent": UA})
        with urllib.request.urlopen(req, timeout=20) as r:
            self.token = json.loads(r.read())["access_token"]

    def search(self, title, artist):
        key = f"sp:{title}|{artist}"
        cached = self.cache.get(key)
        if cached is not None:
            return cached
        q = f'track:"{bare_title(title)}" artist:"{artist}"'
        url = ("https://api.spotify.com/v1/search?type=track&limit=10&q="
               + urllib.parse.quote(q))
        data = get_json(url, {"Authorization": f"Bearer {self.token}"}) or {}
        items = [{"id": t["id"], "name": t["name"],
                  "artists": [{"name": a["name"]} for a in t["artists"]],
                  "album": t["album"]["name"],
                  "release_date": t["album"].get("release_date", "")}
                 for t in data.get("tracks", {}).get("items", [])]
        if not items:  # retry unfielded -- the fielded query is strict about spelling
            url = ("https://api.spotify.com/v1/search?type=track&limit=10&q="
                   + urllib.parse.quote(f"{bare_title(title)} {artist}"))
            data = get_json(url, {"Authorization": f"Bearer {self.token}"}) or {}
            items = [{"id": t["id"], "name": t["name"],
                      "artists": [{"name": a["name"]} for a in t["artists"]],
                      "album": t["album"]["name"],
                      "release_date": t["album"].get("release_date", "")}
                     for t in data.get("tracks", {}).get("items", [])]
        self.cache.put(key, items)
        return items


# --------------------------------------------------------------------------- musicbrainz
def musicbrainz_year(title, artist, cache):
    """Earliest first-release-date across matching recordings, or ''."""
    key = f"mb:{title}|{artist}"
    cached = cache.get(key)
    if cached is None:
        q = f'recording:"{bare_title(title)}" AND artist:"{artist}"'
        url = ("https://musicbrainz.org/ws/2/recording?fmt=json&limit=25&query="
               + urllib.parse.quote(q))
        data = get_json(url) or {}
        cached = [{"title": r.get("title", ""),
                   "artists": [{"name": c.get("name", "")}
                               for c in r.get("artist-credit", [])
                               if isinstance(c, dict) and "name" in c],
                   "date": r.get("first-release-date", "")}
                  for r in data.get("recordings", [])]
        cache.put(key, cached)
        time.sleep(MB_DELAY)

    years = []
    for rec in cached:
        if score(title, artists(artist), {"name": rec["title"],
                                          "artists": rec["artists"]}) >= 70:
            m = re.match(r"(\d{4})", rec["date"] or "")
            if m:
                years.append(int(m.group(1)))
    return str(min(years)) if years else ""


def spotify_year(hits, title, artist):
    """Earliest Spotify album year among confident matches -- the cross-check."""
    years = []
    for h in hits:
        if score(title, artists(artist), h) >= 70:
            m = re.match(r"(\d{4})", h.get("release_date") or "")
            if m:
                years.append(int(m.group(1)))
    return str(min(years)) if years else ""


# --------------------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, help="process at most N unfilled rows")
    ap.add_argument("--dry-run", action="store_true", help="write nothing")
    ap.add_argument("--threshold", type=int, default=85,
                    help="minimum score to auto-fill a link (default 85)")
    args = ap.parse_args()

    env = {}
    envfile = ROOT / ".env.local"
    if envfile.exists():
        for line in envfile.read_text().splitlines():
            if "=" in line and not line.startswith("#"):
                k, v = line.split("=", 1)
                env[k.strip()] = v.strip()
    cid = os.environ.get("SPOTIFY_CLIENT_ID") or env.get("SPOTIFY_CLIENT_ID")
    secret = os.environ.get("SPOTIFY_CLIENT_SECRET") or env.get("SPOTIFY_CLIENT_SECRET")
    if not cid or not secret:
        sys.exit("Missing SPOTIFY_CLIENT_ID / SPOTIFY_CLIENT_SECRET "
                 "(environment or .env.local).")

    cache = Cache(CACHE)
    sp = Spotify(cid, secret, cache)

    lines = CSV.read_text(encoding="utf-8").splitlines()
    out, review = [], []
    filled = conflicts = flagged = done = 0

    for line in lines:
        s = line.strip()
        if not s or s.startswith("#"):
            out.append(line)
            continue
        cols = line.split(";")
        if len(cols) < 4:
            out.append(line)
            continue
        title, artist = cols[0].strip(), cols[1].strip()
        has_link = "spotify" in cols[3]
        has_year = re.fullmatch(r"\d{4}", cols[2].strip()) is not None
        if (has_link and has_year) or (args.limit and done >= args.limit):
            out.append(line)
            continue
        done += 1

        hits = []
        for a in artists(artist)[:2]:
            hits += sp.search(title, a)
            if hits:
                break
        ranked = sorted(((score(title, artists(artist), h), h) for h in hits),
                        key=lambda x: -x[0])
        best = ranked[0] if ranked else (0, None)

        if not has_link and best[0] >= args.threshold:
            cols[3] = f"https://open.spotify.com/track/{best[1]['id']}"
            filled += 1
        elif not has_link:
            flagged += 1
            cands = "; ".join(f"[{sc}] {h['name']} — "
                              f"{', '.join(a['name'] for a in h['artists'])} "
                              f"({h.get('release_date', '')[:4]}) "
                              f"https://open.spotify.com/track/{h['id']}"
                              for sc, h in ranked[:4]) or "no search hits"
            review.append(f"{title};{artist}\n    {cands}")

        if not has_year:
            mb = musicbrainz_year(title, artist, cache)
            spy = spotify_year(hits, title, artist)
            if mb:
                cols[2] = mb
                if spy and spy != mb:
                    conflicts += 1
                    review.append(f"{title};{artist}\n    YEAR: MusicBrainz {mb} "
                                  f"vs earliest Spotify album {spy} — used {mb}")
            elif spy:
                # no MusicBrainz match: Spotify's date may be a reissue, so flag it
                cols[2] = spy
                review.append(f"{title};{artist}\n    YEAR: {spy} from Spotify only "
                              f"(no MusicBrainz match) — may be a reissue")

        out.append(";".join(cols))
        cache.flush()

    cache.flush()
    if not args.dry_run:
        CSV.write_text("\n".join(out) + "\n", encoding="utf-8")
        REVIEW.write_text("\n".join(review) + "\n", encoding="utf-8")
    print(f"processed {done} rows: {filled} links filled, {flagged} needing a "
          f"human pick, {conflicts} year conflicts -> {REVIEW.name}")


if __name__ == "__main__":
    main()
