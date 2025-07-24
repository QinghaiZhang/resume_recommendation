package com.resumerecommendation.common.entity;

import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "match_results")
public class MatchResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long resumeId;

    private Long jobPositionId;

    @Field(type = FieldType.Float)
    private Float overallScore;

    @Field(type = FieldType.Object)
    private Map<String, Float> categoryScores;

    @Field(type = FieldType.Object)
    private Map<String, String> matchAnalysis;

    @Field(type = FieldType.Text)
    private String aiSuggestions;

    private LocalDateTime matchTime;

    @Field(type = FieldType.Boolean)
    private Boolean isRecommended;
} 