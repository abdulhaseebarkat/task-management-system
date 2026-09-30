package com.slmtires.itms.service;

import com.slmtires.itms.dto.CategoryManagementResponse;
import com.slmtires.itms.dto.CategoryRequest;
import com.slmtires.itms.entity.TaskCategory;
import com.slmtires.itms.exception.ConflictException;
import com.slmtires.itms.exception.ResourceNotFoundException;
import com.slmtires.itms.repository.TaskCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin-only category management. Categories are never hard-deleted (a task's FK would forbid it
 * anyway) - "deactivate" is the only removal path, same pattern as deactivating a user: it stops
 * showing up as a choice for NEW work (task creation, or changing an existing task's category -
 * see TaskService#applyFields), but every task that already has it keeps it untouched.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {
    private final TaskCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryManagementResponse> listAll() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
            .map(CategoryService::toResponse)
            .toList();
    }

    @Transactional
    public CategoryManagementResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A category with this name already exists.");
        }
        TaskCategory category = new TaskCategory();
        category.setName(name);
        category.setActive(true);
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryManagementResponse rename(Long id, CategoryRequest request) {
        TaskCategory category = findOrThrow(id);
        String name = request.name().trim();
        if (!category.getName().equalsIgnoreCase(name) && categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A category with this name already exists.");
        }
        category.setName(name);
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryManagementResponse setActive(Long id, boolean active) {
        TaskCategory category = findOrThrow(id);
        category.setActive(active);
        return toResponse(categoryRepository.save(category));
    }

    private TaskCategory findOrThrow(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found."));
    }

    private static CategoryManagementResponse toResponse(TaskCategory category) {
        return new CategoryManagementResponse(category.getId(), category.getName(), category.isActive());
    }
}
