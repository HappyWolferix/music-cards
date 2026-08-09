package sk.musiccards.app;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import sk.musiccards.core.SpotifyLink;

/**
 * Starts playback of a track in the installed Spotify app via a deep-link
 * intent. With Spotify Premium and autoplay this starts the song directly;
 * no authentication or SDK is required.
 */
public final class SpotifyPlayer {

    private SpotifyPlayer() {
    }

    /** Returns false when neither the Spotify app nor a browser could handle it. */
    public static boolean play(Context context, String trackId) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(SpotifyLink.toUri(trackId)));
        intent.putExtra(Intent.EXTRA_REFERRER,
                Uri.parse("android-app://" + context.getPackageName()));
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            // Spotify app not installed - fall back to the web player.
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(SpotifyLink.toWebUrl(trackId)));
            try {
                context.startActivity(web);
                return true;
            } catch (ActivityNotFoundException e2) {
                return false;
            }
        }
    }
}
