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
import java.util.List;
import java.util.Map;

@Data
@Entity
@Table(name = "resumes")
@Document(indexName = "resumes")
public class Resume {
    @Id
    @javax.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Integer)
    private Integer age;

    @Field(type = FieldType.Text)
    private String email;

    @Field(type = FieldType.Text)
    private String phone;

    @Field(type = FieldType.Text)
    private String education;

    @Field(type = FieldType.Nested)
    private List<WorkExperience> workExperiences;

    @Field(type = FieldType.Keyword)
    private List<String> skills;

    @Field(type = FieldType.Object)
    private Map<String, Integer> skillLevels;

    @Field(type = FieldType.Text)
    private String rawContent;

    @Field(type = FieldType.Float)
    private Float score;

    @Field(type = FieldType.Text)
    private String aiAnalysis;

    @Field(type = FieldType.Text)
    private String improvementSuggestions;
} 