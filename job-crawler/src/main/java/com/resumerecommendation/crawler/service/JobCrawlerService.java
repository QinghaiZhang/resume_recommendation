package com.resumerecommendation.crawler.service;

import com.resumerecommendation.common.entity.JobPosition;
import com.resumerecommendation.common.entity.Resume;
import com.resumerecommendation.common.entity.WorkExperience;

import java.util.List;

public interface JobCrawlerService {
    /**
     * 开始爬取指定来源的职位信息
     */
    void startCrawling(String source);

    /**
     * 停止爬虫
     */
    void stopCrawling();

    /**
     * 获取爬虫状态
     */
    String getCrawlerStatus();

    /**
     * 根据关键词搜索职位
     */
    List<JobPosition> searchJobs(String keyword, String city, Integer pageSize);

    /**
     * 更新过期的职位信息
     */
    void updateExpiredJobs();

    /**
     * 清理无效职位
     */
    void cleanInvalidJobs();

    /**
     * 基于简历爬取相关职位
     * @param resume 简历信息
     * @param maxResults 最大返回结果数
     * @return 相关职位列表
     */
    List<JobPosition> crawlJobsBasedOnResume(Resume resume, Integer maxResults);

    /**
     * 基于技能列表爬取职位
     * @param skills 技能列表
     * @param maxResults 最大返回结果数
     * @return 相关职位列表
     */
    default List<JobPosition> crawlJobsBySkills(List<String> skills, Integer maxResults) {
        return searchJobs(String.join(" ", skills), null, maxResults);
    }

    /**
     * 基于工作经验爬取职位
     * @param experiences 工作经验列表
     * @param maxResults 最大返回结果数
     * @return 相关职位列表
     */
    default List<JobPosition> crawlJobsByExperience(List<WorkExperience> experiences, Integer maxResults) {
        if (experiences == null || experiences.isEmpty()) {
            return List.of();
        }
        String query = experiences.stream()
                .map(WorkExperience::getJobTitle)
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        return searchJobs(query, null, maxResults);
    }
} 