package com.resumerecommendation.common.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Entity
@Table(name = "job_positions")
@Document(indexName = "job_positions")
public class JobPosition {
    @Id
    @javax.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String company;

    @Field(type = FieldType.Text)
    private String location;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Integer)
    private Integer salaryMin;

    @Field(type = FieldType.Integer)
    private Integer salaryMax;

    @Field(type = FieldType.Keyword)
    private List<String> requiredSkills;

    @Field(type = FieldType.Keyword)
    private List<String> preferredSkills;

    @Field(type = FieldType.Object)
    private Map<String, Integer> skillLevelRequirements;

    @Field(type = FieldType.Integer)
    private Integer experienceYears;

    @Field(type = FieldType.Text)
    private String educationRequirement;

    @Field(type = FieldType.Text)
    private String source;

    @Field(type = FieldType.Text)
    private String sourceUrl;

    @Field(type = FieldType.Date)
    private LocalDateTime publishTime;

    @Field(type = FieldType.Date)
    private LocalDateTime crawlTime;
} 