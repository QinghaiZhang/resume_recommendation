package com.resumerecommendation.crawler.controller;

import com.resumerecommendation.common.entity.JobPosition;
import com.resumerecommendation.crawler.service.JobCrawlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/crawler")
@RequiredArgsConstructor
public class JobCrawlerController {

    private final JobCrawlerService jobCrawlerService;

    @PostMapping("/start/{source}")
    public ResponseEntity<String> startCrawling(@PathVariable String source) {
        jobCrawlerService.startCrawling(source);
        return ResponseEntity.ok("Crawler started for source: " + source);
    }

    @PostMapping("/stop")
    public ResponseEntity<String> stopCrawling() {
        jobCrawlerService.stopCrawling();
        return ResponseEntity.ok("Crawler stopped");
    }

    @GetMapping("/status")
    public ResponseEntity<String> getCrawlerStatus() {
        return ResponseEntity.ok(jobCrawlerService.getCrawlerStatus());
    }

    @GetMapping("/search")
    public ResponseEntity<List<JobPosition>> searchJobs(
            @RequestParam String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ResponseEntity.ok(jobCrawlerService.searchJobs(keyword, city, pageSize));
    }

    @PostMapping("/update-expired")
    public ResponseEntity<String> updateExpiredJobs() {
        jobCrawlerService.updateExpiredJobs();
        return ResponseEntity.ok("Expired jobs update started");
    }

    @PostMapping("/clean-invalid")
    public ResponseEntity<String> cleanInvalidJobs() {
        jobCrawlerService.cleanInvalidJobs();
        return ResponseEntity.ok("Invalid jobs cleanup started");
    }
} 