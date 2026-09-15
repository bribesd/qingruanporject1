package com.enterprise.kb.ai;

import com.enterprise.kb.exception.ApiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 模块1：文档解析（PDFBox 解析 pdf，POI 解析 docx，纯文本直接读取）。
 */
@Service
public class DocumentParserService {

    private static final Set<String> SUPPORTED = Set.of("pdf", "docx", "txt", "md");

    public String parse(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!SUPPORTED.contains(ext)) {
            throw new ApiException(400, "不支持的文档类型，仅支持 pdf / docx / txt / md");
        }
        try (InputStream in = file.getInputStream()) {
            String text = switch (ext) {
                case "pdf" -> parsePdf(in);
                case "docx" -> parseDocx(in);
                default -> new String(in.readAllBytes(), StandardCharsets.UTF_8);
            };
            if (text == null || text.isBlank()) {
                throw new ApiException(400, "文档内容为空或无法提取文本");
            }
            return text;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(400, "文档解析失败：" + e.getMessage());
        }
    }

    private String parsePdf(InputStream in) throws Exception {
        try (PDDocument doc = PDDocument.load(in)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String parseDocx(InputStream in) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }
}
