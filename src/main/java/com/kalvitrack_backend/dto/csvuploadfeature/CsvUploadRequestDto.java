package com.kalvitrack_backend.dto.csvuploadfeature;

import com.kalvitrack_backend.dto.csvuploadfeature.StudentCsvRowDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CsvUploadRequestDto {

    @NotBlank(message = "Uploaded by is required")
    private String uploadedBy;

    @NotNull(message = "Student data is required")
    private List<StudentCsvRowDto> students;

    private String batchId;
}