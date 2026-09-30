package com.example.demo.api.dto;

import com.example.demo.entity.SuggestionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResolveSuggestionRequest {

    @NotNull(message = "status is required (ACCEPTED or REJECTED)")
    private SuggestionStatus status;
}
