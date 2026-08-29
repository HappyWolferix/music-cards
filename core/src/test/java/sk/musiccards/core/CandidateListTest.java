package sk.musiccards.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.Test;

public class CandidateListTest {

    private static List<CandidateList.Placing> parse(String csv) throws IOException {
        return CandidateList.parse(new StringReader(csv));
    }

    @Test
    public void readsWishRow() throws IOException {
        List<CandidateList.Placing> p = parse("Slavíci z Madridu;Waldemar Matuška;1968;TODO\n");
        assertEquals(1, p.size());
        assertTrue(p.get(0).isWish());
        assertEquals("Slavíci z Madridu", p.get(0).title);
        assertEquals("1968", p.get(0).year);
        assertEquals(CandidateList.Placing.NO_POSITION, p.get(0).position);
    }

    @Test
    public void readsChartRow() throws IOException {
        List<CandidateList.Placing> p =
                parse("Nič sa nemení;KALI;;;sk;1;sk:202351,52#1;RUKA HORE\n");
        assertEquals(1, p.size());
        assertFalse(p.get(0).isWish());
        assertEquals("sk", p.get(0).chart);
        assertEquals("202351,52", p.get(0).week);
        assertEquals(1, p.get(0).position);
        assertEquals("RUKA HORE", p.get(0).publisher);
    }

    @Test
    public void expandsEveryWeekIntoItsOwnPlacing() throws IOException {
        List<CandidateList.Placing> p = parse(
                "Půlnoční;NECKÁŘ;;;cz sk;2;cz:201351#2 sk:201651,52#14;SUPRAPHON\n");
        assertEquals(2, p.size());
        assertEquals("cz", p.get(0).chart);
        assertEquals(2, p.get(0).position);
        assertEquals("sk", p.get(1).chart);
        assertEquals(14, p.get(1).position);
    }

    /**
     * Regression: the wishlist flag was once inferred from the position column,
     * which is never empty — a re-read then dropped every wishlist-only song.
     */
    @Test
    public void keepsWishlistFlagOnReread() throws IOException {
        List<CandidateList.Placing> p = parse("Kokosy;Yo Yo Band;1993;;wishlist;999;;\n");
        assertEquals(1, p.size());
        assertTrue(p.get(0).isWish());
        assertEquals("1993", p.get(0).year);
    }

    /** A song both charted and wished for must round-trip both reasons. */
    @Test
    public void keepsWishlistFlagAlongsideChartPlacings() throws IOException {
        List<CandidateList.Placing> p =
                parse("Anděl;MIRAI;2016;;cz wishlist;49;cz:202052,53#49;UNIVERSAL\n");
        assertEquals(2, p.size());
        assertTrue(p.get(0).isWish());
        assertEquals("cz", p.get(1).chart);
        assertEquals(49, p.get(1).position);
    }

    @Test
    public void keepsAHandFilledLink() throws IOException {
        List<CandidateList.Placing> p =
                parse("Kokosy;Yo Yo Band;1993;spotify:track:5u5iANSXzVAsztBRXWkxvC;wishlist;999;;\n");
        assertEquals("spotify:track:5u5iANSXzVAsztBRXWkxvC", p.get(0).link);
    }

    @Test
    public void normalizesAPastedSpotifyUrl() throws IOException {
        List<CandidateList.Placing> p = parse(
                "A;B;1993;https://open.spotify.com/track/5u5iANSXzVAsztBRXWkxvC;wishlist;999;;\n");
        assertEquals("spotify:track:5u5iANSXzVAsztBRXWkxvC", p.get(0).link);
    }

    @Test
    public void treatsAnUnusableLinkAsNotFilledIn() throws IOException {
        assertEquals("", parse("A;B;1993;not-a-link;wishlist;999;;\n").get(0).link);
        assertEquals("", parse("A;B;1993;TODO\n").get(0).link);
    }

    @Test
    public void carriesTheLinkOntoEveryPlacing() throws IOException {
        List<CandidateList.Placing> p = parse(
                "A;B;;spotify:track:5u5iANSXzVAsztBRXWkxvC;cz sk;2;cz:201351#2 sk:201651,52#14;P\n");
        assertEquals(2, p.size());
        assertEquals("spotify:track:5u5iANSXzVAsztBRXWkxvC", p.get(1).link);
    }

    @Test
    public void stillReadsOlderRowsWithoutALinkColumn() throws IOException {
        List<CandidateList.Placing> p = parse("A;B;;cz;1;cz:202631#1;P\n");
        assertEquals(1, p.size());
        assertEquals("", p.get(0).link);
        assertEquals("cz", p.get(0).chart);
    }

    @Test
    public void skipsCommentsAndBlanks() throws IOException {
        assertTrue(parse("# a comment\n\n   \n").isEmpty());
    }

    @Test
    public void dropsUnusableYears() throws IOException {
        assertEquals("", parse("A;B;TODO;TODO\n").get(0).year);
        assertEquals("", parse("A;B;1968-01-01;TODO\n").get(0).year);
    }

    @Test
    public void ignoresUnparsableWeekTokens() throws IOException {
        assertTrue(parse("A;B;;;cz;1;garbage cz:x#y;P\n").isEmpty());
    }

    @Test(expected = CandidateList.FormatException.class)
    public void rejectsUnknownColumnCount() throws IOException {
        parse("A;B;C\n");
    }
}
