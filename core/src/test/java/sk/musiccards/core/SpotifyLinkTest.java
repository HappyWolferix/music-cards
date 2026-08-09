package sk.musiccards.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SpotifyLinkTest {

    private static final String ID = "6rqhFgbbKwnb9MLmUQDhG6";

    @Test
    public void parsesSpotifyUri() {
        assertEquals(ID, SpotifyLink.parseTrackId("spotify:track:" + ID));
    }

    @Test
    public void parsesWebUrl() {
        assertEquals(ID, SpotifyLink.parseTrackId("https://open.spotify.com/track/" + ID));
    }

    @Test
    public void parsesWebUrlWithQuery() {
        assertEquals(ID, SpotifyLink.parseTrackId(
                "https://open.spotify.com/track/" + ID + "?si=abc123&utm_source=share"));
    }

    @Test
    public void parsesIntlLocaleUrl() {
        assertEquals(ID, SpotifyLink.parseTrackId("https://open.spotify.com/intl-sk/track/" + ID));
    }

    @Test
    public void parsesBareId() {
        assertEquals(ID, SpotifyLink.parseTrackId(ID));
    }

    @Test
    public void trimsWhitespace() {
        assertEquals(ID, SpotifyLink.parseTrackId("  spotify:track:" + ID + "\n"));
    }

    @Test
    public void rejectsNonTrackUri() {
        assertNull(SpotifyLink.parseTrackId("spotify:album:" + ID));
        assertNull(SpotifyLink.parseTrackId("https://open.spotify.com/album/" + ID));
    }

    @Test
    public void rejectsForeignHost() {
        assertNull(SpotifyLink.parseTrackId("https://example.com/track/" + ID));
    }

    @Test
    public void rejectsMalformedId() {
        assertNull(SpotifyLink.parseTrackId("spotify:track:tooShort"));
        assertNull(SpotifyLink.parseTrackId("spotify:track:" + ID.substring(0, 21) + "!"));
        assertNull(SpotifyLink.parseTrackId(""));
        assertNull(SpotifyLink.parseTrackId(null));
        assertNull(SpotifyLink.parseTrackId("random text"));
    }

    @Test
    public void roundTripsUriAndWebUrl() {
        assertEquals("spotify:track:" + ID, SpotifyLink.toUri(ID));
        assertEquals(ID, SpotifyLink.parseTrackId(SpotifyLink.toWebUrl(ID)));
    }
}
