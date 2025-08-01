package com.resumerecommendation.crawler.service.impl;

import com.resumerecommendation.common.entity.JobPosition;
import com.resumerecommendation.common.entity.Resume;
import com.resumerecommendation.common.entity.WorkExperience;
import com.resumerecommendation.crawler.config.CrawlerConfig;
import com.resumerecommendation.crawler.service.JobCrawlerService;
import com.resumerecommendation.crawler.service.ResumeAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeCrawlerServiceImpl implements JobCrawlerService {

    private final CrawlerConfig crawlerConfig;
    private final ResumeAnalyzer resumeAnalyzer;
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Override
    public List<JobPosition> crawlJobsBasedOnResume(Resume resume, Integer maxResults) {
        // 分析简历
        ResumeAnalyzer.AnalysisResult analysis = resumeAnalyzer.analyzeResume(resume);
        
        // 创建多个爬虫任务
        List<CompletableFuture<List<JobPosition>>> futures = new ArrayList<>();
        
        // 基于技能的爬取
        futures.add(CompletableFuture.supplyAsync(() -> 
            crawlJobsBySkills(analysis.getKeywords(), maxResults / 3), executorService));
            
        // 基于行业的爬取
        futures.add(CompletableFuture.supplyAsync(() ->
            crawlJobsByIndustry(analysis.getIndustries(), maxResults / 3), executorService));
            
        // 基于职位名称的爬取
        futures.add(CompletableFuture.supplyAsync(() ->
            crawlJobsByTitle(resume.getWorkExperiences(), maxResults / 3), executorService));

        // 合并结果
        List<JobPosition> allJobs = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        // 计算相关度并排序
        return rankJobsByRelevance(allJobs, analysis);
    }

    public List<JobPosition> crawlJobsBySkills(List<String> skills, Integer maxResults) {
        if (skills.isEmpty()) return new ArrayList<>();

        String skillsQuery = String.join(" ", skills);
        return searchJobs(skillsQuery, null, maxResults);
    }

    private List<JobPosition> crawlJobsByIndustry(List<String> industries, Integer maxResults) {
        if (industries.isEmpty()) return new ArrayList<>();

        String industryQuery = String.join(" ", industries);
        return searchJobs(industryQuery, null, maxResults);
    }

    private List<JobPosition> crawlJobsByTitle(List<WorkExperience> experiences, Integer maxResults) {
        if (experiences == null || experiences.isEmpty()) return new ArrayList<>();

        String titleQuery = experiences.stream()
                .map(WorkExperience::getJobTitle)
                .collect(Collectors.joining(" "));
        return searchJobs(titleQuery, null, maxResults);
    }

    private List<JobPosition> rankJobsByRelevance(List<JobPosition> jobs, ResumeAnalyzer.AnalysisResult analysis) {
        // 计算每个职位的相关度分数
        Map<JobPosition, Double> scoreMap = new HashMap<>();
        
        for (JobPosition job : jobs) {
            double score = calculateRelevanceScore(job, analysis);
            scoreMap.put(job, score);
        }
        
        // 按分数排序
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<JobPosition, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private double calculateRelevanceScore(JobPosition job, ResumeAnalyzer.AnalysisResult analysis) {
        double skillScore = calculateSkillScore(job, analysis.getSkillWeights());
        double industryScore = calculateIndustryScore(job, analysis.getIndustries());
        double experienceScore = calculateExperienceScore(job, analysis.getYearsOfExperience());
        
        // 权重可以通过配置调整
        return skillScore * 0.4 + industryScore * 0.3 + experienceScore * 0.3;
    }

    private double calculateSkillScore(JobPosition job, Map<String, Double> skillWeights) {
        if (job.getRequiredSkills() == null || job.getRequiredSkills().isEmpty()) {
            return 0.0;
        }

        return job.getRequiredSkills().stream()
                .mapToDouble(skill -> skillWeights.getOrDefault(skill.toLowerCase(), 0.0))
                .average()
                .orElse(0.0);
    }

    private double calculateIndustryScore(JobPosition job, List<String> industries) {
        if (industries.isEmpty()) return 0.0;
        
        // 简单实现：如果职位所属行业在简历行业列表中，给1.0分，否则0.0分
        return industries.stream()
                .anyMatch(industry -> 
                    job.getIndustry() != null && 
                    job.getIndustry().toLowerCase().contains(industry.toLowerCase())) ? 1.0 : 0.0;
    }

    private double calculateExperienceScore(JobPosition job, int yearsOfExperience) {
        if (job.getRequiredExperience() == null) return 1.0;
        
        int required = job.getRequiredExperience();
        if (yearsOfExperience >= required) {
            return 1.0;
        } else if (yearsOfExperience >= required * 0.7) {
            return 0.7;
        } else {
            return 0.3;
        }
    }

    // 实现其他必要的接口方法
    @Override
    public void startCrawling(String source) {
        // 实现代码
    }

    @Override
    public void stopCrawling() {
        // 实现代码
    }

    @Override
    public String getCrawlerStatus() {
        return "Running";
    }

    @Override
    public List<JobPosition> searchJobs(String keyword, String city, Integer pageSize) {
        // 实现搜索逻辑，可以复用JobCrawlerServiceImpl中的实现
        return new ArrayList<>();
    }

    @Override
    public void updateExpiredJobs() {
        // 实现代码
    }

    @Override
    public void cleanInvalidJobs() {
        // 实现代码
    }
} 