package com.resumerecommendation.common.service.impl;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerecommendation.common.entity.JobPosition;
import com.resumerecommendation.common.entity.Resume;
import com.resumerecommendation.common.entity.WorkExperience;
import com.resumerecommendation.common.service.AIService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service("aiService")
public class QwenAIServiceImpl implements AIService {

    @Value("${dashscope.api.key}")
    private String apiKey;

    private Generation generation;

    private static final String MODEL = "qwen-plus";
    
    // 创建ObjectMapper实例用于JSON解析
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    private void init() {
        generation = new Generation();
    }

    @Override
    public Map<String, Object> analyzeResume(Resume resume) {
        // 生成分析报告
        String analysisPrompt = String.format("""
            分析以下简历内容，提供详细的分析报告：
            
            个人信息：
            姓名：%s
            教育背景：%s
            
            工作经验：
            %s
            
            技能：
            %s
            
            请提供以下方面的分析：
            1. 职业发展轨迹
            2. 核心竞争力
            3. 技能评估
            4. 经验水平
            5. 改进建议
            """,
            resume.getName(),
            resume.getEducation(),
            formatWorkExperience(resume),
            String.join(", ", resume.getSkills())
        );

        String analysisResponse = callQwen(analysisPrompt);
        Map<String, Object> analysisResult = parseAnalysisResponse(analysisResponse);
        return analysisResult;
    }

    @NotNull
    public void parseExtractedInfoResponse(Resume resume) {
        String prompt = String.format("""
            请分析以下简历内容，从中提取并结构化关键信息：
            
            简历全文：
            %s
            
            请从以上简历中提取以下信息并以JSON格式返回：
            1. 姓名(full_name)
            2. 教育背景(education)，包括学校、专业、学历、时间等
            3. 工作经验(work_experiences)，每项包含公司名称、职位、时间段、工作描述
            4. 技能(skills)，包括技术技能和软技能
            
            返回格式示例：
            {
              "full_name": "张三",
              "education": "北京大学计算机科学与技术专业，学士学位，2015-2019",
              "work_experiences": [
                {
                  "company": "某某科技有限公司",
                  "position": "高级软件工程师",
                  "period": "2020年1月-至今",
                  "description": "负责..."
                }
              ],
              "skills": ["Java", "Spring Boot", "沟通能力"]
            }
            
            请严格按照以上JSON格式返回，不要包含其他内容。
            """, resume.getRawContent());

        String response = callQwen(prompt);
        Map<String, Object> extractedInfo = parseExtractedInfoResponse(response);

        // 将提取的信息填充到resume对象中
        if (extractedInfo.containsKey("full_name")) {
            resume.setName((String) extractedInfo.get("full_name"));
        }

        if (extractedInfo.containsKey("education")) {
            resume.setEducation((String) extractedInfo.get("education"));
        }

        if (extractedInfo.containsKey("skills")) {
            List<String> skills = (List<String>) extractedInfo.get("skills");
            resume.setSkills(skills);
        }

        if (extractedInfo.containsKey("work_experiences")) {
            List<WorkExperience> work_experiences = (List<WorkExperience>) extractedInfo.get("work_experiences");
            resume.setWorkExperiences(work_experiences);
        }
    }

    @Override
    public List<String> extractSkills(String content) {
        String prompt = "从以下文本中提取所有技术技能、软技能和专业技能，按类别分类：\n\n" +
            content + "\n\n" +
            "请以JSON格式返回，格式如下：\n" +
            "{\n" +
            "    \"technical_skills\": [],\n" +
            "    \"soft_skills\": [],\n" +
            "    \"professional_skills\": []\n" +
            "}";

        String response = callQwen(prompt);
        return parseSkillsResponse(response);
    }

    @Override
    public Map<String, Integer> assessSkillLevels(String content, List<String> skills) {
        String prompt = String.format("""
            基于以下内容，评估列出技能的熟练度（1-5分）：
            
            内容：
            %s
            
            技能列表：
            %s
            
            请以JSON格式返回评分，并说明评分依据。
            """, content, String.join(", ", skills));

        String response = callQwen(prompt);
        return parseSkillLevelsResponse(response);
    }

    @Override
    public String generateImprovementSuggestions(Resume resume) {
        String prompt = String.format("""
            基于以下简历信息，提供具体的改进建议：
            
            教育背景：%s
            工作经验：%s
            技能：%s
            
            请从以下方面提供建议：
            1. 内容展示
            2. 技能提升
            3. 经验描述
            4. 职业规划
            """,
            resume.getEducation(),
            formatWorkExperience(resume),
            String.join(", ", resume.getSkills())
        );

        return callQwen(prompt);
    }

    @Override
    public Map<String, Object> calculateMatchScore(Resume resume, JobPosition jobPosition) {
        String prompt = String.format("""
            比较以下简历和职位要求的匹配程度：
            
            简历信息：
            教育：%s
            工作经验：%s
            技能：%s
            
            职位要求：
            职位：%s
            公司：%s
            要求技能：%s
            教育要求：%s
            经验要求：%d年
            
            请从以下维度评分（0-100）并说明原因：
            1. 技能匹配度
            2. 经验匹配度
            3. 教育匹配度
            4. 整体匹配度
            """,
            resume.getEducation(),
            formatWorkExperience(resume),
            String.join(", ", resume.getSkills()),
            jobPosition.getTitle(),
            jobPosition.getCompany(),
            String.join(", ", jobPosition.getRequiredSkills()),
            jobPosition.getEducationRequirement(),
            jobPosition.getExperienceYears()
        );

        String response = callQwen(prompt);
        return parseMatchScoreResponse(response);
    }

    @Override
    public String generateMatchAnalysis(Resume resume, JobPosition jobPosition, Map<String, Object> matchResults) {
        String prompt = String.format("""
            基于以下匹配结果，生成详细的分析报告：
            
            匹配得分：
            %s
            
            请提供：
            1. 优势分析
            2. 不足分析
            3. 改进建议
            4. 竞争力评估
            """, formatMatchResults(matchResults));

        return callQwen(prompt);
    }

    @Override
    public String generateTargetedSuggestions(Resume resume, JobPosition targetPosition) {
        String prompt = String.format("""
            基于简历信息和目标职位，提供针对性的优化建议：
            
            简历技能：%s
            目标职位：%s
            职位要求技能：%s
            
            请提供：
            1. 需要强化的技能
            2. 需要补充的经验
            3. 简历描述优化建议
            4. 面试准备建议
            """,
            String.join(", ", resume.getSkills()),
            targetPosition.getTitle(),
            String.join(", ", targetPosition.getRequiredSkills())
        );

        return callQwen(prompt);
    }

    private String callQwen(String prompt) {
        try {
            Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content("你是一个专业的人力资源专家和职业顾问，擅长简历分析和职业规划。")
                .build();
            Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt)
                .build();

            GenerationParam param = GenerationParam.builder()
                .model(MODEL)
                .messages(Arrays.asList(systemMsg, userMsg))
                .temperature(0.7f)
                .apiKey(apiKey)
                .build();
                
            GenerationResult result = generation.call(param);
            
            // 增强错误处理，避免空指针异常
            if (result == null || result.getOutput() == null) {
                log.error("调用通义千问 API 返回空结果");
                throw new RuntimeException("AI 服务返回空结果");
            }
            
            GenerationOutput output = result.getOutput();
            
            // 首先尝试从choices获取结果
            if (output.getChoices() != null && !output.getChoices().isEmpty()) {
                return output.getChoices().get(0).getMessage().getContent();
            }
            
            // 如果choices为空，尝试从text获取结果
            if (output.getText() != null && !output.getText().isEmpty()) {
                return output.getText();
            }
            
            log.error("调用通义千问 API 返回结果中无有效内容");
            throw new RuntimeException("AI 服务未返回有效内容");
        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            log.error("调用通义千问 API 失败", e);
            throw new RuntimeException("AI 服务暂时不可用");
        } catch (Exception e) {
            log.error("调用通义千问 API 时发生未知错误", e);
            throw new RuntimeException("AI 服务发生未知错误");
        }
    }

    private String formatWorkExperience(Resume resume) {
        StringBuilder sb = new StringBuilder();
        if (resume.getWorkExperiences() != null) {
            resume.getWorkExperiences().forEach(exp -> {
                sb.append(String.format("""
                    公司：%s
                    职位：%s
                    时间：%s 至 %s
                    描述：%s
                    
                    """,
                    exp.getCompanyName(),
                    exp.getJobTitle(),
                    exp.getStartDate(),
                    exp.getEndDate(),
                    exp.getDescription()
                ));
            });
        }
        return sb.toString();
    }

    private Map<String, Object> parseAnalysisResponse(String response) {
        try {
            // 尝试解析为JSON对象
            Map<String, Object> resultMap = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            return resultMap;
        } catch (JsonProcessingException e) {
            // 如果不是有效的JSON，将其作为纯文本处理
            log.warn("分析响应不是有效的JSON格式，将作为纯文本处理: {}", e.getMessage());
            
            Map<String, Object> result = new HashMap<>();
            result.put("analysis_text", response);
            return result;
        } catch (Exception e) {
            log.error("处理分析响应时发生未知错误: {}", e.getMessage());
            log.error("原始响应内容: {}", response);
            throw new RuntimeException("处理分析响应时发生错误", e);
        }
    }

    private List<String> parseSkillsResponse(String response) {
        try {
            // 尝试将响应解析为JSON对象
            Map<String, Object> resultMap = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            
            List<String> allSkills = new ArrayList<>();
            
            // 提取各类技能
            String[] skillTypes = {"technical_skills", "soft_skills", "professional_skills"};
            for (String skillType : skillTypes) {
                if (resultMap.containsKey(skillType)) {
                    Object skillsObj = resultMap.get(skillType);
                    if (skillsObj instanceof List<?>) {
                        List<String> skills = (List<String>) skillsObj;
                        allSkills.addAll(skills);
                    }
                }
            }
            
            return allSkills;
        } catch (JsonProcessingException e) {
            // 如果不是有效的JSON，尝试其他解析方式
            log.warn("技能响应不是有效的JSON格式: {}", e.getMessage());
            
            // 尝试按行分割（假设每行一个技能）
            String[] lines = response.split("\n");
            List<String> skills = new ArrayList<>();
            for (String line : lines) {
                line = line.trim();
                // 过滤掉空行和标题类行
                if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("-") && !line.contains(":")) {
                    skills.add(line);
                }
            }
            
            if (!skills.isEmpty()) {
                return skills;
            }
            
            // 最后尝试简单地按逗号分割
            String[] skillArray = response.split(",");
            List<String> skillList = new ArrayList<>();
            for (String skill : skillArray) {
                String trimmedSkill = skill.trim();
                if (!trimmedSkill.isEmpty()) {
                    skillList.add(trimmedSkill);
                }
            }
            
            return skillList;
        } catch (Exception e) {
            log.error("处理技能响应时发生未知错误: {}", e.getMessage());
            log.error("原始响应内容: {}", response);
            throw new RuntimeException("处理技能响应时发生错误", e);
        }
    }

    private Map<String, Integer> parseSkillLevelsResponse(String response) {
        try {
            // 尝试将响应解析为JSON对象
            Map<String, Object> resultMap = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            
            Map<String, Integer> skillLevels = new HashMap<>();
            
            // 遍历所有键值对，查找技能等级信息
            for (Map.Entry<String, Object> entry : resultMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                // 如果值是数字类型，认为是技能等级
                if (value instanceof Number) {
                    skillLevels.put(key, ((Number) value).intValue());
                } 
                // 如果值是字符串但可以转换为数字，也认为是技能等级
                else if (value instanceof String) {
                    try {
                        int level = Integer.parseInt((String) value);
                        skillLevels.put(key, level);
                    } catch (NumberFormatException e) {
                        // 忽略无法解析为数字的字符串
                        log.debug("无法将字符串 '{}' 解析为技能等级: {}", value, e.getMessage());
                    }
                }
            }
            
            return skillLevels;
        } catch (JsonProcessingException e) {
            // 如果不是有效的JSON，尝试其他解析方式
            log.warn("技能等级响应不是有效的JSON格式: {}", e.getMessage());
            
            Map<String, Integer> skillLevels = new HashMap<>();
            
            // 尝试按行解析，格式如 "Java: 4" 或 "Python - 3"
            String[] lines = response.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    // 尝试匹配 "技能名称: 等级" 格式
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        String skill = parts[0].trim();
                        try {
                            int level = Integer.parseInt(parts[1].trim());
                            skillLevels.put(skill, level);
                            continue;
                        } catch (NumberFormatException ex) {
                            // 继续尝试其他格式
                        }
                    }
                    
                    // 尝试匹配 "技能名称 - 等级" 格式
                    String[] parts2 = line.split("-");
                    if (parts2.length == 2) {
                        String skill = parts2[0].trim();
                        try {
                            int level = Integer.parseInt(parts2[1].trim());
                            skillLevels.put(skill, level);
                            continue;
                        } catch (NumberFormatException ex) {
                            // 继续尝试其他格式
                        }
                    }
                    
                    // 尝试匹配 "技能名称 等级" 格式（用空格分隔）
                    String[] parts3 = line.split("\\s+");
                    if (parts3.length >= 2) {
                        try {
                            // 假设最后一部分是等级
                            int level = Integer.parseInt(parts3[parts3.length - 1]);
                            // 其余部分组成技能名称
                            String skill = String.join(" ", Arrays.copyOfRange(parts3, 0, parts3.length - 1));
                            skillLevels.put(skill.trim(), level);
                        } catch (NumberFormatException ex) {
                            // 无法解析，跳过该行
                            log.debug("无法解析技能等级行: {}", line);
                        }
                    }
                }
            }
            
            return skillLevels;
        } catch (Exception e) {
            log.error("处理技能等级响应时发生未知错误: {}", e.getMessage());
            log.error("原始响应内容: {}", response);
            throw new RuntimeException("处理技能等级响应时发生错误", e);
        }
    }

    private Map<String, Object> parseMatchScoreResponse(String response) {
        // 实现解析逻辑
        return new HashMap<>();
    }

    private String formatMatchResults(Map<String, Object> matchResults) {
        // 实现格式化逻辑
        return "";
    }
    
    private Map<String, Object> parseExtractedInfoResponse(String response) {
        try {
            // 将 JSON 字符串转换为 Map 结构
            Map<String, Object> resultMap = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            
            Map<String, Object> result = new HashMap<>();
            
            // 提取姓名
            if (resultMap.containsKey("full_name")) {
                result.put("full_name", resultMap.get("full_name"));
            }
            
            // 提取教育背景
            if (resultMap.containsKey("education")) {
                result.put("education", resultMap.get("education"));
            }
            
            // 提取技能列表
            if (resultMap.containsKey("skills")) {
                Object skillsObj = resultMap.get("skills");
                if (skillsObj instanceof List<?>) {
                    // 如果已经是 List 类型，直接使用
                    result.put("skills", (List<String>) skillsObj);
                } else if (skillsObj instanceof String) {
                    // 如果是字符串类型，尝试解析为列表
                    String[] skillsArray = ((String) skillsObj).split(",");
                    result.put("skills", Arrays.asList(skillsArray));
                }
            }
            
            // 提取工作经验
            if (resultMap.containsKey("work_experiences")) {
                Object workExpObj = resultMap.get("work_experiences");
                if (workExpObj instanceof List<?>) {
                    // 如果是Map列表，转换为WorkExperience对象列表
                    List<Map<String, Object>> workExpList = (List<Map<String, Object>>) workExpObj;
                    List<WorkExperience> workExperiences = new ArrayList<>();
                    for (Map<String, Object> workExpMap : workExpList) {
                        WorkExperience workExperience = new WorkExperience();
                        if (workExpMap.containsKey("company")) {
                            workExperience.setCompanyName((String) workExpMap.get("company"));
                        }
                        if (workExpMap.containsKey("position")) {
                            workExperience.setJobTitle((String) workExpMap.get("position"));
                        }
                        if (workExpMap.containsKey("period")) {
                            String period = (String) workExpMap.get("period");
                            // 简单处理时间段，实际项目中可能需要更复杂的解析
                            if (period != null && period.contains("-")) {
                                String[] parts = period.split("-");
                                if (parts.length >= 2) {
                                    try {
                                        // 这里只是简单示例，实际可能需要更复杂的日期解析
                                        // 由于WorkExperience使用LocalDate，这里不进行实际转换
                                    } catch (Exception e) {
                                        log.debug("无法解析时间段: {}", period);
                                    }
                                }
                            }
                        }
                        if (workExpMap.containsKey("description")) {
                            workExperience.setDescription((String) workExpMap.get("description"));
                        }
                        workExperiences.add(workExperience);
                    }
                    result.put("work_experiences", workExperiences);
                } else if (workExpObj instanceof String) {
                    // 如果是字符串，尝试重新解析为列表，然后转换为WorkExperience对象
                    try {
                        List<Map<String, Object>> workExpList = objectMapper.readValue(
                            (String) workExpObj,
                            new TypeReference<List<Map<String, Object>>>() {}
                        );
                        List<WorkExperience> workExperiences = new ArrayList<>();
                        for (Map<String, Object> workExpMap : workExpList) {
                            WorkExperience workExperience = new WorkExperience();
                            if (workExpMap.containsKey("company")) {
                                workExperience.setCompanyName((String) workExpMap.get("company"));
                            }
                            if (workExpMap.containsKey("position")) {
                                workExperience.setJobTitle((String) workExpMap.get("position"));
                            }
                            if (workExpMap.containsKey("period")) {
                                String period = (String) workExpMap.get("period");
                                // 简单处理时间段
                                if (period != null && period.contains("-")) {
                                    String[] parts = period.split("-");
                                    if (parts.length >= 2) {
                                        try {
                                            // 这里只是简单示例，实际可能需要更复杂的日期解析
                                        } catch (Exception e) {
                                            log.debug("无法解析时间段: {}", period);
                                        }
                                    }
                                }
                            }
                            if (workExpMap.containsKey("description")) {
                                workExperience.setDescription((String) workExpMap.get("description"));
                            }
                            workExperiences.add(workExperience);
                        }
                        result.put("work_experiences", workExperiences);
                    } catch (JsonProcessingException e) {
                        log.warn("无法将工作经验字符串解析为JSON列表: {}", e.getMessage());
                        // 如果解析失败，保留原始字符串
                        result.put("work_experiences", workExpObj);
                    }
                } else {
                    // 其他类型，直接保存
                    result.put("work_experiences", workExpObj);
                }
            }
            
            return result;
        } catch (JsonProcessingException e) {
            log.error("解析简历信息响应失败: {}", e.getMessage());
            log.error("原始响应内容: {}", response);
            throw new RuntimeException("AI 返回的简历信息格式不正确", e);
        } catch (Exception e) {
            log.error("处理简历信息响应失败: {}", e.getMessage());
            log.error("原始响应内容: {}", response);
            throw new RuntimeException("处理 AI 返回的简历信息时发生错误", e);
        }
    }
}