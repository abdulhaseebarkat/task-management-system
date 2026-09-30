package com.slmtires.itms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskCommentRequest(
    @NotBlank(message = "A comment cannot be empty.") @Size(max = 4000) String comment
) {}
