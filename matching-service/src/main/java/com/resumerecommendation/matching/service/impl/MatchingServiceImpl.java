package com.resumerecommendation.matching.service.impl;

import com.resumerecommendation.common.entity.JobPosition;
import com.resumerecommendation.common.entity.MatchResult;
import com.resumerecommendation.common.entity.Resume;
import com.resumerecommendation.common.service.AIService;
import com.resumerecommendation.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingServiceImpl implements MatchingService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final AIService aiService;

    @Override
    public List<MatchResult> matchJobsForResume(Resume resume, Integer limit) {
        // 1. 使用Elasticsearch搜索相关职位
        List<JobPosition> relevantJobs = searchRelevantJobs(resume, limit);

        // 2. 使用AI服务计算详细匹配度
        return relevantJobs.stream()
                .map(job -> calculateMatch(resume, job))
                .sorted(Comparator.comparing(MatchResult::getOverallScore).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<MatchResult> matchResumesForJob(JobPosition jobPosition, Integer limit) {
        // 1. 使用Elasticsearch搜索相关简历
        List<Resume> relevantResumes = searchRelevantResumes(jobPosition, limit);

        // 2. 使用AI服务计算详细匹配度
        return relevantResumes.stream()
                .map(resume -> calculateMatch(resume, jobPosition))
                .sorted(Comparator.comparing(MatchResult::getOverallScore).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public MatchResult calculateMatch(Resume resume, JobPosition jobPosition) {
        // 使用AI服务计算匹配度
        Map<String, Object> matchResults = aiService.calculateMatchScore(resume, jobPosition);
        String analysis = aiService.generateMatchAnalysis(resume, jobPosition, matchResults);

        // 创建匹配结果
        MatchResult matchResult = new MatchResult();
        matchResult.setResumeId(resume.getId());
        matchResult.setJobPositionId(jobPosition.getId());
        matchResult.setOverallScore(calculateOverallScore(matchResults));
        matchResult.setCategoryScores(extractCategoryScores(matchResults));
        matchResult.setMatchAnalysis(extractMatchAnalysis(matchResults));
        matchResult.setAiSuggestions(analysis);
        matchResult.setMatchTime(LocalDateTime.now());

        return matchResult;
    }

    @Override
    public List<JobPosition> getRecommendedJobs(Resume resume, Integer limit) {
        // 1. 获取匹配结果
        List<MatchResult> matches = matchJobsForResume(resume, limit);

        // 2. 提取推荐的职位
        return matches.stream()
                .filter(match -> match.getOverallScore() >= 0.7) // 匹配度阈值
                .map(match -> findJobById(match.getJobPositionId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<Resume> getRecommendedResumes(JobPosition jobPosition, Integer limit) {
        // 1. 获取匹配结果
        List<MatchResult> matches = matchResumesForJob(jobPosition, limit);

        // 2. 提取推荐的简历
        return matches.stream()
                .filter(match -> match.getOverallScore() >= 0.7) // 匹配度阈值
                .map(match -> findResumeById(match.getResumeId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public void updateRecommendationModel(Long resumeId, Long jobId, Boolean isRelevant) {
        // 实现基于用户反馈的模型更新逻辑
        // 这里可以收集用户反馈数据，用于后续模型优化
    }

    private List<JobPosition> searchRelevantJobs(Resume resume, Integer limit) {
        // 构建搜索条件
        Criteria criteria = new Criteria();

        // 添加技能匹配条件
        if (resume.getSkills() != null && !resume.getSkills().isEmpty()) {
            criteria.and(new Criteria("requiredSkills").in(resume.getSkills()));
        }

        // 添加工作经验条件
        if (resume.getWorkExperiences() != null && !resume.getWorkExperiences().isEmpty()) {
            int yearsOfExperience = calculateTotalExperience(resume);
            criteria.and(new Criteria("experienceYears").lessThanEqual(yearsOfExperience));
        }

        // 执行搜索
        SearchHits<JobPosition> searchHits = elasticsearchOperations.search(
                new CriteriaQuery(criteria).setMaxResults(limit),
                JobPosition.class
        );

        return searchHits.stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }

    private List<Resume> searchRelevantResumes(JobPosition jobPosition, Integer limit) {
        // 构建搜索条件
        Criteria criteria = new Criteria();

        // 添加技能匹配条件
        if (jobPosition.getRequiredSkills() != null && !jobPosition.getRequiredSkills().isEmpty()) {
            criteria.and(new Criteria("skills").in(jobPosition.getRequiredSkills()));
        }

        // 添加工作经验条件
        if (jobPosition.getExperienceYears() != null) {
            criteria.and(new Criteria("workExperiences").exists());
        }

        // 执行搜索
        SearchHits<Resume> searchHits = elasticsearchOperations.search(
                new CriteriaQuery(criteria).setMaxResults(limit),
                Resume.class
        );

        return searchHits.stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }

    private Float calculateOverallScore(Map<String, Object> matchResults) {
        // 实现计算总分的逻辑
        return (Float) matchResults.getOrDefault("overallScore", 0.0f);
    }

    private Map<String, Float> extractCategoryScores(Map<String, Object> matchResults) {
        // 实现提取分类得分的逻辑
        @SuppressWarnings("unchecked")
        Map<String, Float> scores = (Map<String, Float>) matchResults.getOrDefault("categoryScores", new HashMap<>());
        return scores;
    }

    private Map<String, String> extractMatchAnalysis(Map<String, Object> matchResults) {
        // 实现提取匹配分析的逻辑
        @SuppressWarnings("unchecked")
        Map<String, String> analysis = (Map<String, String>) matchResults.getOrDefault("analysis", new HashMap<>());
        return analysis;
    }

    private JobPosition findJobById(Long jobId) {
        return elasticsearchOperations.get(jobId.toString(), JobPosition.class);
    }

    private Resume findResumeById(Long resumeId) {
        return elasticsearchOperations.get(resumeId.toString(), Resume.class);
    }

    private int calculateTotalExperience(Resume resume) {
        if (resume.getWorkExperiences() == null || resume.getWorkExperiences().isEmpty()) {
            return 0;
        }

        return resume.getWorkExperiences().stream()
                .mapToInt(exp -> {
                    if (exp.getStartDate() == null || exp.getEndDate() == null) {
                        return 0;
                    }
                    return exp.getEndDate().getYear() - exp.getStartDate().getYear();
                })
                .sum();
    }
} 