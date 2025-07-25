package com.resumerecommendation.analysis.controller;

import com.resumerecommendation.analysis.service.ResumeParserService;
import com.resumerecommendation.common.entity.Resume;
import com.resumerecommendation.common.service.AIService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/resume")
@RequiredArgsConstructor
@Tag(name = "简历分析", description = "提供简历解析、技能评估和改进建议等相关API")
public class ResumeAnalysisController {

    private final ResumeParserService resumeParserService;
    private final AIService aiService;

    @PostMapping("/analyze")
    @Operation(summary = "分析简历文件", description = "上传并分析简历文件(PDF或Word)，提取其中的关键信息")
    @ApiResponse(responseCode = "200", description = "成功解析简历", 
                 content = @Content(mediaType = "application/json", 
                          schema = @Schema(implementation = Resume.class)))
    public ResponseEntity<Resume> analyzeResume(
            @Parameter(description = "简历文件(PDF或Word格式)") 
            @RequestParam("file") MultipartFile file) throws IOException {
        Resume resume = resumeParserService.parseResume(file);
        return ResponseEntity.ok(resume);
    }

    @PostMapping("/{resumeId}/improve")
    @Operation(summary = "获取简历改进建议", description = "基于AI分析，为指定简历提供改进建议")
    @ApiResponse(responseCode = "200", description = "成功生成改进建议")
    public ResponseEntity<String> getImprovementSuggestions(
            @Parameter(description = "简历ID") @PathVariable Long resumeId,
            @Parameter(description = "简历对象") @RequestBody Resume resume) {
        String suggestions = aiService.generateImprovementSuggestions(resume);
        return ResponseEntity.ok(suggestions);
    }

    @PostMapping("/{resumeId}/skills")
    @Operation(summary = "评估技能等级", description = "分析简历中的技能并评估其等级")
    @ApiResponse(responseCode = "200", description = "成功评估技能等级", 
                 content = @Content(mediaType = "application/json"))
    public ResponseEntity<Map<String, Integer>> assessSkills(
            @Parameter(description = "简历ID") @PathVariable Long resumeId,
            @Parameter(description = "简历对象") @RequestBody Resume resume) {
        Map<String, Integer> skillLevels = aiService.assessSkillLevels(
                resume.getRawContent(),
                resume.getSkills()
        );
        return ResponseEntity.ok(skillLevels);
    }

    @PostMapping("/{resumeId}/analyze-content")
    @Operation(summary = "分析简历内容", description = "全面分析简历内容，包括优势、不足和改进建议")
    @ApiResponse(responseCode = "200", description = "成功分析简历内容", 
                 content = @Content(mediaType = "application/json"))
    public ResponseEntity<Map<String, Object>> analyzeContent(
            @Parameter(description = "简历ID") @PathVariable Long resumeId,
            @Parameter(description = "简历对象") @RequestBody Resume resume) {
        Map<String, Object> analysis = aiService.analyzeResume(resume);
        return ResponseEntity.ok(analysis);
    }
}