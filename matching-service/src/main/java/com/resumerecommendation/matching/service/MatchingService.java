package com.resumerecommendation.matching.service;

import com.resumerecommendation.common.entity.JobPosition;
import com.resumerecommendation.common.entity.MatchResult;
import com.resumerecommendation.common.entity.Resume;

import java.util.List;

public interface MatchingService {
    /**
     * 为简历匹配合适的职位
     */
    List<MatchResult> matchJobsForResume(Resume resume, Integer limit);

    /**
     * 为职位匹配合适的简历
     */
    List<MatchResult> matchResumesForJob(JobPosition jobPosition, Integer limit);

    /**
     * 计算简历和职位的匹配度
     */
    MatchResult calculateMatch(Resume resume, JobPosition jobPosition);

    /**
     * 获取简历的推荐职位
     */
    List<JobPosition> getRecommendedJobs(Resume resume, Integer limit);

    /**
     * 获取职位的推荐简历
     */
    List<Resume> getRecommendedResumes(JobPosition jobPosition, Integer limit);

    /**
     * 基于用户反馈更新推荐模型
     */
    void updateRecommendationModel(Long resumeId, Long jobId, Boolean isRelevant);
} 