package sk.musiccards.app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import sk.musiccards.core.SpotifyLink;

/**
 * Single-screen flow: big Scan button -> ZXing camera scanner -> parse the QR
 * payload into a Spotify track id -> hand off playback to the Spotify app.
 */
public class MainActivity extends AppCompatActivity {

    private TextView statusView;

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() == null) {
                    return; // user cancelled the scanner
                }
                handleScan(result.getContents());
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusView = findViewById(R.id.status);
        Button scanButton = findViewById(R.id.scan_button);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScan();
            }
        });
    }

    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt(getString(R.string.scan_prompt));
        options.setBeepEnabled(false);
        options.setOrientationLocked(true);
        scanLauncher.launch(options);
    }

    private void handleScan(String payload) {
        String trackId = SpotifyLink.parseTrackId(payload);
        if (trackId == null) {
            statusView.setText(getString(R.string.error_not_a_song_qr));
            return;
        }
        boolean started = SpotifyPlayer.play(this, trackId);
        statusView.setText(started
                ? getString(R.string.status_playing)
                : getString(R.string.error_spotify_missing));
    }
}
