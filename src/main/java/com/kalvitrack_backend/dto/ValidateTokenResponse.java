package com.kalvitrack_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor

public class ValidateTokenResponse
{
    private boolean valid;
    private String message;
    private String email;
}
