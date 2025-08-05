package com.resumerecommendation.common.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "职位信息实体")
public class JobPosition {

    @Schema(description = "职位ID")
    private Long id;

    @Schema(description = "职位标题")
    private String title;

    @Schema(description = "公司名称")
    private String company;

    @Schema(description = "工作地点")
    private String location;

    @Schema(description = "所属行业")
    private String industry;

    @Schema(description = "职位描述")
    private String description;

    @Schema(description = "职位来源")
    private String source;

    @Schema(description = "来源URL")
    private String sourceUrl;

    @Schema(description = "最低薪资")
    private Integer salaryMin;

    @Schema(description = "最高薪资")
    private Integer salaryMax;

    @Schema(description = "薪资")
    private String salary;

    @Schema(description = "所需技能")
    private List<String> requiredSkills;

    @Schema(description = "所需工作经验（年）")
    private Integer requiredExperience;

    @Schema(description = "职位类型")
    private String employmentType;

    @Schema(description = "工作性质")
    private String jobNature;

    @Schema(description = "学历要求")
    private String educationRequirement;

    @Schema(description = "公司规模")
    private String companySize;

    @Schema(description = "公司性质")
    private String companyType;

    @Schema(description = "工作年限")
    private Integer experienceYears;

    @Schema(description = "爬取时间")
    private LocalDateTime crawlTime;

    @Schema(description = "发布时间")
    private LocalDateTime publishTime;

    @Schema(description = "职位状态")
    private String status;

    @Schema(description = "职位福利")
    private List<String> benefits;

    @Schema(description = "相关度分数")
    private Double relevanceScore;

}