package sk.musiccards.cards;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import sk.musiccards.core.CandidateList;
import sk.musiccards.core.ChartExport;
import sk.musiccards.core.Song;
import sk.musiccards.core.SongCatalog;
import sk.musiccards.core.SongKey;

/**
 * Builds the single list of songs we want on cards but do not have yet.
 *
 * Everything merges into one file: SK charts, CZ charts, and hand-written
 * wishlist entries. The goal is one data/songs.csv, so a song wanted twice is
 * one candidate and becomes one card. Chart placings are kept only because they
 * make a useful running order — best-charting songs first, wishlist entries
 * after them — not because the origin matters.
 *
 * Chart rows carry no Spotify track id, so this never writes data/songs.csv: the
 * candidates are a shopping list. A song becomes a card by hand — paste its
 * Spotify link into the link column, then move the first four fields (which are
 * the catalog shape) into data/songs.csv.
 *
 * Inputs may be chart exports (hitparada*.xls) or a previously generated
 * candidate list — including this run's own output file, which is read fully
 * before anything is written. That makes the merged list the durable record:
 * the raw .xls downloads can be thrown away and a later run still keeps every
 * song. It also makes runs accumulate, so a song only ever leaves the list by
 * landing in the catalog (or by being deleted from the list by hand).
 *
 * Usage: ImportCharts &lt;catalog.csv&gt; &lt;candidates.csv&gt; &lt;input1&gt; [input2 ...]
 */
public final class ImportCharts {

    /** One song we still owe a card, merged across everywhere it was wanted. */
    private static final class Candidate {
        private final String title;
        private final String artist;
        private String year;
        private String link;
        private String publisher;
        private final Set<String> charts = new TreeSet<>();
        private final Set<String> weeks = new TreeSet<>();
        private int bestPosition = CandidateList.Placing.NO_POSITION;

        Candidate(String title, String artist) {
            this.title = title;
            this.artist = artist;
            this.year = "";
            this.link = "";
            this.publisher = "";
        }

        void seen(String chartSlug, String week, int position, String year, String link,
                String publisher) {
            bestPosition = Math.min(bestPosition, position);
            charts.add(chartSlug);
            if (!week.isEmpty()) {
                weeks.add(chartSlug + ":" + week + "#" + position);
            }
            if (this.year.isEmpty()) {
                this.year = year;
            }
            if (this.link.isEmpty()) {
                this.link = link;
            }
            if (this.publisher.isEmpty()) {
                this.publisher = publisher;
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: ImportCharts <catalog.csv> <candidates.csv> "
                    + "<input1> [input2 ...]   (inputs: hitparada*.xls or a candidate list)");
            System.exit(2);
        }

        Set<String> known = new HashSet<>();
        try (FileReader reader = new FileReader(args[0], StandardCharsets.UTF_8)) {
            for (Song s : SongCatalog.parse(reader)) {
                known.add(SongKey.of(s.title, s.artist));
            }
        }
        System.out.println(args[0] + ": " + known.size() + " songs already in the catalog");

        Map<String, Candidate> candidates = new LinkedHashMap<>();
        Set<String> sources = new LinkedHashSet<>();
        int rows = 0, inCatalog = 0;

        for (int i = 2; i < args.length; i++) {
            String path = args[i];
            String content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            List<String[]> read = looksLikeChartExport(content)
                    ? readChartExport(content, path, sources)
                    : readSongList(content, path, sources);
            for (String[] r : read) {
                rows++;
                String key = SongKey.of(r[0], r[1]);
                if (known.contains(key)) {
                    inCatalog++;
                    continue;
                }
                candidates.computeIfAbsent(key, k -> new Candidate(r[0], r[1]))
                        .seen(r[2], r[3], Integer.parseInt(r[4]), r[5], r[6], r[7]);
            }
        }

        List<Candidate> out = new ArrayList<>(candidates.values());
        out.sort(Comparator
                .comparingInt((Candidate c) -> c.bestPosition)
                .thenComparing(c -> c.artist, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(c -> c.title, String.CASE_INSENSITIVE_ORDER));

        try (PrintWriter w = new PrintWriter(
                Files.newBufferedWriter(Paths.get(args[1]), StandardCharsets.UTF_8))) {
            w.println("# Candidates — every song we want on a card and do not have yet.");
            w.println("# Generated by ImportCharts (make import-charts). One list: SK charts,");
            w.println("# CZ charts and wishlist entries all merged, best-charting first.");
            w.println("#");
            w.println("# To finish a song: paste its Spotify link into the link column, then");
            w.println("# move the first four fields into data/songs.csv and drop the line here.");
            w.println("# `make cards` validates every link, so a typo cannot reach a card.");
            w.println("#");
            w.println("# Sources:");
            for (String s : sources) {
                w.println("#   " + s);
            }
            w.println("#");
            w.println("# Format: title;artist;year;link;charts;best-position;weeks;publisher");
            w.println("# The first four columns are the data/songs.csv shape: fill in year and");
            w.println("# link, then copy those four fields across and delete the line here.");
            for (Candidate c : out) {
                w.println(clean(c.title) + ";" + clean(c.artist) + ";" + c.year + ";" + c.link + ";"
                        + String.join(" ", c.charts) + ";" + c.bestPosition + ";"
                        + String.join(" ", c.weeks) + ";" + clean(c.publisher));
            }
        }

        System.out.println();
        System.out.println("Read " + rows + " rows -> wrote " + out.size()
                + " combined candidates to " + args[1]);
        long linked = out.stream().filter(c -> !c.link.isEmpty()).count();
        System.out.println("  skipped " + inCatalog + " rows already in the catalog");
        System.out.println("  " + linked + " of " + out.size() + " have a link filled in and are "
                + "ready to move into data/songs.csv");
    }

    /** A candidate list or wishlist from disk, expanded into rows. */
    private static List<String[]> readSongList(String content, String path, Set<String> sources)
            throws IOException {
        List<CandidateList.Placing> placings = CandidateList.parse(new java.io.StringReader(content));
        List<String[]> rows = new ArrayList<>();
        Set<String> songs = new HashSet<>();
        int wishes = 0;
        for (CandidateList.Placing p : placings) {
            songs.add(SongKey.of(p.title, p.artist));
            if (p.isWish()) {
                wishes++;
            }
            rows.add(new String[] {p.title, p.artist, p.chart, p.week,
                    String.valueOf(p.position), p.year, p.link, p.publisher});
        }
        System.out.println(path + ": " + songs.size() + " songs"
                + (wishes > 0 ? " (" + wishes + " without chart data)" : ""));
        sources.add(path + " (" + songs.size() + " songs)");
        return rows;
    }

    private static boolean looksLikeChartExport(String content) {
        return content.regionMatches(true, 0, "<html", 0, 5) || content.contains("<table");
    }

    /** Chart export -> rows of {title, artist, chartSlug, week, position, publisher}. */
    private static List<String[]> readChartExport(String content, String path, Set<String> sources)
            throws IOException {
        List<ChartExport.Entry> entries = ChartExport.parse(new java.io.StringReader(content));
        List<String[]> rows = new ArrayList<>();
        if (entries.isEmpty()) {
            System.out.println(path + ": no chart rows — not an IFPI chart export?");
            return rows;
        }
        ChartExport.Entry first = entries.get(0);
        String slug = slug(first.chart);
        System.out.println(path + ": " + entries.size() + " chart rows ("
                + (first.chart.isEmpty() ? "unknown chart" : first.chart)
                + ", week " + first.week + ")");
        sources.add(slug + " week " + first.week + " (" + entries.size() + " rows)");
        for (ChartExport.Entry e : entries) {
            rows.add(new String[] {e.title, e.artist, slug, e.week,
                    String.valueOf(e.position), "", "", e.publisher});
        }
        return rows;
    }

    /** Short chart tag: "CZ - RADIO - TOP 50 CZ" -> "cz". */
    private static String slug(String chartName) {
        String normalized = SongKey.normalize(chartName);
        if (normalized.isEmpty()) {
            return "unknown";
        }
        String first = normalized.split(" ")[0];
        return first.length() == 2 ? first : normalized.replace(' ', '-');
    }

    /** Field separator is ';' and '#' starts a comment — keep them out of fields. */
    private static String clean(String s) {
        return s.replace(';', ',').replace('\n', ' ').replace("#", "").trim();
    }

    private ImportCharts() {
    }
}
