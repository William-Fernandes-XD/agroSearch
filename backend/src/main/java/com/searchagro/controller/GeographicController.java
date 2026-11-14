package com.searchagro.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.searchagro.model.GeographicLocation;
import com.searchagro.service.NominatimService;

@RestController
@RequestMapping({"/api/geographic", "/api/nominatim"})
public class GeographicController {

    private final NominatimService nominatimService;

    public GeographicController(NominatimService nominatimService) {
        this.nominatimService = nominatimService;
    }

    @GetMapping("/search")
    public List<GeographicLocation> search(@RequestParam("q") String query) {
        return nominatimService.search(query);
    }
}
