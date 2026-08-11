package com.rta.dignify.domain;

import com.rta.dignify.dto.itunes.ItunesItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackJunkFilterTest {

    private ItunesItem item(String artist, String collection, String track) {
        return new ItunesItem("track", 1L, 2L, artist, collection, track,
                "art", "preview", "view", null, "Pop", "2020-01-01T00:00:00Z", "US");
    }

    @Test
    void junkPatterns_areFilteredOut() {
        assertThat(Track.from(item("Karaoke Star", "Hits", "Song"), null)).isEmpty();
        assertThat(Track.from(item("Artist", "A Tribute to Queen", "Song"), null)).isEmpty();
        assertThat(Track.from(item("Artist", "Album", "Bohemian Rhapsody (Originally Performed by Queen)"), null)).isEmpty();
        assertThat(Track.from(item("Artist", "Album", "Song (Made Famous by X)"), null)).isEmpty();
        assertThat(Track.from(item("Artist", "In the Style of Adele", "Song"), null)).isEmpty();
        assertThat(Track.from(item("Nature Sounds", "Relaxing Rain Sounds", "Thunder"), null)).isEmpty();
        assertThat(Track.from(item("Rockabye Baby!", "Lullaby Versions of Metallica", "Enter Sandman"), null)).isEmpty();
        assertThat(Track.from(item("8-Bit Arcade", "Chiptune", "Song"), null)).isEmpty();
        assertThat(Track.from(item("Yoga Meditation", "Zen", "Calm"), null)).isEmpty();
        assertThat(Track.from(item("Workout Music", "150 BPM Running", "Pump"), null)).isEmpty();
    }

    @Test
    void realTracks_arePreserved() {
        assertThat(Track.from(item("Queen", "A Night at the Opera", "Bohemian Rhapsody"), null)).isPresent();
        assertThat(Track.from(item("Radiohead", "OK Computer", "Karma Police"), null)).isPresent();
    }

    /// artistId는 저장될 때 말고는 쓰이는 데가 없어서, 관통이 끊겨도 아무 화면도 안 깨진다.
    /// 조용히 null로만 쌓이는 걸 막으려면 여기서 잡아야 한다.
    @Test
    void artistId_isCarriedFromItunes() {
        Track track = Track.from(item("Queen", "A Night at the Opera", "Bohemian Rhapsody"), null).orElseThrow();
        assertThat(track.getArtistId()).isEqualTo(2L);
    }
}
