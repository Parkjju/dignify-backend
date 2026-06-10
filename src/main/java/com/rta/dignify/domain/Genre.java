package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "genres")
@Entity
@Getter
public class Genre extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "genre_id")
    private Long id;

    @Column(name = "genre_name_en", nullable = false, unique = true)
    private String genreNameEn;

    @Column(name = "genre_name_ko", nullable = false, unique = true)
    private String genreNameKo;
}
