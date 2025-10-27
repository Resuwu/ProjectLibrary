package org.example.library.controllers;

import org.example.library.models.Faculty;
import org.example.library.models.FacultyType;
import org.example.library.services.FacultyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/faculties")
public class FacultyController {

    @Autowired
    private FacultyService facultyService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getFaculties() {
        List<Map<String, Object>> faculties = Arrays.stream(FacultyType.values())
                .map(facultyType -> {
                    Map<String, Object> facultyMap = new HashMap<>();
                    facultyMap.put("facultyId", getFacultyId(facultyType)); // Получаем уникальный идентификатор
                    facultyMap.put("type", facultyType.getDisplayName());
                    return facultyMap;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(faculties);
    }

    // Метод для получения уникального идентификатора для каждого типа факультета
    private Long getFacultyId(FacultyType facultyType) {
        return switch (facultyType) {
            case COMMON -> 1L;
            case ARTS -> 2L;
            case ENGINEERING -> 3L;
            case BUSINESS -> 4L;
            case SCIENCE -> 5L;
        };
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/add")
    public ResponseEntity<Faculty> addFaculty(@RequestBody Faculty faculty) {
        Faculty createdFaculty = facultyService.save(faculty);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdFaculty);
    }

}
