package com.kalvitrack_backend.dto.csvuploadfeature;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvUploadResponseDto {
    private boolean success;
    private String message;
    private String batchId;
    private int totalRecords;
    private int successfulRecords;
    private int failedRecords;
    private List<String> errors;

    public static CsvUploadResponseDto success(String batchId, int totalRecords, int successfulRecords) {
        return CsvUploadResponseDto.builder()
                .success(true)
                .message("CSV upload completed")
                .batchId(batchId)
                .totalRecords(totalRecords)
                .successfulRecords(successfulRecords)
                .failedRecords(totalRecords - successfulRecords)
                .build();
    }

    public static CsvUploadResponseDto failure(String message, List<String> errors) {
        return CsvUploadResponseDto.builder()
                .success(false)
                .message(message)
                .errors(errors)
                .totalRecords(0)
                .successfulRecords(0)
                .failedRecords(0)
                .build();
    }
}