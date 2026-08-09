package sk.musiccards.core;

/** One entry of the song catalog — mirrors one physical card. */
public final class Song {

    public final String title;
    public final String artist;
    public final int year;
    public final String trackId;

    public Song(String title, String artist, int year, String trackId) {
        this.title = title;
        this.artist = artist;
        this.year = year;
        this.trackId = trackId;
    }

    @Override
    public String toString() {
        return artist + " - " + title + " (" + year + ")";
    }
}
