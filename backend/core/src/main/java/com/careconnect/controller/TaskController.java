package com.careconnect.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.careconnect.dto.v2.TaskDtoV2;
import com.careconnect.service.v2.TaskServiceV2;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskServiceV2 taskService;

    public TaskController(TaskServiceV2 taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/{patientId}")
    public ResponseEntity<?> createTask(
            @PathVariable Long patientId,
            @RequestBody TaskDtoV2 taskDto) {

        TaskDtoV2 createdTask = taskService.createTask(patientId, taskDto);

        return ResponseEntity.ok(createdTask);
    }

    @GetMapping("/{patientId}")
    public ResponseEntity<?> getTasks(@PathVariable Long patientId) {
        return ResponseEntity.ok("Task retrieval endpoint temporarily disabled");
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<?> deleteTask(@PathVariable Long taskId) {

        taskService.deleteTask(taskId, true);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Task deleted successfully");

        return ResponseEntity.ok(response);
    }
}