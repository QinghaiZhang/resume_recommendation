package com.resumerecommendation.common.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "简历实体")
public class Resume {

    @Schema(description = "简历ID")
    private Long id;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "电话")
    private String phone;

    @Schema(description = "原始内容")
    private String rawContent;

    @Schema(description = "工作经验列表")
    private List<WorkExperience> workExperiences;

    @Schema(description = "技能列表")
    private List<String> skills;

    @Schema(description = "技能等级映射")
    private Map<String, Integer> skillLevels;

    @Schema(description = "教育背景")
    private String education;

    @Schema(description = "AI分析结果")
    private String aiAnalysis;

    @Schema(description = "改进建议")
    private String improvementSuggestions;

    @Schema(description = "附加信息")
    private Map<String, Object> additionalInfo;
}