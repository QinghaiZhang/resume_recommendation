package com.resumerecommendation.matching.controller;

import com.resumerecommendation.common.entity.JobPosition;
import com.resumerecommendation.common.entity.MatchResult;
import com.resumerecommendation.common.entity.Resume;
import com.resumerecommendation.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/matching")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;

    @PostMapping("/resume/{resumeId}/jobs")
    public ResponseEntity<List<MatchResult>> matchJobsForResume(
            @PathVariable Long resumeId,
            @RequestBody Resume resume,
            @RequestParam(defaultValue = "10") Integer limit) {
        return ResponseEntity.ok(matchingService.matchJobsForResume(resume, limit));
    }

    @PostMapping("/job/{jobId}/resumes")
    public ResponseEntity<List<MatchResult>> matchResumesForJob(
            @PathVariable Long jobId,
            @RequestBody JobPosition jobPosition,
            @RequestParam(defaultValue = "10") Integer limit) {
        return ResponseEntity.ok(matchingService.matchResumesForJob(jobPosition, limit));
    }

    @PostMapping("/calculate")
    public ResponseEntity<MatchResult> calculateMatch(
            @RequestParam Long resumeId,
            @RequestParam Long jobId,
            @RequestBody MatchCalculationRequest request) {
        return ResponseEntity.ok(matchingService.calculateMatch(request.getResume(), request.getJobPosition()));
    }

    @GetMapping("/resume/{resumeId}/recommendations")
    public ResponseEntity<List<JobPosition>> getRecommendedJobs(
            @PathVariable Long resumeId,
            @RequestBody Resume resume,
            @RequestParam(defaultValue = "10") Integer limit) {
        return ResponseEntity.ok(matchingService.getRecommendedJobs(resume, limit));
    }

    @GetMapping("/job/{jobId}/recommendations")
    public ResponseEntity<List<Resume>> getRecommendedResumes(
            @PathVariable Long jobId,
            @RequestBody JobPosition jobPosition,
            @RequestParam(defaultValue = "10") Integer limit) {
        return ResponseEntity.ok(matchingService.getRecommendedResumes(jobPosition, limit));
    }

    @PostMapping("/feedback")
    public ResponseEntity<Void> provideFeedback(
            @RequestParam Long resumeId,
            @RequestParam Long jobId,
            @RequestParam Boolean isRelevant) {
        matchingService.updateRecommendationModel(resumeId, jobId, isRelevant);
        return ResponseEntity.ok().build();
    }
}

class MatchCalculationRequest {
    private Resume resume;
    private JobPosition jobPosition;

    // Getters and setters
    public Resume getResume() {
        return resume;
    }

    public void setResume(Resume resume) {
        this.resume = resume;
    }

    public JobPosition getJobPosition() {
        return jobPosition;
    }

    public void setJobPosition(JobPosition jobPosition) {
        this.jobPosition = jobPosition;
    }
} 