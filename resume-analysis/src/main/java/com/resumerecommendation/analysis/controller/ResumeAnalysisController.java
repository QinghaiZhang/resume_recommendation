package com.resumerecommendation.analysis.controller;

import com.resumerecommendation.analysis.service.ResumeParserService;
import com.resumerecommendation.common.entity.Resume;
import com.resumerecommendation.common.service.AIService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/resume")
@RequiredArgsConstructor
public class ResumeAnalysisController {

    private final ResumeParserService resumeParserService;
    private final AIService aiService;

    @PostMapping("/analyze")
    public ResponseEntity<Resume> analyzeResume(@RequestParam("file") MultipartFile file) throws IOException {
        Resume resume = resumeParserService.parseResume(file);
        return ResponseEntity.ok(resume);
    }

    @PostMapping("/{resumeId}/improve")
    public ResponseEntity<String> getImprovementSuggestions(@PathVariable Long resumeId,
                                                          @RequestBody Resume resume) {
        String suggestions = aiService.generateImprovementSuggestions(resume);
        return ResponseEntity.ok(suggestions);
    }

    @PostMapping("/{resumeId}/skills")
    public ResponseEntity<Map<String, Integer>> assessSkills(@PathVariable Long resumeId,
                                                           @RequestBody Resume resume) {
        Map<String, Integer> skillLevels = aiService.assessSkillLevels(
                resume.getRawContent(),
                resume.getSkills()
        );
        return ResponseEntity.ok(skillLevels);
    }

    @PostMapping("/{resumeId}/analyze-content")
    public ResponseEntity<Map<String, Object>> analyzeContent(@PathVariable Long resumeId,
                                                            @RequestBody Resume resume) {
        Map<String, Object> analysis = aiService.analyzeResume(resume);
        return ResponseEntity.ok(analysis);
    }
} 