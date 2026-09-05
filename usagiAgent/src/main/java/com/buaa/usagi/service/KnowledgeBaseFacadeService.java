package com.buaa.usagi.service;

import com.buaa.usagi.model.request.CreateKnowledgeBaseRequest;
import com.buaa.usagi.model.request.UpdateKnowledgeBaseRequest;
import com.buaa.usagi.model.response.CreateKnowledgeBaseResponse;
import com.buaa.usagi.model.response.GetKnowledgeBasesResponse;

public interface KnowledgeBaseFacadeService {
    GetKnowledgeBasesResponse getKnowledgeBases();

    CreateKnowledgeBaseResponse createKnowledgeBase(CreateKnowledgeBaseRequest request);

    void deleteKnowledgeBase(String knowledgeBaseId);

    void updateKnowledgeBase(String knowledgeBaseId, UpdateKnowledgeBaseRequest request);
}

