package com.resumerecommendation.analysis.service;

import com.resumerecommendation.common.entity.Resume;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ResumeParserService {
    /**
     * 解析上传的简历文件
     */
    Resume parseResume(MultipartFile file) throws IOException;

    /**
     * 解析PDF格式简历
     */
    Resume parsePdfResume(byte[] content) throws IOException;

    /**
     * 解析Word格式简历
     */
    Resume parseWordResume(byte[] content) throws IOException;

    /**
     * 提取文本内容
     */
    String extractText(byte[] content, String fileType) throws IOException;
} 