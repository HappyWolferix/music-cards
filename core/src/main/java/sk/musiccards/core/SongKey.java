package sk.musiccards.core;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Builds the identity key used to tell whether two rows are the same song.
 *
 * Sources spell songs inconsistently (chart exports shout in caps and drop
 * diacritics, playlists do not), so the key folds case, diacritics and
 * punctuation: "Tanečnice" and "TANECNICE" collide on purpose.
 */
public final class SongKey {

    private SongKey() {
    }

    /** Key for one song; equal keys mean "already have it". */
    public static String of(String title, String artist) {
        return normalize(title) + "|" + normalize(artist);
    }

    /** Lowercase, strip diacritics and punctuation, collapse whitespace. */
    public static String normalize(String s) {
        String folded = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return folded.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
