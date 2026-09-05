package com.buaa.usagi.service;

import com.buaa.usagi.model.request.CreateDocumentRequest;
import com.buaa.usagi.model.request.UpdateDocumentRequest;
import com.buaa.usagi.model.response.CreateDocumentResponse;
import com.buaa.usagi.model.response.GetDocumentsResponse;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentFacadeService {
    GetDocumentsResponse getDocuments();

    GetDocumentsResponse getDocumentsByKbId(String kbId);

    CreateDocumentResponse createDocument(CreateDocumentRequest request);

    CreateDocumentResponse uploadDocument(String kbId, MultipartFile file);

    void deleteDocument(String documentId);

    void updateDocument(String documentId, UpdateDocumentRequest request);
}
