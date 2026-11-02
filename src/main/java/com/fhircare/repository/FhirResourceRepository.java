package com.fhircare.repository;

import com.fhircare.entity.FhirResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FhirResourceRepository extends JpaRepository<FhirResource, Long> {
    Optional<FhirResource> findByResourceTypeAndResourceId(String resourceType, String resourceId);
}
