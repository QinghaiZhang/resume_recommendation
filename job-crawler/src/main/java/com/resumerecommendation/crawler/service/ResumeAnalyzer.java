package com.resumerecommendation.crawler.service;

import com.resumerecommendation.common.entity.Resume;
import com.resumerecommendation.common.entity.WorkExperience;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ResumeAnalyzer {
    
    @Data
    public static class AnalysisResult {
        private List<String> keywords;
        private List<String> industries;
        private Map<String, Double> skillWeights;
        private int yearsOfExperience;
    }
    
    public AnalysisResult analyzeResume(Resume resume) {
        AnalysisResult result = new AnalysisResult();
        
        // 提取关键词
        Set<String> keywords = new HashSet<>();
        keywords.addAll(resume.getSkills());
        keywords.addAll(extractKeywordsFromExperience(resume.getWorkExperiences()));
        result.setKeywords(new ArrayList<>(keywords));
        
        // 提取行业信息
        result.setIndustries(extractIndustries(resume.getWorkExperiences()));
        
        // 计算技能权重
        result.setSkillWeights(calculateSkillWeights(resume));
        
        // 计算工作年限
        result.setYearsOfExperience(calculateTotalExperience(resume.getWorkExperiences()));
        
        return result;
    }
    
    private List<String> extractKeywordsFromExperience(List<WorkExperience> experiences) {
        if (experiences == null) return new ArrayList<>();
        
        return experiences.stream()
                .map(exp -> Arrays.asList(exp.getJobTitle().split("\\s+|,|;")))
                .flatMap(List::stream)
                .distinct()
                .collect(Collectors.toList());
    }
    
    private List<String> extractIndustries(List<WorkExperience> experiences) {
        if (experiences == null) return new ArrayList<>();
        
        return experiences.stream()
                .map(WorkExperience::getIndustry)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }
    
    private Map<String, Double> calculateSkillWeights(Resume resume) {
        Map<String, Double> weights = new HashMap<>();
        
        // 基于技能等级计算权重
        if (resume.getSkillLevels() != null) {
            resume.getSkillLevels().forEach((skill, level) -> {
                weights.put(skill, level / 10.0); // 假设技能等级为1-10
            });
        }
        
        // 基于工作经验中的技能使用频率调整权重
        if (resume.getWorkExperiences() != null) {
            resume.getWorkExperiences().forEach(exp -> {
                // 这里可以添加更复杂的权重计算逻辑
            });
        }
        
        return weights;
    }
    
    private int calculateTotalExperience(List<WorkExperience> experiences) {
        if (experiences == null) return 0;
        
        return experiences.stream()
                .mapToInt(exp -> {
                    // 这里需要根据实际的WorkExperience类结构计算工作时长
                    return 1; // 示例返回值
                })
                .sum();
    }
} 