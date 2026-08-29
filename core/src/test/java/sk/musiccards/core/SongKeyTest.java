package sk.musiccards.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class SongKeyTest {

    @Test
    public void foldsCaseAndDiacritics() {
        assertEquals(SongKey.of("Tanečnice", "Elán"), SongKey.of("TANECNICE", "ELAN"));
    }

    @Test
    public void foldsPunctuationAndSpacing() {
        assertEquals(SongKey.of("Nič sa nemení", "Kali & I.M.T. Smile"),
                SongKey.of("Nic  sa nemeni!", "KALI & I M T  SMILE"));
    }

    @Test
    public void differentSongsDoNotCollide() {
        assertNotEquals(SongKey.of("Stužková", "Elán"), SongKey.of("Amnestia", "Elán"));
    }

    @Test
    public void titleAndArtistAreNotInterchangeable() {
        assertNotEquals(SongKey.of("Elán", "Stužková"), SongKey.of("Stužková", "Elán"));
    }

    @Test
    public void trimsAndCollapsesWhitespace() {
        assertEquals("nic sa nemeni", SongKey.normalize("  Nič   sa\tnemení  "));
    }
}
