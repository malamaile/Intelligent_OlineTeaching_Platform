package com.iotp.controller;

import com.iotp.common.Result;
import com.iotp.security.UserContext;
import com.iotp.service.AiChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/ai")
public class AiChatController {

    @Autowired
    private AiChatService aiChatService;

    /** 上传文件根目录 */
    private static final String UPLOAD_DIR = System.getProperty("user.dir") + java.io.File.separator + "uploads";

    /**
     * AI 对话（SSE 流式）
     * POST /ai/chat
     * Body: { "message": "...", "context": {"contextType":"COURSE","contextName":"Java","contextId":101,...} }
     */
    @PostMapping("/chat")
    public SseEmitter chat(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getUserId();
        String message = (String) body.get("message");

        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.get("context");
        if (context == null) {
            context = new HashMap<>();
        }
        // 补充用户角色信息
        context.putIfAbsent("roleName", UserContext.getRole());
        final Map<String, Object> finalContext = context;

        SseEmitter emitter = new SseEmitter(0L);

        new Thread(() -> {
            try {
                aiChatService.chatStream(userId, message, finalContext, chunk -> {
                    try {
                        emitter.send(SseEmitter.event().name("message").data(chunk));
                    } catch (IOException ignored) {}
                });
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    /**
     * 上传文件并作为对话上下文
     * POST /ai/upload (multipart/form-data)
     * 返回文件内容文本，前端再带上 fileContent 调 /ai/chat
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String originalName = file.getOriginalFilename();
            String ext = "";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
            }

            // 生成唯一文件名
            String savedName = UUID.randomUUID().toString().replace("-", "") + ext;
            java.io.File dir = new java.io.File(UPLOAD_DIR, "ai-uploads");
            if (!dir.exists()) dir.mkdirs();
            java.io.File dest = new java.io.File(dir, savedName);
            file.transferTo(dest);

            // 提取文本内容
            String textContent = extractText(dest, ext);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fileName", originalName);
            data.put("fileUrl", "ai-uploads/" + savedName);
            data.put("fileContent", textContent);
            data.put("fileSize", file.getSize());
            return Result.ok("上传成功", data);
        } catch (Exception e) {
            return Result.error(500, "文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 获取对话历史
     * GET /ai/history?limit=50
     */
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> getHistory(@RequestParam(defaultValue = "50") int limit) {
        Long userId = UserContext.getUserId();
        List<Map<String, Object>> history = aiChatService.getHistory(userId, limit);
        return Result.ok(history);
    }

    /**
     * 清空对话历史
     * DELETE /ai/history
     */
    @DeleteMapping("/history")
    public Result<String> clearHistory() {
        Long userId = UserContext.getUserId();
        aiChatService.clearHistory(userId);
        return Result.ok("已清空");
    }

    /**
     * 全格式文本提取（支持 txt/md/代码/PDF/Word/PPT/Excel/图片）
     */
    private String extractText(java.io.File file, String ext) {
        try {
            // 纯文本类
            Set<String> textExts = new HashSet<>(Arrays.asList(
                    ".txt", ".md", ".java", ".py", ".c", ".cpp", ".h", ".js", ".ts",
                    ".json", ".xml", ".html", ".css", ".sql", ".yaml", ".yml", ".csv"
            ));
            if (textExts.contains(ext)) {
                return new String(java.nio.file.Files.readAllBytes(file.toPath()));
            }

            // PDF
            if (".pdf".equals(ext)) {
                return extractPdfText(file);
            }

            // DOCX
            if (".docx".equals(ext)) {
                return extractDocxText(file);
            }

            // DOC (旧版)
            if (".doc".equals(ext)) {
                return extractDocText(file);
            }

            // PPTX
            if (".pptx".equals(ext)) {
                return extractPptxText(file);
            }

            // PPT (旧版)
            if (".ppt".equals(ext)) {
                return extractPptText(file);
            }

            // Excel
            if (".xlsx".equals(ext) || ".xls".equals(ext)) {
                return extractExcelText(file);
            }

            // 图片
            if (Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp").contains(ext)) {
                return extractImageInfo(file, ext);
            }

        } catch (Exception e) {
            return "【文件解析失败：" + e.getMessage() + "】";
        }
        return "【不支持的文件格式：" + ext + "】";
    }

    // ==================== PDF ====================
    private String extractPdfText(java.io.File file) {
        try {
            org.apache.pdfbox.pdmodel.PDDocument doc =
                    org.apache.pdfbox.pdmodel.PDDocument.load(file);
            org.apache.pdfbox.text.PDFTextStripper stripper =
                    new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            doc.close();
            // 限制长度
            if (text.length() > 10000) text = text.substring(0, 10000) + "\n...(内容已截断)";
            return text.trim().isEmpty() ? "【PDF 无可提取文字，可能是扫描件】" : text;
        } catch (Exception e) {
            return "【PDF 解析失败：" + e.getMessage() + "】";
        }
    }

    // ==================== DOCX ====================
    private String extractDocxText(java.io.File file) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            org.apache.poi.xwpf.usermodel.XWPFDocument doc =
                    new org.apache.poi.xwpf.usermodel.XWPFDocument(fis);
            StringBuilder sb = new StringBuilder();
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText().trim();
                if (!text.isEmpty()) sb.append(text).append("\n");
            }
            // 也提取表格内容
            for (org.apache.poi.xwpf.usermodel.XWPFTable table : doc.getTables()) {
                for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
                    for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText().trim();
                        if (!cellText.isEmpty()) sb.append(cellText).append("\t");
                    }
                    sb.append("\n");
                }
            }
            doc.close();
            String text = sb.toString();
            if (text.length() > 10000) text = text.substring(0, 10000) + "\n...(内容已截断)";
            return text.trim().isEmpty() ? "【DOCX 文档内容为空】" : text;
        } catch (Exception e) {
            return "【DOCX 解析失败：" + e.getMessage() + "】";
        }
    }

    // ==================== DOC (旧版) ====================
    private String extractDocText(java.io.File file) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            org.apache.poi.hwpf.HWPFDocument doc =
                    new org.apache.poi.hwpf.HWPFDocument(fis);
            org.apache.poi.hwpf.extractor.WordExtractor extractor =
                    new org.apache.poi.hwpf.extractor.WordExtractor(doc);
            String text = String.join("\n", extractor.getParagraphText());
            extractor.close();
            doc.close();
            if (text.length() > 10000) text = text.substring(0, 10000) + "\n...(内容已截断)";
            return text.trim().isEmpty() ? "【DOC 文档内容为空】" : text;
        } catch (Exception e) {
            return "【DOC 解析失败：" + e.getMessage() + "】";
        }
    }

    // ==================== PPTX ====================
    private String extractPptxText(java.io.File file) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            org.apache.poi.xslf.usermodel.XMLSlideShow ppt =
                    new org.apache.poi.xslf.usermodel.XMLSlideShow(fis);
            StringBuilder sb = new StringBuilder();
            int slideNum = 1;
            for (org.apache.poi.xslf.usermodel.XSLFSlide slide : ppt.getSlides()) {
                sb.append("\n--- 第").append(slideNum).append("页 ---\n");
                for (org.apache.poi.xslf.usermodel.XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape) {
                        String text = ((org.apache.poi.xslf.usermodel.XSLFTextShape) shape).getText();
                        if (text != null && !text.trim().isEmpty()) {
                            sb.append(text.trim()).append("\n");
                        }
                    }
                }
                slideNum++;
            }
            ppt.close();
            String text = sb.toString();
            if (text.length() > 10000) text = text.substring(0, 10000) + "\n...(内容已截断)";
            return text.trim().isEmpty() ? "【PPTX 无可提取文字】" : text;
        } catch (Exception e) {
            return "【PPTX 解析失败：" + e.getMessage() + "】";
        }
    }

    // ==================== PPT (旧版) ====================
    private String extractPptText(java.io.File file) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            org.apache.poi.hslf.usermodel.HSLFSlideShow ppt =
                    new org.apache.poi.hslf.usermodel.HSLFSlideShow(fis);
            StringBuilder sb = new StringBuilder();
            int slideNum = 1;
            for (org.apache.poi.hslf.usermodel.HSLFSlide slide : ppt.getSlides()) {
                sb.append("\n--- 第").append(slideNum).append("页 ---\n");
                for (org.apache.poi.hslf.usermodel.HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof org.apache.poi.hslf.usermodel.HSLFTextShape) {
                        String text = ((org.apache.poi.hslf.usermodel.HSLFTextShape) shape).getText();
                        if (text != null && !text.trim().isEmpty()) {
                            sb.append(text.trim()).append("\n");
                        }
                    }
                }
                slideNum++;
            }
            ppt.close();
            String text = sb.toString();
            if (text.length() > 10000) text = text.substring(0, 10000) + "\n...(内容已截断)";
            return text.trim().isEmpty() ? "【PPT 无可提取文字】" : text;
        } catch (Exception e) {
            return "【PPT 解析失败，请转换为 PPTX 格式：" + e.getMessage() + "】";
        }
    }

    // ==================== Excel ====================
    private String extractExcelText(java.io.File file) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            org.apache.poi.ss.usermodel.Workbook wb =
                    org.apache.poi.ss.usermodel.WorkbookFactory.create(fis);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(i);
                sb.append("\n--- 工作表：").append(sheet.getSheetName()).append(" ---\n");
                for (org.apache.poi.ss.usermodel.Row row : sheet) {
                    for (org.apache.poi.ss.usermodel.Cell cell : row) {
                        sb.append(getCellValue(cell)).append("\t");
                    }
                    sb.append("\n");
                }
            }
            wb.close();
            String text = sb.toString();
            if (text.length() > 10000) text = text.substring(0, 10000) + "\n...(内容已截断)";
            return text.trim().isEmpty() ? "【Excel 内容为空】" : text;
        } catch (Exception e) {
            return "【Excel 解析失败：" + e.getMessage() + "】";
        }
    }

    private String getCellValue(org.apache.poi.ss.usermodel.Cell cell) {
        switch (cell.getCellType()) {
            case STRING:  return cell.getStringCellValue();
            case NUMERIC: return String.valueOf(Math.round(cell.getNumericCellValue() * 100.0) / 100.0);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return String.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) { return cell.getStringCellValue(); }
            default: return "";
        }
    }

    // ==================== 图片 ====================
    private String extractImageInfo(java.io.File file, String ext) {
        try {
            javax.imageio.ImageReader reader = null;
            java.util.Iterator<javax.imageio.ImageReader> readers =
                    javax.imageio.ImageIO.getImageReadersBySuffix(ext.replace(".", ""));
            if (readers.hasNext()) reader = readers.next();
            if (reader != null) {
                reader.setInput(javax.imageio.ImageIO.createImageInputStream(file));
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                long size = file.length();
                reader.dispose();
                return String.format(
                    "【图片信息】文件名：%s，尺寸：%d×%d像素，大小：%.1fKB。" +
                    "当前 AI 模型为文本模型，不支持识别图片内容，请用文字描述你想了解的内容。",
                    file.getName(), w, h, size / 1024.0
                );
            }
        } catch (Exception e) {
            return "【图片文件：" + file.getName() + "，AI 暂不支持图片内容识别】";
        }
        return "【图片文件：" + file.getName() + "，无法读取信息】";
    }
}
