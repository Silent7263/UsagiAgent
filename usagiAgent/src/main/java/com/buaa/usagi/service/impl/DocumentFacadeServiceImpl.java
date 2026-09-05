package com.buaa.usagi.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.buaa.usagi.converter.DocumentConverter;
import com.buaa.usagi.exception.BizException;
import com.buaa.usagi.mapper.DocumentMapper;
import com.buaa.usagi.model.dto.DocumentDTO;
import com.buaa.usagi.model.entity.Document;
import com.buaa.usagi.model.request.CreateDocumentRequest;
import com.buaa.usagi.model.request.UpdateDocumentRequest;
import com.buaa.usagi.model.response.CreateDocumentResponse;
import com.buaa.usagi.model.response.GetDocumentsResponse;
import com.buaa.usagi.model.vo.DocumentVO;
import com.buaa.usagi.mapper.ChunkBgeM3Mapper;
import com.buaa.usagi.model.entity.ChunkBgeM3;
import com.buaa.usagi.service.DocumentFacadeService;
import com.buaa.usagi.service.DocumentStorageService;
import com.buaa.usagi.service.MarkdownCorrectionService;
import com.buaa.usagi.service.MarkdownParserService;
import com.buaa.usagi.service.PdfMarkdownConverter;
import com.buaa.usagi.service.RagService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class DocumentFacadeServiceImpl implements DocumentFacadeService {

    private final DocumentMapper documentMapper;
    private final DocumentConverter documentConverter;
    private final DocumentStorageService documentStorageService;
    private final MarkdownParserService markdownParserService;
    private final RagService ragService;
    private final ChunkBgeM3Mapper chunkBgeM3Mapper;
    private final PdfMarkdownConverter pdfMarkdownConverter;
    private final MarkdownCorrectionService markdownCorrectionService;

    @Override
    public GetDocumentsResponse getDocuments() {
        List<Document> documents = documentMapper.selectAll();
        List<DocumentVO> result = new ArrayList<>();
        for (Document document : documents) {
            try {
                DocumentVO vo = documentConverter.toVO(document);
                result.add(vo);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return GetDocumentsResponse.builder()
                .documents(result.toArray(new DocumentVO[0]))
                .build();
    }

    @Override
    public GetDocumentsResponse getDocumentsByKbId(String kbId) {
        List<Document> documents = documentMapper.selectByKbId(kbId);
        List<DocumentVO> result = new ArrayList<>();
        for (Document document : documents) {
            try {
                DocumentVO vo = documentConverter.toVO(document);
                result.add(vo);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return GetDocumentsResponse.builder()
                .documents(result.toArray(new DocumentVO[0]))
                .build();
    }

    @Override
    public CreateDocumentResponse createDocument(CreateDocumentRequest request) {
        try {
            // 将 CreateDocumentRequest 转换为 DocumentDTO
            DocumentDTO documentDTO = documentConverter.toDTO(request);

            // 将 DocumentDTO 转换为 Document 实体
            Document document = documentConverter.toEntity(documentDTO);

            // 设置创建时间和更新时间
            LocalDateTime now = LocalDateTime.now();
            document.setCreatedAt(now);
            document.setUpdatedAt(now);

            // 插入数据库，ID 由数据库自动生成
            int result = documentMapper.insert(document);
            if (result <= 0) {
                throw new BizException("创建文档失败");
            }

            // 返回生成的 documentId
            return CreateDocumentResponse.builder()
                    .documentId(document.getId())
                    .build();
        } catch (JsonProcessingException e) {
            throw new BizException("创建文档时发生序列化错误: " + e.getMessage());
        }
    }

    @Override
    public CreateDocumentResponse uploadDocument(String kbId, MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new BizException("上传的文件为空");
            }

            // 提取文件信息
            String originalFilename = file.getOriginalFilename();
            String filetype = getFileType(originalFilename);
            long fileSize = file.getSize();

            // 创建文档记录（先创建记录，获取 documentId）
            DocumentDTO documentDTO = DocumentDTO.builder()
                    .kbId(kbId)
                    .filename(originalFilename)
                    .filetype(filetype)
                    .size(fileSize)
                    .build();

            Document document = documentConverter.toEntity(documentDTO);
            LocalDateTime now = LocalDateTime.now();
            document.setCreatedAt(now);
            document.setUpdatedAt(now);

            // 插入数据库，获取生成的 documentId
            int result = documentMapper.insert(document);
            if (result <= 0) {
                throw new BizException("创建文档记录失败");
            }

            String documentId = document.getId();

            // 保存文件
            String filePath = documentStorageService.saveFile(kbId, documentId, file);

            // 更新文档记录，保存文件路径到 metadata
            DocumentDTO.MetaData metadata = new DocumentDTO.MetaData();
            metadata.setFilePath(filePath);
            documentDTO.setMetadata(metadata);
            documentDTO.setId(documentId);
            documentDTO.setCreatedAt(now);
            documentDTO.setUpdatedAt(now);

            Document updatedDocument = documentConverter.toEntity(documentDTO);
            updatedDocument.setId(documentId);
            updatedDocument.setCreatedAt(now);
            updatedDocument.setUpdatedAt(now);

            documentMapper.updateById(updatedDocument);

            log.info("文档上传成功: kbId={}, documentId={}, filename={}", kbId, documentId, originalFilename);

            // 如果是 Markdown 文件，进行解析并生成 chunks
            if ("md".equalsIgnoreCase(filetype) || "markdown".equalsIgnoreCase(filetype)) {
                processMarkdownDocument(kbId, documentId, filePath);
            } else if ("pdf".equalsIgnoreCase(filetype)) {
                // PDF：转换 Markdown → 模型纠错 → 解析入库
                try {
                    processPdfDocument(kbId, documentId, filePath, originalFilename);
                } catch (Exception e) {
                    // 处理失败时清理已创建的记录与文件，避免残留
                    try {
                        documentStorageService.deleteFile(filePath);
                    } catch (Exception ignore) {
                        log.warn("清理 PDF 文件失败: filePath={}", filePath);
                    }
                    documentMapper.deleteById(documentId);
                    throw e;
                }
            } else {
                // TODO: 未来可以增加其他文件类型的处理逻辑
                log.warn("待新增处理的文件类型: {}", filetype);
            }

            return CreateDocumentResponse.builder()
                    .documentId(documentId)
                    .build();
        } catch (IOException e) {
            log.error("文件保存失败", e);
            throw new BizException("文件保存失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteDocument(String documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BizException("文档不存在: " + documentId);
        }

        // 删除文件
        try {
            DocumentDTO documentDTO = documentConverter.toDTO(document);
            if (documentDTO.getMetadata() != null && documentDTO.getMetadata().getFilePath() != null) {
                String filePath = documentDTO.getMetadata().getFilePath();
                documentStorageService.deleteFile(filePath);
            }
        } catch (Exception e) {
            log.warn("删除文件失败，继续删除文档记录: documentId={}, error={}", documentId, e.getMessage());
            // 即使文件删除失败，也继续删除数据库记录
        }

        // 删除数据库记录
        int result = documentMapper.deleteById(documentId);
        if (result <= 0) {
            throw new BizException("删除文档失败");
        }
    }

    /**
     * 处理 PDF 文档：转换 Markdown → 模型纠错 → 解析并生成 chunks
     */
    private void processPdfDocument(String kbId, String documentId, String filePath, String originalFilename) {
        try {
            log.info("开始处理 PDF 文档: kbId={}, documentId={}, filename={}", kbId, documentId, originalFilename);

            Path path = documentStorageService.getFilePath(filePath);
            String draft;
            try (InputStream inputStream = Files.newInputStream(path)) {
                draft = pdfMarkdownConverter.convert(inputStream);
            }
            log.info("PDF 转 Markdown 草稿完成: documentId={}, 草稿 {} 字符", documentId, draft.length());

            if (!draft.trim().isEmpty()) {
                // 模型纠错
                String corrected = markdownCorrectionService.correct(draft);
                log.info("模型纠错完成: documentId={}, 纠错后 {} 字符", documentId, corrected.length());

                // 保存纠错后的 Markdown 文件（便于追溯）
                String baseName = originalFilename != null && originalFilename.contains(".")
                        ? originalFilename.substring(0, originalFilename.lastIndexOf("."))
                        : "document";
                try {
                    String mdPath = documentStorageService.saveTextFile(
                            kbId, documentId, baseName + ".md", corrected);
                    log.info("纠错后的 Markdown 已保存: documentId={}, path={}", documentId, mdPath);
                } catch (IOException e) {
                    log.warn("保存纠错后的 Markdown 失败（不影响入库）: documentId={}, error={}",
                            documentId, e.getMessage());
                }

                // 解析并生成 chunks
                processMarkdownText(kbId, documentId, corrected);
            } else {
                log.warn("PDF 未提取到任何文本: documentId={}", documentId);
                throw new BizException("PDF 未提取到任何文本，可能是扫描件或加密文档");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF 文档处理失败: documentId={}", documentId, e);
            throw new BizException("PDF 转换失败：" + e.getMessage());
        }
    }

    /**
     * 处理 Markdown 文档，解析并生成 chunks
     */
    private void processMarkdownDocument(String kbId, String documentId, String filePath) {
        try {
            log.info("开始处理 Markdown 文档: kbId={}, documentId={}, filePath={}", kbId, documentId, filePath);

            // 从保存的文件路径读取文件
            Path path = documentStorageService.getFilePath(filePath);
            String content;
            try (InputStream inputStream = Files.newInputStream(path)) {
                content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            processMarkdownText(kbId, documentId, content);
        } catch (Exception e) {
            log.error("处理 Markdown 文档失败: documentId={}", documentId, e);
            // 不抛出异常，避免影响文档上传流程
        }
    }

    /**
     * 解析 Markdown 文本并生成 chunks（Markdown 文件与 PDF 纠错结果共用）
     */
    private void processMarkdownText(String kbId, String documentId, String markdownText) {
        try (InputStream inputStream = new ByteArrayInputStream(markdownText.getBytes(StandardCharsets.UTF_8))) {
            // 解析 Markdown 文本
            List<MarkdownParserService.MarkdownSection> sections = markdownParserService.parseMarkdown(inputStream);

            if (sections.isEmpty()) {
                log.warn("Markdown 文档解析后没有找到任何章节: documentId={}", documentId);
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            int chunkCount = 0;

            // 为每个章节生成 chunk
            for (MarkdownParserService.MarkdownSection section : sections) {
                String title = section.getTitle();
                String content = section.getContent();

                if (title == null || title.trim().isEmpty()) {
                    continue;
                }

                // 对标题进行 embedding
                float[] embedding = ragService.embed(title);

                // 创建 ChunkBgeM3 实体
                ChunkBgeM3 chunk = ChunkBgeM3.builder()
                        .kbId(kbId)
                        .docId(documentId)
                        .content(content != null ? content : "")
                        .metadata(null) // 可以存储标题信息到 metadata
                        .embedding(embedding)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

                // 插入数据库
                int result = chunkBgeM3Mapper.insert(chunk);

                if (result > 0) {
                    chunkCount++;
                    log.debug("创建 chunk 成功: title={}, chunkId={}", title, chunk.getId());
                } else {
                    log.warn("创建 chunk 失败: title={}", title);
                }
            }
            log.info("Markdown 文档处理完成: documentId={}, 共生成 {} 个 chunks", documentId, chunkCount);
        } catch (Exception e) {
            log.error("解析 Markdown 文本失败: documentId={}", documentId, e);
        }
    }

    /**
     * 从文件名提取文件类型
     */
    private String getFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    @Override
    public void updateDocument(String documentId, UpdateDocumentRequest request) {
        try {
            // 查询现有的文档
            Document existingDocument = documentMapper.selectById(documentId);
            if (existingDocument == null) {
                throw new BizException("文档不存在: " + documentId);
            }

            // 将现有 Document 转换为 DocumentDTO
            DocumentDTO documentDTO = documentConverter.toDTO(existingDocument);

            // 使用 UpdateDocumentRequest 更新 DocumentDTO
            documentConverter.updateDTOFromRequest(documentDTO, request);

            // 将更新后的 DocumentDTO 转换回 Document 实体
            Document updatedDocument = documentConverter.toEntity(documentDTO);

            // 保留原有的 ID、kbId 和创建时间
            updatedDocument.setId(existingDocument.getId());
            updatedDocument.setKbId(existingDocument.getKbId());
            updatedDocument.setCreatedAt(existingDocument.getCreatedAt());
            updatedDocument.setUpdatedAt(LocalDateTime.now());

            // 更新数据库
            int result = documentMapper.updateById(updatedDocument);
            if (result <= 0) {
                throw new BizException("更新文档失败");
            }
        } catch (JsonProcessingException e) {
            throw new BizException("更新文档时发生序列化错误: " + e.getMessage());
        }
    }
}
