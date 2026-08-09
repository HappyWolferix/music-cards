package sk.musiccards.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the semicolon-separated song list (data/songs.csv).
 *
 * Format, one song per line:  title;artist;year;spotify-link
 * Lines starting with '#' and blank lines are ignored.
 * The spotify-link column accepts any form {@link SpotifyLink} understands.
 */
public final class SongCatalog {

    private SongCatalog() {
    }

    /** Thrown when a line cannot be parsed; message includes the line number. */
    public static class FormatException extends RuntimeException {
        public FormatException(String message) {
            super(message);
        }
    }

    public static List<Song> parse(Reader reader) throws IOException {
        BufferedReader br = new BufferedReader(reader);
        List<Song> songs = new ArrayList<>();
        String line;
        int lineNo = 0;
        while ((line = br.readLine()) != null) {
            lineNo++;
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }
            String[] cols = s.split(";");
            if (cols.length != 4) {
                throw new FormatException("line " + lineNo + ": expected 4 columns "
                        + "(title;artist;year;spotify-link), got " + cols.length);
            }
            int year;
            try {
                year = Integer.parseInt(cols[2].trim());
            } catch (NumberFormatException e) {
                throw new FormatException("line " + lineNo + ": year is not a number: " + cols[2]);
            }
            String trackId = SpotifyLink.parseTrackId(cols[3]);
            if (trackId == null) {
                throw new FormatException("line " + lineNo + ": not a Spotify track link: " + cols[3]);
            }
            songs.add(new Song(cols[0].trim(), cols[1].trim(), year, trackId));
        }
        return songs;
    }
}
