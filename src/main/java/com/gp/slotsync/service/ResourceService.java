package com.gp.slotsync.service;

import com.gp.slotsync.dto.ResourceRequest;
import com.gp.slotsync.dto.ResourceResponse;
import com.gp.slotsync.entity.Resource;
import com.gp.slotsync.exception.ResourceNotFoundException;
import com.gp.slotsync.repository.ResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResourceService {

    private final ResourceRepository resourceRepository;

    public ResourceService(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    @Transactional
    public ResourceResponse createResource(ResourceRequest request) {
        Resource resource = new Resource();
        resource.setName(request.name());
        resource.setType(request.type());
        resource.setActive(request.isActiveOrDefault());

        Resource saved = resourceRepository.save(resource);
        return ResourceResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> getAllResources(Boolean activeOnly) {
        List<Resource> resources = (activeOnly != null && activeOnly)
                ? resourceRepository.findByActiveTrue()
                : resourceRepository.findAll();
        return resources.stream().map(ResourceResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public ResourceResponse getResourceById(Long id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found with id: " + id));
        return ResourceResponse.fromEntity(resource);
    }

    @Transactional
    public ResourceResponse updateResource(Long id, ResourceRequest request) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found with id: " + id));

        resource.setName(request.name());
        resource.setType(request.type());
        if (request.active() != null) {
            resource.setActive(request.active());
        }

        Resource updated = resourceRepository.save(resource);
        return ResourceResponse.fromEntity(updated);
    }

    @Transactional
    public void deleteResource(Long id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found with id: " + id));
        resource.setActive(false);
        resourceRepository.save(resource);
    }
}
