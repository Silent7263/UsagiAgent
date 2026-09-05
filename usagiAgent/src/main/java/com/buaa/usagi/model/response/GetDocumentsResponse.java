package com.buaa.usagi.model.response;

import com.buaa.usagi.model.vo.DocumentVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetDocumentsResponse {
    private DocumentVO[] documents;
}

