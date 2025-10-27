package org.example.library.models.DTO;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UserDTO {
    private Long userId;
    private String username;
    private String email;
    private List<String> roles;
    private List<Map<String, Object>> faculties; // Список факультетов, связанных с пользователем
}

