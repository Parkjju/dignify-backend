package com.rta.dignify.controller;

import com.rta.dignify.dto.genre.GenreListResponse;
import com.rta.dignify.service.GenreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/genres")
@RestController
public class GenreController {

    private final GenreService genreService;

    @GetMapping
    public GenreListResponse getGenreList() {
        return genreService.getGenreList();
    }
}
