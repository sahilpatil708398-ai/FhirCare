package com.fhircare.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccessDecisionRequest {
    private String decision; // APPROVED or REJECTED
}
