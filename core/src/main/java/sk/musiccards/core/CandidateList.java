package sk.musiccards.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses the candidate list (data/candidates.csv) — the songs we want on cards
 * but have no Spotify link for yet.
 *
 * Two row shapes are accepted, because the file collects wishes from anywhere:
 * <ul>
 *   <li>4 columns {@code title;artist;year;link} — the catalog shape, i.e. a
 *       hand-written wish.</li>
 *   <li>8 columns {@code title;artist;year;link;charts;best-position;weeks;publisher}
 *       — a row this list produced earlier. The first four columns are exactly the
 *       catalog shape, so a finished row copies straight into data/songs.csv.</li>
 *   <li>7 columns — the same without the link column (older files).</li>
 * </ul>
 *
 * The file is both an output and an input of the importer, so parsing must be
 * lossless: a row read here and written back must be identical. In particular
 * {@link Placing#WISHLIST} lives in the {@code charts} column and nowhere else —
 * it is the only record that a song was wanted without ever charting.
 */
public final class CandidateList {

    /** One reason a song is wanted: a chart placing, or a plain wish. */
    public static final class Placing {
        /** Chart tag for a song nobody charted — a hand-written wish. */
        public static final String WISHLIST = "wishlist";
        /** Sorts after every real chart placing. */
        public static final int NO_POSITION = 999;

        public final String title;
        public final String artist;
        public final String year;       // "" when unknown
        public final String link;       // spotify:track:... , or "" when not filled in
        public final String chart;      // chart tag, or WISHLIST
        public final String week;       // "" for a wish
        public final int position;      // NO_POSITION for a wish
        public final String publisher;  // "" when unknown

        Placing(String title, String artist, String year, String link, String chart,
                String week, int position, String publisher) {
            this.title = title;
            this.artist = artist;
            this.year = year;
            this.link = link;
            this.chart = chart;
            this.week = week;
            this.position = position;
            this.publisher = publisher;
        }

        public boolean isWish() {
            return WISHLIST.equals(chart);
        }
    }

    /** Thrown when a line has neither accepted column count. */
    public static class FormatException extends RuntimeException {
        public FormatException(String message) {
            super(message);
        }
    }

    private CandidateList() {
    }

    /**
     * Reads every row, expanding one stored row into one {@link Placing} per
     * reason it is wanted (each week it charted, plus a wish when tagged).
     */
    public static List<Placing> parse(Reader reader) throws IOException {
        List<Placing> placings = new ArrayList<>();
        BufferedReader br = new BufferedReader(reader);
        String line;
        int lineNo = 0;
        while ((line = br.readLine()) != null) {
            lineNo++;
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }
            String[] cols = s.split(";", -1);
            if (cols.length == 4) {
                placings.add(new Placing(cols[0].trim(), cols[1].trim(), year(cols[2]),
                        link(cols[3]), Placing.WISHLIST, "", Placing.NO_POSITION, ""));
            } else if (cols.length == 7) {
                placings.addAll(expand(cols, ""));            // older file, no link column
            } else if (cols.length >= 8) {
                placings.addAll(expand(shiftOutLink(cols), link(cols[3])));
            } else {
                throw new FormatException("line " + lineNo + ": expected 4 columns (wish), "
                        + "8 (candidate) or 7 (candidate without link), got " + cols.length);
            }
        }
        return placings;
    }

    /** Drops the link column so the rest lines up with the older 7-column shape. */
    private static String[] shiftOutLink(String[] cols) {
        String[] without = new String[cols.length - 1];
        System.arraycopy(cols, 0, without, 0, 3);
        System.arraycopy(cols, 4, without, 3, cols.length - 4);
        return without;
    }

    private static List<Placing> expand(String[] cols, String link) {
        String title = cols[0].trim();
        String artist = cols[1].trim();
        String year = year(cols[2]);
        String publisher = cols[6].trim();
        List<Placing> placings = new ArrayList<>();
        if (Arrays.asList(cols[3].trim().split("\\s+")).contains(Placing.WISHLIST)) {
            placings.add(new Placing(title, artist, year, link, Placing.WISHLIST, "",
                    Placing.NO_POSITION, publisher));
        }
        for (String week : cols[5].trim().split("\\s+")) {
            Placing placing = placing(title, artist, year, link, week, publisher);
            if (placing != null) {
                placings.add(placing);
            }
        }
        return placings;
    }

    /** "cz:202631#1" -> one placing; null when the token is not one. */
    private static Placing placing(String title, String artist, String year, String link,
            String token, String publisher) {
        int colon = token.indexOf(':');
        int hash = token.lastIndexOf('#');
        if (colon <= 0 || hash <= colon + 1 || hash == token.length() - 1) {
            return null;
        }
        String position = token.substring(hash + 1);
        for (int i = 0; i < position.length(); i++) {
            if (!Character.isDigit(position.charAt(i))) {
                return null;
            }
        }
        return new Placing(title, artist, year, link, token.substring(0, colon),
                token.substring(colon + 1, hash), Integer.parseInt(position), publisher);
    }

    /**
     * Normalizes a hand-typed link to {@code spotify:track:<id>}; anything that is
     * not a real track reference (blank, TODO, a typo) reads as "not filled in yet".
     */
    private static String link(String value) {
        String trackId = SpotifyLink.parseTrackId(value.trim());
        return trackId == null ? "" : SpotifyLink.toUri(trackId);
    }

    /** Keeps a plain 4-digit year, drops anything else (TODO, dates, blanks). */
    private static String year(String value) {
        String v = value.trim();
        if (v.length() != 4) {
            return "";
        }
        for (int i = 0; i < v.length(); i++) {
            if (!Character.isDigit(v.charAt(i))) {
                return "";
            }
        }
        return v;
    }
}
