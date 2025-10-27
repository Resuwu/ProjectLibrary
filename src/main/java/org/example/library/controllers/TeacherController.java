package org.example.library.controllers;

import org.example.library.models.Book;
import org.example.library.models.BookEntry;
import org.example.library.models.LibraryUser ;
import org.example.library.services.BookService;
import org.example.library.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    @Autowired
    private BookService bookService;

    @Autowired
    private UserService userService;

    @PreAuthorize("hasRole('TEACHER')") // Ограничение доступа для преподавателей
    @GetMapping("/books")
    public ResponseEntity<Page<Book>> getAllBooks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Book> books = bookService.getAllBooks(page, size); // Используем пагинацию
        return ResponseEntity.ok(books);
    }

    @PreAuthorize("hasRole('TEACHER')") // Ограничение доступа для преподавателей
    @PostMapping("/add-book")
    public ResponseEntity<Book> addBook(@RequestBody Book newBook, Authentication authentication) {
        // Получаем текущего пользователя
        LibraryUser currentUser = userService.findByUsername(authentication.getName());

        // Создаем запись о добавлении книги
        BookEntry bookEntry = new BookEntry();
        bookEntry.setAddedBy(currentUser);
        bookEntry.setAddedAt(LocalDateTime.now());

        // Устанавливаем запись в книгу
        newBook.setEntry(bookEntry); // Устанавливаем связь между книгой и записью

        // Сохраняем книгу
        bookService.uploadBook(newBook, currentUser); // Передаем текущего пользователя, если это необходимо

        return ResponseEntity.status(HttpStatus.CREATED).body(newBook);
    }



    // Другие методы для преподавателя можно добавить здесь
}
