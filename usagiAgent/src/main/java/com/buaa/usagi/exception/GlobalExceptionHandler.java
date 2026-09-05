package com.buaa.usagi.exception;

import com.buaa.usagi.model.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    /**
     * 捕获业务异常，错误信息返回给前端
     */
    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException e) {
        return ApiResponse.error(e.getMessage());
    }

    /**
     * 处理 404 错误
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handle404(NoResourceFoundException e) {
        return ResponseEntity.notFound().build();
    }

    /**
     * 捕获大模型服务调用异常（如 API Key 无效导致的 401），给前端明确提示
     */
    @ExceptionHandler(NonTransientAiException.class)
    public ApiResponse<Void> handleAiException(NonTransientAiException e) {
        log.error("大模型服务调用失败", e);
        String msg = e.getMessage();
        if (msg != null && (msg.contains("401")
                || msg.contains("Authentication")
                || msg.contains("authentication_error")
                || msg.contains("invalid"))) {
            return ApiResponse.error("大模型服务鉴权失败：API Key 未配置或无效，请在 application.yaml 中配置正确的 api-key 后重启服务");
        }
        return ApiResponse.error("大模型服务调用失败，请稍后重试");
    }

    /**
     * 捕获上传文件过大异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件过大: {}", e.getMessage());
        return ApiResponse.error("上传文件过大，单文件最大 50MB");
    }

    /**
     * 捕获所有未处理的异常, 对前端不返回错误信息
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("服务器内部错误", e);
        return ApiResponse.error("服务器内部错误");
    }
}
