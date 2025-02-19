package org.example.library.controllers;

import org.example.library.models.Book;
import org.example.library.models.LibraryUser ;
import org.example.library.models.Role;
import org.example.library.services.BookService; // Импортируйте BookService
import org.example.library.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class ViewController {

    @Autowired
    private UserService userService; // Сервис для работы с пользователями

    @Autowired
    private BookService bookService; // Сервис для работы с книгами

    @GetMapping("/")
    public String home(Model model) {
        return "index"; // Возврат имени шаблона для отображения домашней страницы
    }

    @GetMapping("/books")
    public String viewBooks(Model model) {
        return "books"; // Возврат имени шаблона для отображения списка книг
    }

    @GetMapping("/books/read")
    public String readBooks(Model model) {
        return "reader"; // Возврат имени шаблона для отображения списка книг
    }

    @GetMapping("/register")
    public String register(Model model) {
        return "register"; // Возврат имени шаблона для страницы регистрации
    }

    @GetMapping("/login")
    public String login(Model model) {
        return "login"; // Возврат имени шаблона для страницы авторизации
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public String adminPage(Model model) {
        return "admin"; // Возврат имени шаблона для отображения страницы администратора
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/add-book")
    public String addBook(Model model) {
        return "addbook"; // Возврат имени шаблона для отображения страницы преподавателя
    }

    // Метод для добавления текущего пользователя в модель
    private void addCurrentUserToModel(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            LibraryUser  user = userService.findByUsername(username);
            model.addAttribute("currentUser", user); // Убедитесь, что пробел после "currentUser " отсутствует
        }
    }

    @GetMapping("/actions")
    public String getActionsPage(Model model, Principal principal) {
        LibraryUser  currentUser  = userService.findByUsername(principal.getName());
        List<String> roles = currentUser .getRoles().stream()
                .map(Role::getRoleName)
                .collect(Collectors.toList());
        model.addAttribute("roles", roles);
        return "actions"; // Возвращает имя шаблона (например, actions.html)
    }


    @GetMapping("/books-edit")
    public String editBooks() {
        return "books-edit"; // Это будет искать файл в src/main/resources/templates
    }

    @GetMapping("/books/edit")
    public String editBook(@RequestParam("id") Long bookId, Model model) {
        Book book = bookService.getBookById(bookId); // Используйте getBookById
        if (book != null) {
            model.addAttribute("book", book); // Добавьте книгу в модель, чтобы использовать в шаблоне
            return "editbook"; // Возврат имени шаблона для редактирования книги
        } else {
            return "redirect:/books-edit"; // Перенаправление, если книга не найдена
        }
    }

}
