package sk.musiccards.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Spotify playlist exports in the Exportify CSV format
 * (header: Track URI,Track Name,Album Name,Artist Name(s),Release Date,...).
 * Quoted fields (RFC 4180 style, "" escapes) and a UTF-8 BOM are handled.
 *
 * Only the columns needed for cards are extracted; Release Date is the ALBUM
 * date, so for compilation albums the year may be later than the song's
 * original release — {@link Entry#suspiciousYear} flags likely cases.
 */
public final class PlaylistExport {

    /** One playlist row reduced to card-relevant fields. */
    public static final class Entry {
        public final String trackId;
        public final String title;
        public final String artist;   // first artist only
        public final String album;
        public final int year;        // 0 when missing/unparsable
        public final boolean suspiciousYear;

        Entry(String trackId, String title, String artist, String album, int year) {
            this.trackId = trackId;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.year = year;
            this.suspiciousYear = looksLikeCompilation(album);
        }
    }

    private PlaylistExport() {
    }

    public static List<Entry> parse(Reader reader) throws IOException {
        BufferedReader br = new BufferedReader(reader);
        List<Entry> entries = new ArrayList<>();
        String header = br.readLine();
        if (header == null) {
            return entries;
        }
        if (header.startsWith("﻿")) {
            header = header.substring(1);
        }
        List<String> cols = splitCsvRecord(br, header);
        int iUri = cols.indexOf("Track URI");
        int iName = cols.indexOf("Track Name");
        int iAlbum = cols.indexOf("Album Name");
        int iArtist = cols.indexOf("Artist Name(s)");
        int iDate = cols.indexOf("Release Date");
        if (iUri < 0 || iName < 0 || iArtist < 0 || iDate < 0) {
            throw new IOException("not an Exportify CSV: missing expected columns in header");
        }

        String line;
        while ((line = br.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            List<String> f = splitCsvRecord(br, line);
            int max = Math.max(Math.max(iUri, iName), Math.max(iArtist, iDate));
            if (f.size() <= max) {
                continue; // truncated row
            }
            String trackId = SpotifyLink.parseTrackId(f.get(iUri));
            if (trackId == null) {
                continue; // local files / episodes
            }
            String artist = f.get(iArtist);
            int comma = artist.indexOf(',');
            if (comma > 0) {
                artist = artist.substring(0, comma);
            }
            entries.add(new Entry(trackId, f.get(iName).trim(), artist.trim(),
                    iAlbum >= 0 && f.size() > iAlbum ? f.get(iAlbum).trim() : "",
                    parseYear(f.get(iDate))));
        }
        return entries;
    }

    /** Year = first 4-digit prefix of the date field ("1984-01-01", "1978"). */
    private static int parseYear(String date) {
        String d = date.trim();
        if (d.length() < 4) {
            return 0;
        }
        try {
            return Integer.parseInt(d.substring(0, 4));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean looksLikeCompilation(String album) {
        String a = album.toLowerCase();
        return a.contains("best of") || a.contains("greatest") || a.contains("hits")
                || a.contains(" naj") || a.startsWith("naj") || a.contains("gold")
                || a.contains("platinum") || a.contains("collection") || a.contains("legenda")
                || a.contains("výber") || a.contains("vyber") || a.contains("supermix");
    }

    /**
     * Splits one CSV record into fields. A record may span multiple physical
     * lines when a quoted field contains a newline; continuation lines are
     * pulled from the reader.
     */
    private static List<String> splitCsvRecord(BufferedReader br, String firstLine)
            throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        String line = firstLine;
        boolean inQuotes = false;
        int i = 0;
        while (true) {
            if (i >= line.length()) {
                if (inQuotes) {
                    String next = br.readLine();
                    if (next == null) {
                        break;
                    }
                    cur.append('\n');
                    line = next;
                    i = 0;
                    continue;
                }
                break;
            }
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
            i++;
        }
        fields.add(cur.toString());
        return fields;
    }
}
