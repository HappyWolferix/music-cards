package sk.musiccards.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.Test;

public class ChartExportTest {

    private static final String HEAD = "<html>\n<head><meta charset=\"UTF-8\"></head>\n<body>\n"
            + "ČNS IFPI<br><b>SK - RADIO - TOP 50 SK</b><br><b>Týden 202351,52</b><br>"
            + "<table border=\"1\">\n"
            + "<tr><th><b>&nbsp;&nbsp;TT&nbsp;</b></th><th><b>-1T</b></th><th><b>-2T</b></th>"
            + "<th><b>Interpret</b></th><th><b>Titul</b></th><th><b>Vyd.</b></th><tr>\n";
    private static final String FOOT = "</table></body></html>\n";

    private static String row(String pos, String artist, String title, String publisher) {
        return "<td align=right>" + pos + "</td><td align=right>1</td><td align=right>1</td>"
                + "<td align=left>" + artist + "</td><td align=left>" + title + "</td>"
                + "<td align=left>" + publisher + "</td><tr>\n";
    }

    @Test
    public void parsesRowsAndWeek() throws IOException {
        String html = HEAD + row("1", "KALI &amp; I.M.T. SMILE", "Nič sa nemení", "RADO") + FOOT;
        List<ChartExport.Entry> e = ChartExport.parse(new StringReader(html));
        assertEquals(1, e.size());
        assertEquals(1, e.get(0).position);
        assertEquals("KALI & I.M.T. SMILE", e.get(0).artist);
        assertEquals("Nič sa nemení", e.get(0).title);
        assertEquals("RADO", e.get(0).publisher);
        assertEquals("202351,52", e.get(0).week);
    }

    @Test
    public void readsChartName() throws IOException {
        String html = HEAD + row("1", "A", "Song", "X") + FOOT;
        assertEquals("SK - RADIO - TOP 50 SK",
                ChartExport.parse(new StringReader(html)).get(0).chart);
    }

    @Test
    public void readsCzechChartName() throws IOException {
        String html = HEAD.replace("SK - RADIO - TOP 50 SK", "CZ - RADIO - TOP 50 CZ")
                + row("1", "LUCIE", "Medvídek", "X") + FOOT;
        assertEquals("CZ - RADIO - TOP 50 CZ",
                ChartExport.parse(new StringReader(html)).get(0).chart);
    }

    @Test
    public void emptyChartNameWhenHeaderMissing() throws IOException {
        String html = "<html><body><table>\n<tr>" + row("1", "A", "Song", "X") + FOOT;
        assertEquals("", ChartExport.parse(new StringReader(html)).get(0).chart);
    }

    @Test
    public void skipsHeaderRow() throws IOException {
        String html = HEAD + row("2", "HEX", "Pridaj sa k nám", "CL") + FOOT;
        List<ChartExport.Entry> e = ChartExport.parse(new StringReader(html));
        assertEquals(1, e.size());
        assertEquals("HEX", e.get(0).artist);
    }

    @Test
    public void keepsChartOrderAndPositions() throws IOException {
        String html = HEAD
                + row("1", "A", "First", "X")
                + row("2", "B", "Second", "X")
                + row("10", "C", "Tenth", "X")
                + FOOT;
        List<ChartExport.Entry> e = ChartExport.parse(new StringReader(html));
        assertEquals(3, e.size());
        assertEquals(10, e.get(2).position);
        assertEquals("Tenth", e.get(2).title);
    }

    @Test
    public void skipsRowsWithoutPositionOrSong() throws IOException {
        String html = HEAD
                + row("-", "A", "Unranked", "X")
                + row("3", "", "", "X")
                + row("4", "D", "Kept", "X")
                + FOOT;
        List<ChartExport.Entry> e = ChartExport.parse(new StringReader(html));
        assertEquals(1, e.size());
        assertEquals("Kept", e.get(0).title);
    }

    @Test
    public void toleratesMissingPublisherColumn() throws IOException {
        String html = HEAD + "<td align=right>5</td><td>1</td><td>1</td>"
                + "<td>ELÁN</td><td>Stužková</td><tr>\n" + FOOT;
        List<ChartExport.Entry> e = ChartExport.parse(new StringReader(html));
        assertEquals(1, e.size());
        assertEquals("", e.get(0).publisher);
    }

    @Test
    public void emptyWeekWhenHeaderMissing() throws IOException {
        String html = "<html><body><table>\n<tr>"
                + row("1", "A", "Song", "X") + FOOT;
        List<ChartExport.Entry> e = ChartExport.parse(new StringReader(html));
        assertEquals("", e.get(0).week);
    }

    @Test
    public void emptyFileYieldsNoEntries() throws IOException {
        assertTrue(ChartExport.parse(new StringReader("")).isEmpty());
    }
}
