package org.example.library.services;

import org.example.library.exceptions.UserAlreadyExistsException;
import org.example.library.models.Faculty;
import org.example.library.models.FacultyType;
import org.example.library.models.LibraryUser ;
import org.example.library.models.Role;
import org.example.library.repositories.FacultyRepository; // Импортируйте FacultyRepository
import org.example.library.repositories.LibraryUserRepository;
import org.example.library.repositories.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private LibraryUserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private FacultyRepository facultyRepository; // Добавьте зависимость

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    public LibraryUser  registerUser (String username, String email, String rawPassword) {
        if (userRepository.findByUsername(username) != null) {
            throw new UserAlreadyExistsException("Пользователь с таким именем уже существует");
        }
        if (userRepository.findByEmail(email) != null) {
            throw new UserAlreadyExistsException("Пользователь с таким email уже существует");
        }

        LibraryUser  user = new LibraryUser ();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword)); // Хешируем пароль

        // Установка роли "STUDENT" по умолчанию
        Role studentRole = roleRepository.findByRoleName("STUDENT");
        if (studentRole != null) {
            user.setRoles(new HashSet<>(Collections.singletonList(studentRole))); // Используем Set
        }

        // Получение факультета "Общедоступный"
        List<Faculty> faculties = facultyRepository.findByType(FacultyType.COMMON);
        Faculty commonFaculty = faculties.isEmpty() ? null : faculties.getFirst(); // Получаем первый факультет, если он есть

        if (commonFaculty == null) {
            // Если факультет не найден, создаем его
            commonFaculty = new Faculty();
            commonFaculty.setType(FacultyType.COMMON);
            facultyRepository.save(commonFaculty);
        }

        // Добавление факультета "Общедоступный" к пользователю
        user.getFaculties().add(commonFaculty); // Добавляем факультет "Общедоступный"

        return userRepository.save(user);
    }



    public LibraryUser  authenticateUser (String username, String password, String role) {
        LibraryUser  user = userRepository.findByUsername(username);
        if (user == null) {
            logger.warn("Пользователь с именем {} не найден", username);
            return null; // Неудачная аутентификация
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            logger.warn("Неверный пароль для пользователя {}", username);
            return null; // Неудачная аутентификация
        }

        // Проверка роли
        Role userRole = roleRepository.findByRoleName(role);
        if (userRole == null || !user.getRoles().contains(userRole)) {
            logger.warn("Пользователь {} не имеет доступа с ролью {}", username, role);
            return null; // Неудачная аутентификация, т.к. Пользователь не имеет указанной роли
        }

        return user; // Успешная аутентификация
    }


    public LibraryUser findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public LibraryUser findById(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }

}
