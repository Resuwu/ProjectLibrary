package org.example.library.controllers;

import org.example.library.models.Book;
import org.example.library.models.DTO.UserDTO;
import org.example.library.models.DTO.UserRoleUpdateDTO;
import org.example.library.models.LibraryUser ;
import org.example.library.services.AdminService;
import org.example.library.services.BookService; // Импортируйте BookService
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.example.library.models.Role; // Добавьте этот импорт


import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private BookService bookService; // Внедряем BookService

    @PreAuthorize("hasRole('ADMIN')") // Ограничение доступа для администраторов
    @PostMapping("/users")
    public ResponseEntity<LibraryUser > createUser (@RequestBody LibraryUser  user) {
        LibraryUser  createdUser  = adminService.createUser (user);
        return ResponseEntity.ok(createdUser );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/assign-role/{userId}/{roleId}")
    public ResponseEntity<Void> assignRoleToUser (@PathVariable Long userId, @PathVariable Long roleId) {
        System.out.println("Попытка назначения роли пользователю с ID: " + userId + " и ролью с ID: " + roleId);
        adminService.assignRoleToUser (userId, roleId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("Authenticated user: " + authentication.getName());
        System.out.println("Authorities: " + authentication.getAuthorities());

        List<LibraryUser > users = adminService.getAllUsers();

        // Преобразование LibraryUser  в UserDTO
        List<UserDTO> userDTOs = users.stream()
                .map(user -> {
                    UserDTO dto = new UserDTO();
                    dto.setUserId(user.getUserId());
                    dto.setUsername(user.getUsername());
                    dto.setEmail(user.getEmail());
                    // Преобразуйте роли в список строк
                    dto.setRoles(user.getRoles().stream()
                            .map(Role::getRoleName) // Получаем имя роли
                            .collect(Collectors.toList()));
                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(userDTOs);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/assign-faculty/{userId}/{facultyId}")
    public ResponseEntity<Void> assignFacultyToUser (@PathVariable Long userId, @PathVariable Long facultyId) {
        adminService.assignFacultyToUser (userId, facultyId);
        return ResponseEntity.ok().build();
    }


    @PreAuthorize("hasRole('ADMIN')") // Ограничение доступа для администраторов
    @PostMapping("/add-book")
    public ResponseEntity<Book> addBook(@RequestParam String title, @RequestParam Long authorId) {
        Book book = new Book();
        book.setTitle(title);
        // Установите другие свойства книги, если нужно

        // Получаем текущего аутентифицированного пользователя
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        LibraryUser  addedBy = (LibraryUser ) authentication.getPrincipal(); // Предполагается, что вы используете LibraryUser  как UserDetails

        // Сохраняем книгу с записью о добавлении
        Book savedBook = bookService.uploadBook(book, addedBy);

        return ResponseEntity.ok(savedBook);
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/update-roles")
    public ResponseEntity<Void> updateRoles(@RequestBody List<UserRoleUpdateDTO> updates) {
        adminService.updateUserRoles(updates);
        return ResponseEntity.ok().build();
    }


}