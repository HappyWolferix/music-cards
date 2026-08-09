package sk.musiccards.cards;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import sk.musiccards.core.Song;
import sk.musiccards.core.SongCatalog;
import sk.musiccards.core.SpotifyLink;

/**
 * Generates printable card sheets from the song catalog:
 *   - one QR png per song (encodes the spotify:track: URI)
 *   - cards.html: print it double-sided (flip on long edge) to get physical
 *     cards with title/artist/year on the front and the QR on the back.
 *
 * Usage: GenerateCards <songs.csv> <output-dir>
 */
public final class GenerateCards {

    private static final int QR_SIZE = 400;
    private static final int CARDS_PER_ROW = 3;
    private static final int ROWS_PER_PAGE = 3;

    public static void main(String[] args) throws IOException, WriterException {
        if (args.length != 2) {
            System.err.println("usage: GenerateCards <songs.csv> <output-dir>");
            System.exit(2);
        }
        List<Song> songs;
        try (FileReader reader = new FileReader(args[0], StandardCharsets.UTF_8)) {
            songs = SongCatalog.parse(reader);
        }
        Path outDir = Paths.get(args[1]);
        Files.createDirectories(outDir.resolve("qr"));

        QRCodeWriter writer = new QRCodeWriter();
        for (Song song : songs) {
            BitMatrix matrix = writer.encode(SpotifyLink.toUri(song.trackId),
                    BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            MatrixToImageWriter.writeToPath(matrix, "PNG",
                    outDir.resolve("qr").resolve(song.trackId + ".png"));
        }
        writeHtml(songs, outDir);
        System.out.println("Generated " + songs.size() + " cards in " + outDir);
    }

    private static void writeHtml(List<Song> songs, Path outDir) throws IOException {
        int perPage = CARDS_PER_ROW * ROWS_PER_PAGE;
        try (PrintWriter out = new PrintWriter(
                Files.newBufferedWriter(outDir.resolve("cards.html"), StandardCharsets.UTF_8))) {
            out.println("<!doctype html><html><head><meta charset='utf-8'><title>Music Cards cards</title>");
            out.println("<style>");
            out.println("  @page { size: A4; margin: 10mm; }");
            out.println("  body { font-family: sans-serif; margin: 0; }");
            out.println("  .page { display: grid; grid-template-columns: repeat(" + CARDS_PER_ROW + ", 60mm);");
            out.println("          grid-auto-rows: 60mm; gap: 5mm; page-break-after: always; }");
            out.println("  .card { border: 1px dashed #999; display: flex; flex-direction: column;");
            out.println("          align-items: center; justify-content: center; text-align: center; padding: 4mm; box-sizing: border-box; }");
            out.println("  .card .year { font-size: 22pt; font-weight: bold; }");
            out.println("  .card .title { font-size: 12pt; font-weight: bold; margin-top: 3mm; }");
            out.println("  .card .artist { font-size: 10pt; color: #444; }");
            out.println("  .card img { width: 45mm; height: 45mm; }");
            out.println("  .back { direction: rtl; }"); // mirror column order so backs line up when flipped on long edge
            out.println("</style></head><body>");

            for (int start = 0; start < songs.size(); start += perPage) {
                int end = Math.min(start + perPage, songs.size());
                out.println("<div class='page'>");
                for (int i = start; i < end; i++) {
                    Song s = songs.get(i);
                    out.println("  <div class='card'><div class='year'>" + s.year + "</div>"
                            + "<div class='title'>" + escape(s.title) + "</div>"
                            + "<div class='artist'>" + escape(s.artist) + "</div></div>");
                }
                out.println("</div>");
                out.println("<div class='page back'>");
                for (int i = start; i < end; i++) {
                    Song s = songs.get(i);
                    out.println("  <div class='card'><img src='qr/" + s.trackId + ".png' alt='QR'></div>");
                }
                out.println("</div>");
            }
            out.println("</body></html>");
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private GenerateCards() {
    }
}
