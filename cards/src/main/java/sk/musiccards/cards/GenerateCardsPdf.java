package sk.musiccards.cards;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import sk.musiccards.core.Song;
import sk.musiccards.core.SongCatalog;
import sk.musiccards.core.SpotifyLink;

/**
 * Renders the song catalog as a duplex-printable A4 PDF: alternating pages of
 * card fronts (title/artist/year) and card backs (QR code). Back pages mirror
 * the column order, so printing double-sided with "flip on long edge" puts
 * each QR exactly behind its song card. Cut along the grid lines.
 *
 * Usage: GenerateCardsPdf <songs.csv> <output.pdf>
 */
public final class GenerateCardsPdf {

    // 4 x 5 grid = 20 cards per A4 page
    private static final int COLS = 4;
    private static final int ROWS = 5;
    private static final float MARGIN = mm(8);

    private static final float PAGE_W = PDRectangle.A4.getWidth();
    private static final float PAGE_H = PDRectangle.A4.getHeight();
    private static final float CARD_W = (PAGE_W - 2 * MARGIN) / COLS;
    private static final float CARD_H = (PAGE_H - 2 * MARGIN) / ROWS;
    private static final float PAD = mm(3);

    // Pastel card backgrounds; kept light enough for black text to stay readable.
    private static final Color[] CARD_COLORS = {
            new Color(255, 205, 205), // red
            new Color(255, 224, 178), // orange
            new Color(255, 249, 196), // yellow
            new Color(197, 225, 255), // blue
            new Color(200, 230, 201), // green
            new Color(225, 190, 231), // purple
    };

    private static final Random RANDOM = new Random();

    private static float mm(double v) {
        return (float) (v * 72.0 / 25.4);
    }

    public static void main(String[] args) throws IOException, WriterException {
        if (args.length != 2) {
            System.err.println("usage: GenerateCardsPdf <songs.csv> <output.pdf>");
            System.exit(2);
        }
        List<Song> songs;
        try (FileReader reader = new FileReader(args[0], StandardCharsets.UTF_8)) {
            songs = SongCatalog.parse(reader);
        }
        Path out = Paths.get(args[1]);
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }

        try (PDDocument doc = new PDDocument()) {
            PDFont regular = loadFont(doc, "/fonts/DejaVuSans.ttf");
            PDFont bold = loadFont(doc, "/fonts/DejaVuSans-Bold.ttf");

            int perPage = COLS * ROWS;
            for (int start = 0; start < songs.size(); start += perPage) {
                List<Song> page = songs.subList(start, Math.min(start + perPage, songs.size()));
                addFrontPage(doc, page, regular, bold);
                addBackPage(doc, page);
            }
            doc.save(out.toFile());
        }
        System.out.println("Wrote " + songs.size() + " cards ("
                + ((songs.size() + COLS * ROWS - 1) / (COLS * ROWS)) + " sheets) to " + out);
        System.out.println("Print: A4, actual size (100%), double-sided, FLIP ON LONG EDGE.");
    }

    private static PDFont loadFont(PDDocument doc, String resource) throws IOException {
        try (InputStream in = GenerateCardsPdf.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("bundled font missing: " + resource);
            }
            return PDType0Font.load(doc, in);
        }
    }

    /** Card slot rectangle; col/row are grid positions, origin bottom-left (PDF space). */
    private static float slotX(int col) {
        return MARGIN + col * CARD_W;
    }

    private static float slotY(int row) {
        return PAGE_H - MARGIN - (row + 1) * CARD_H;
    }

    private static void addFrontPage(PDDocument doc, List<Song> page,
                                     PDFont regular, PDFont bold) throws IOException {
        PDPage pdfPage = new PDPage(PDRectangle.A4);
        doc.addPage(pdfPage);
        try (PDPageContentStream cs = new PDPageContentStream(doc, pdfPage)) {
            for (int i = 0; i < page.size(); i++) {
                float x = slotX(i % COLS);
                float y = slotY(i / COLS);
                cs.setNonStrokingColor(CARD_COLORS[RANDOM.nextInt(CARD_COLORS.length)]);
                cs.addRect(x, y, CARD_W, CARD_H);
                cs.fill();
            }
            cs.setNonStrokingColor(Color.BLACK);
            drawGrid(cs);
            for (int i = 0; i < page.size(); i++) {
                Song s = page.get(i);
                float x = slotX(i % COLS);
                float y = slotY(i / COLS);
                float cx = x + CARD_W / 2;

                // year — the star of the card
                drawCentered(cs, bold, 26, String.valueOf(s.year), cx, y + CARD_H - mm(14));

                // title: up to two shrink-to-fit lines
                List<String> titleLines = wrap(bold, 11, s.title, CARD_W - 2 * PAD);
                float ty = y + CARD_H - mm(22);
                for (String line : titleLines) {
                    float size = fitSize(bold, 11, line, CARD_W - 2 * PAD);
                    drawCentered(cs, bold, size, line, cx, ty);
                    ty -= mm(5);
                }

                float artistSize = fitSize(regular, 9, s.artist, CARD_W - 2 * PAD);
                drawCentered(cs, regular, artistSize, s.artist, cx, y + mm(6));
            }
        }
    }

    private static void addBackPage(PDDocument doc, List<Song> page)
            throws IOException, WriterException {
        PDPage pdfPage = new PDPage(PDRectangle.A4);
        doc.addPage(pdfPage);
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 0);
        try (PDPageContentStream cs = new PDPageContentStream(doc, pdfPage)) {
            drawGrid(cs);
            for (int i = 0; i < page.size(); i++) {
                Song s = page.get(i);
                int col = i % COLS;
                int row = i / COLS;
                // long-edge duplex on portrait pages mirrors horizontally
                int backCol = COLS - 1 - col;
                float x = slotX(backCol);
                float y = slotY(row);

                BitMatrix matrix = writer.encode(SpotifyLink.toUri(s.trackId),
                        BarcodeFormat.QR_CODE, 300, 300, hints);
                PDImageXObject img = LosslessFactory.createFromImage(doc,
                        MatrixToImageWriter.toBufferedImage(matrix));
                float qrSize = Math.min(CARD_W, CARD_H) - 2 * PAD - mm(4);
                cs.drawImage(img,
                        x + (CARD_W - qrSize) / 2,
                        y + (CARD_H - qrSize) / 2,
                        qrSize, qrSize);
            }
        }
    }

    /** Light cut lines around every card slot. */
    private static void drawGrid(PDPageContentStream cs) throws IOException {
        cs.setStrokingColor(180, 180, 180);
        cs.setLineWidth(0.4f);
        for (int c = 0; c <= COLS; c++) {
            float x = MARGIN + c * CARD_W;
            cs.moveTo(x, MARGIN);
            cs.lineTo(x, PAGE_H - MARGIN);
        }
        for (int r = 0; r <= ROWS; r++) {
            float y = MARGIN + r * CARD_H;
            cs.moveTo(MARGIN, y);
            cs.lineTo(PAGE_W - MARGIN, y);
        }
        cs.stroke();
    }

    private static void drawCentered(PDPageContentStream cs, PDFont font, float size,
                                     String text, float centerX, float baselineY) throws IOException {
        float w = font.getStringWidth(text) / 1000f * size;
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(centerX - w / 2, baselineY);
        cs.showText(text);
        cs.endText();
    }

    /** Shrinks the font size until the text fits maxWidth (floor 5pt). */
    private static float fitSize(PDFont font, float size, String text, float maxWidth)
            throws IOException {
        float s = size;
        while (s > 5 && font.getStringWidth(text) / 1000f * s > maxWidth) {
            s -= 0.5f;
        }
        return s;
    }

    /** Splits into at most two lines on a space near the middle if too wide. */
    private static List<String> wrap(PDFont font, float size, String text, float maxWidth)
            throws IOException {
        List<String> lines = new ArrayList<>();
        if (font.getStringWidth(text) / 1000f * size <= maxWidth || text.indexOf(' ') < 0) {
            lines.add(text);
            return lines;
        }
        int mid = text.length() / 2;
        int left = text.lastIndexOf(' ', mid);
        int right = text.indexOf(' ', mid);
        int split = (left < 0) ? right
                : (right < 0) ? left
                : (mid - left <= right - mid) ? left : right;
        lines.add(text.substring(0, split));
        lines.add(text.substring(split + 1));
        return lines;
    }

    private GenerateCardsPdf() {
    }
}
