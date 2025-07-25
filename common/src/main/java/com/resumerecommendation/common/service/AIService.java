package com.resumerecommendation.common.service;

import com.resumerecommendation.common.entity.Resume;
import com.resumerecommendation.common.entity.JobPosition;

import java.util.List;
import java.util.Map;

public interface AIService {

    Map<String, Object> parseExtractedInfoResponse(Resume resume);
    /**
     * 分析简历内容
     */
    Map<String, Object> analyzeResume(Resume resume);

    /**
     * 提取简历中的技能
     */
    List<String> extractSkills(String content);

    /**
     * 评估技能水平
     */
    Map<String, Integer> assessSkillLevels(String content, List<String> skills);

    /**
     * 生成简历改进建议
     */
    String generateImprovementSuggestions(Resume resume);

    /**
     * 计算简历与职位的匹配度
     */
    Map<String, Object> calculateMatchScore(Resume resume, JobPosition jobPosition);

    /**
     * 生成匹配分析报告
     */
    String generateMatchAnalysis(Resume resume, JobPosition jobPosition, Map<String, Object> matchResults);

    /**
     * 基于职位描述生成简历优化建议
     */
    String generateTargetedSuggestions(Resume resume, JobPosition targetPosition);
} 