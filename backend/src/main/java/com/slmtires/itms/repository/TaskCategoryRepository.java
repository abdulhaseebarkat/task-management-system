package com.slmtires.itms.repository;

import com.slmtires.itms.entity.TaskCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskCategoryRepository extends JpaRepository<TaskCategory, Long> {
    List<TaskCategory> findAllByActiveTrueOrderByNameAsc();

    List<TaskCategory> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
