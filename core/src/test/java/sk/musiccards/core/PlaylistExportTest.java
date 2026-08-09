package sk.musiccards.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.Test;

public class PlaylistExportTest {

    private static final String HEADER = "﻿Track URI,Track Name,Album Name,"
            + "Artist Name(s),Release Date,Duration (ms),Popularity\n";
    private static final String ID = "5u5iANSXzVAsztBRXWkxvC";

    @Test
    public void parsesBasicRow() throws IOException {
        String csv = HEADER
                + "spotify:track:" + ID + ",\"22 dní\",\"Nemoderný chalan\",\"Miro Žbirka\",1984-01-01,222640,47\n";
        List<PlaylistExport.Entry> e = PlaylistExport.parse(new StringReader(csv));
        assertEquals(1, e.size());
        assertEquals(ID, e.get(0).trackId);
        assertEquals("22 dní", e.get(0).title);
        assertEquals("Miro Žbirka", e.get(0).artist);
        assertEquals(1984, e.get(0).year);
        assertFalse(e.get(0).suspiciousYear);
    }

    @Test
    public void takesFirstArtistAndYearOnlyDate() throws IOException {
        String csv = HEADER
                + "spotify:track:" + ID + ",\"Lean On\",\"Peace\",\"Major Lazer,MØ,DJ Snake\",2015,176561,74\n";
        List<PlaylistExport.Entry> e = PlaylistExport.parse(new StringReader(csv));
        assertEquals("Major Lazer", e.get(0).artist);
        assertEquals(2015, e.get(0).year);
    }

    @Test
    public void handlesQuotedCommaAndEscapedQuote() throws IOException {
        String csv = HEADER
                + "spotify:track:" + ID + ",\"Hello, \"\"World\"\"\",\"Album\",\"Artist\",1999-05-05,1,1\n";
        List<PlaylistExport.Entry> e = PlaylistExport.parse(new StringReader(csv));
        assertEquals("Hello, \"World\"", e.get(0).title);
    }

    @Test
    public void flagsCompilationAlbums() throws IOException {
        String csv = HEADER
                + "spotify:track:" + ID + ",\"Čardáš dvoch\",\"20 Naj\",\"Karol Duchoň\",2006,1,1\n";
        List<PlaylistExport.Entry> e = PlaylistExport.parse(new StringReader(csv));
        assertTrue(e.get(0).suspiciousYear);
    }

    @Test
    public void skipsNonTrackRows() throws IOException {
        String csv = HEADER
                + "spotify:local:xyz,\"Local file\",\"A\",\"B\",2000,1,1\n"
                + "spotify:track:" + ID + ",\"Real\",\"A\",\"B\",2000,1,1\n";
        List<PlaylistExport.Entry> e = PlaylistExport.parse(new StringReader(csv));
        assertEquals(1, e.size());
        assertEquals("Real", e.get(0).title);
    }

    @Test
    public void handlesNewlineInsideQuotedField() throws IOException {
        String csv = HEADER
                + "spotify:track:" + ID + ",\"Line1\nLine2\",\"Album\",\"Artist\",2001,1,1\n";
        List<PlaylistExport.Entry> e = PlaylistExport.parse(new StringReader(csv));
        assertEquals(1, e.size());
        assertEquals("Line1\nLine2", e.get(0).title);
    }
}
