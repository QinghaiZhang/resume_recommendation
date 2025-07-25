package com.resumerecommendation.analysis.service.impl;

import com.resumerecommendation.analysis.service.ResumeParserService;
import com.resumerecommendation.common.entity.Resume;
import com.resumerecommendation.common.service.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeParserServiceImpl implements ResumeParserService {

    private final AIService aiService;

    @Override
    public Resume parseResume(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        String fileType = getFileExtension(fileName);
        byte[] content = file.getBytes();

        String extractedText = extractText(content, fileType);
        return processResumeText(extractedText);
    }

    @Override
    public Resume parsePdfResume(byte[] content) throws IOException {
        String extractedText = extractText(content, "pdf");
        return processResumeText(extractedText);
    }

    @Override
    public Resume parseWordResume(byte[] content) throws IOException {
        String extractedText = extractText(content, "docx");
        return processResumeText(extractedText);
    }

    @Override
    public String extractText(byte[] content, String fileType) throws IOException {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> extractPdfText(content);
            case "docx" -> extractWordText(content);
            default -> throw new IllegalArgumentException("Unsupported file type: " + fileType);
        };
    }

    private String extractPdfText(byte[] content) throws IOException {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(content))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractWordText(byte[] content) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            XWPFWordExtractor extractor = new XWPFWordExtractor(document);
            return extractor.getText();
        }
    }

    private Resume processResumeText(String text) {
        Resume resume = new Resume();
        resume.setRawContent(text);
        // 使用AI服务分析简历内容
        aiService.parseExtractedInfoResponse(resume);
        List<String> skills = aiService.extractSkills(text);
        Map<String, Integer> skillLevels = aiService.assessSkillLevels(text, skills);
        String suggestions = aiService.generateImprovementSuggestions(resume);

        // 设置分析结果
        resume.setSkills(skills);
        resume.setSkillLevels(skillLevels);
        resume.setImprovementSuggestions(suggestions);

        Map<String, Object> analysis = aiService.analyzeResume(resume);
        resume.setAiAnalysis(analysis.toString());
        return resume;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("File name cannot be null");
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            throw new IllegalArgumentException("Invalid file name: " + fileName);
        }
        return fileName.substring(lastDotIndex + 1);
    }
} 