package com.resumerecommendation.common.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工作经历实体")
@Entity
@Table(name = "work_experiences")
public class WorkExperience {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "公司名称")
    @Field(type = FieldType.Text)
    private String companyName;

    @Schema(description = "职位")
    @Field(type = FieldType.Text)
    private String position;

    @Schema(description = "开始日期")
    @Field(type = FieldType.Date)
    private LocalDate startDate;

    @Schema(description = "结束日期")
    @Field(type = FieldType.Date)
    private LocalDate endDate;

    @Schema(description = "工作描述")
    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private List<String> responsibilities;

    @Field(type = FieldType.Keyword)
    private List<String> achievements;

    @Field(type = FieldType.Keyword)
    private List<String> technologies;
} 