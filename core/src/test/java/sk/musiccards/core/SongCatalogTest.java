package sk.musiccards.core;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.Test;

public class SongCatalogTest {

    private static final String ID = "6rqhFgbbKwnb9MLmUQDhG6";

    @Test
    public void parsesValidCsv() throws IOException {
        String csv = "# comment\n"
                + "\n"
                + "Voda co ma drzi nad vodou;Elan;1988;https://open.spotify.com/track/" + ID + "?si=x\n"
                + "Neznama;Peha;2005;spotify:track:" + ID + "\n";
        List<Song> songs = SongCatalog.parse(new StringReader(csv));
        assertEquals(2, songs.size());
        assertEquals("Voda co ma drzi nad vodou", songs.get(0).title);
        assertEquals("Elan", songs.get(0).artist);
        assertEquals(1988, songs.get(0).year);
        assertEquals(ID, songs.get(0).trackId);
    }

    @Test(expected = SongCatalog.FormatException.class)
    public void rejectsWrongColumnCount() throws IOException {
        SongCatalog.parse(new StringReader("only;three;columns\n"));
    }

    @Test(expected = SongCatalog.FormatException.class)
    public void rejectsBadYear() throws IOException {
        SongCatalog.parse(new StringReader("t;a;not-a-year;spotify:track:" + ID + "\n"));
    }

    @Test(expected = SongCatalog.FormatException.class)
    public void rejectsBadLink() throws IOException {
        SongCatalog.parse(new StringReader("t;a;1999;https://example.com/x\n"));
    }
}
