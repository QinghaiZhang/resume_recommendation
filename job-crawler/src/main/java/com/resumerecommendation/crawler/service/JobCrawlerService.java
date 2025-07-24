package com.resumerecommendation.crawler.service;

import com.resumerecommendation.common.entity.JobPosition;

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
} 