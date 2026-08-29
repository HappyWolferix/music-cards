package sk.musiccards.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses an IFPI radio airplay chart export (the "SK - RADIO - TOP 50 SK" and
 * "CZ - RADIO - TOP 50 CZ" files served as <code>hitparada.xls</code>, which are
 * really an HTML table). Which chart a file holds is only stated inside it —
 * the download name carries no country, so {@link Entry#chart} is the only
 * reliable way to tell SK and CZ snapshots apart.
 *
 * A chart row carries no Spotify track id, so entries from here are candidates
 * for the catalog, not catalog rows — {@code ImportCharts} stages them in
 * data/candidates.csv and the link is filled in by hand later.
 */
public final class ChartExport {

    /** One chart row: where a song stood in one weekly snapshot. */
    public static final class Entry {
        public final int position;
        public final String artist;
        public final String title;
        public final String publisher;
        /** Chart week as printed in the file header, e.g. "202351,52". */
        public final String week;
        /** Chart name from the header, e.g. "CZ - RADIO - TOP 50 CZ"; "" when absent. */
        public final String chart;

        Entry(int position, String artist, String title, String publisher, String week,
                String chart) {
            this.position = position;
            this.artist = artist;
            this.title = title;
            this.publisher = publisher;
            this.week = week;
            this.chart = chart;
        }
    }

    private static final Pattern CHART = Pattern.compile(
            "<b>\\s*([A-Z]{2}\\s*-[^<]*?)\\s*</b>", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEEK = Pattern.compile("[Tt][^<]*den\\s*([0-9][0-9,]*)");
    private static final Pattern ROW = Pattern.compile("<tr>(.*?)(?=<tr>|</table>|$)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL = Pattern.compile("<t[dh][^>]*>(.*?)</t[dh]>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private ChartExport() {
    }

    /**
     * Reads every ranked row of one chart file. Header and separator rows are
     * skipped; a row counts when its first cell is a chart position.
     */
    public static List<Entry> parse(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        String html = sb.toString();

        Matcher weekMatcher = WEEK.matcher(html);
        String week = weekMatcher.find() ? weekMatcher.group(1) : "";
        Matcher chartMatcher = CHART.matcher(html);
        String chart = chartMatcher.find() ? text(chartMatcher.group(1)) : "";

        List<Entry> entries = new ArrayList<>();
        Matcher rows = ROW.matcher(html);
        while (rows.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cellMatcher = CELL.matcher(rows.group(1));
            while (cellMatcher.find()) {
                cells.add(text(cellMatcher.group(1)));
            }
            // Columns: TT | -1T | -2T | Interpret | Titul | Vyd. | NP | PK | PH
            if (cells.size() < 5) {
                continue;
            }
            int position = number(cells.get(0));
            if (position <= 0 || cells.get(3).isEmpty() || cells.get(4).isEmpty()) {
                continue;
            }
            String publisher = cells.size() > 5 ? cells.get(5) : "";
            entries.add(new Entry(position, cells.get(3), cells.get(4), publisher, week, chart));
        }
        return entries;
    }

    /** Strips tags, resolves the few entities these files use, collapses spaces. */
    private static String text(String cell) {
        String s = TAG.matcher(cell).replaceAll("");
        s = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
                .replace(' ', ' ');
        return s.replaceAll("\\s+", " ").trim();
    }

    /** Chart position, or 0 when the cell is not a plain number. */
    private static int number(String cell) {
        if (cell.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < cell.length(); i++) {
            if (!Character.isDigit(cell.charAt(i))) {
                return 0;
            }
        }
        return Integer.parseInt(cell);
    }
}
