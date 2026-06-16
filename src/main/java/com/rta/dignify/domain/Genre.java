package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(name = "genres")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Genre extends BaseTimeEntity {

    @Id
    @Column(name = "genre_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "genre_name_en", nullable = false, unique = true)
    private String genreNameEn;

    @Column(name = "genre_name_ko", nullable = false, unique = true)
    private String genreNameKo;

    private Genre(String genreNameEn, String genreNameKo) {
        this.genreNameEn = genreNameEn;
        this.genreNameKo = genreNameKo;
    }

    public static Genre create(String genreNameEn, String genreNameKo) {
        return new Genre(genreNameEn, genreNameKo);
    }
}
