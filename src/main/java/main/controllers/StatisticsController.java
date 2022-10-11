package main.controllers;

import main.apiResponses.Response;
import main.builders.SiteBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatisticsController {
    @GetMapping("/statistics")
    public Response statistics() {
        return SiteBuilder.getStatistics();
    }
}
