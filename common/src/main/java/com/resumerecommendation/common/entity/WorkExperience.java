package com.resumerecommendation.common.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工作经验实体")
public class WorkExperience {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "公司名称")
    private String companyName;

    @Schema(description = "职位名称")
    private String jobTitle;

    @Schema(description = "所属行业")
    private String industry;

    @Schema(description = "工作描述")
    private String description;

    @Schema(description = "开始时间")
    private LocalDate startDate;

    @Schema(description = "结束时间")
    private LocalDate endDate;

    @Schema(description = "使用的技能")
    private List<String> usedSkills;

    @Schema(description = "主要职责")
    private List<String> responsibilities;

    @Schema(description = "成就")
    private List<String> achievements;

    @Schema(description = "公司规模")
    private String companySize;

    @Schema(description = "公司类型")
    private String companyType;

    /**
     * 计算工作时长（月）
     */
    public int calculateDuration() {
        if (startDate == null) return 0;
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return (end.getYear() - startDate.getYear()) * 12 + end.getMonthValue() - startDate.getMonthValue();
    }
} 