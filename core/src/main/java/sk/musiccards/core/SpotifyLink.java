package sk.musiccards.core;

/**
 * Parses the many shapes a Spotify track reference can take (QR payloads,
 * share links, raw URIs) into a canonical 22-char base62 track id.
 *
 * Accepted forms:
 *   spotify:track:6rqhFgbbKwnb9MLmUQDhG6
 *   https://open.spotify.com/track/6rqhFgbbKwnb9MLmUQDhG6?si=abc
 *   https://open.spotify.com/intl-sk/track/6rqhFgbbKwnb9MLmUQDhG6
 *   6rqhFgbbKwnb9MLmUQDhG6                       (bare id)
 */
public final class SpotifyLink {

    private SpotifyLink() {
    }

    /** Returns the track id, or null if the input is not a Spotify track reference. */
    public static String parseTrackId(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }

        if (s.startsWith("spotify:track:")) {
            return validId(s.substring("spotify:track:".length()));
        }

        String lower = s.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            int hostStart = s.indexOf("//") + 2;
            int pathStart = s.indexOf('/', hostStart);
            if (pathStart < 0) {
                return null;
            }
            String host = s.substring(hostStart, pathStart).toLowerCase();
            if (!host.equals("open.spotify.com") && !host.equals("play.spotify.com")) {
                return null;
            }
            String path = s.substring(pathStart + 1);
            // strip optional locale segment such as "intl-sk/"
            if (path.startsWith("intl-")) {
                int slash = path.indexOf('/');
                if (slash < 0) {
                    return null;
                }
                path = path.substring(slash + 1);
            }
            if (!path.startsWith("track/")) {
                return null;
            }
            String id = path.substring("track/".length());
            int cut = indexOfAny(id, "?#/");
            if (cut >= 0) {
                id = id.substring(0, cut);
            }
            return validId(id);
        }

        // bare id
        return validId(s);
    }

    /** Canonical URI understood by the Spotify Android app. */
    public static String toUri(String trackId) {
        return "spotify:track:" + trackId;
    }

    /** Web fallback for devices without the Spotify app. */
    public static String toWebUrl(String trackId) {
        return "https://open.spotify.com/track/" + trackId;
    }

    private static int indexOfAny(String s, String chars) {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }

    private static String validId(String id) {
        if (id.length() != 22) {
            return null;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            if (!ok) {
                return null;
            }
        }
        return id;
    }
}
