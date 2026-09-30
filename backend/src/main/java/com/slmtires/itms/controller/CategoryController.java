package com.slmtires.itms.controller;

import com.slmtires.itms.dto.CategoryManagementResponse;
import com.slmtires.itms.dto.CategoryRequest;
import com.slmtires.itms.dto.CategoryResponse;
import com.slmtires.itms.dto.UpdateUserStatusRequest;
import com.slmtires.itms.service.CategoryService;
import com.slmtires.itms.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task-categories")
@RequiredArgsConstructor
public class CategoryController {
    private final TaskService taskService;
    private final CategoryService categoryService;

    /** Every authenticated user - active categories only, for task-creation/filter dropdowns. */
    @GetMapping
    public List<CategoryResponse> list() {
        return taskService.listCategories();
    }

    /** Admin-only management view - includes inactive categories, which the plain list above never does. */
    @GetMapping("/manage")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CategoryManagementResponse> listForManagement() {
        return categoryService.listAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryManagementResponse create(@Valid @RequestBody CategoryRequest request) {
        return categoryService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryManagementResponse rename(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.rename(id, request);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryManagementResponse setStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return categoryService.setActive(id, request.active());
    }
}
