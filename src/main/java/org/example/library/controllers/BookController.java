package org.example.library.controllers;

import org.example.library.models.DTO.BookDTO;
import org.example.library.models.*;
import org.example.library.services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/books")
public class BookController {

    @Autowired
    private BookService bookService;

    @Autowired
    private AuthorService authorService;

    @Autowired
    private UserService userService;

    @Autowired
    private FacultyService facultyService; // Сервис для работы с факультетами

    @Autowired
    private CustomUserDetailsService customUserDetailsService;  // Внедрение зависимости


    // Получение всех книг
    @GetMapping("/all")
    public ResponseEntity<List<BookDTO>> getAllBooks(Authentication authentication) {
        LibraryUser currentUser = userService.findByUsername(authentication.getName());
        List<Book> books = bookService.getBooksForUser(currentUser); // Получаем книги для текущего пользователя
        List<BookDTO> bookDTOs = books.stream().map(this::convertToDTO).collect(Collectors.toList());
        return ResponseEntity.ok(bookDTOs);
    }


    // Поиск книг по заголовку
    @GetMapping("/search")
    public ResponseEntity<List<BookDTO>> searchBooks(@RequestParam String title) {
        List<Book> books = bookService.searchBooks(title);
        List<BookDTO> bookDTOs = books.stream().map(this::convertToDTO).collect(Collectors.toList());
        return ResponseEntity.ok(bookDTOs);
    }

    // Получение книги по ID
    @GetMapping("/{id}")
    public ResponseEntity<BookDTO> getBookById(@PathVariable Long id) {
        Book book = bookService.getBookById(id);
        return book != null ? ResponseEntity.ok(convertToDTO(book)) : ResponseEntity.notFound().build();
    }



    // Загрузка новой книги
    @PostMapping("/upload")
    public ResponseEntity<String> uploadBook(
            @RequestParam String title,
            @RequestParam String isbn,
            @RequestParam int publicationYear,
            @RequestParam String description,
            @RequestParam String publisher,
            @RequestParam String authorFirstName,
            @RequestParam String authorLastName,
            @RequestParam MultipartFile file,
            @RequestParam List<Long> facultyIds,
            Authentication authentication) {

        LibraryUser  currentUser  = userService.findByUsername(authentication.getName());

        if (currentUser  == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Пользователь не найден.");
        }

        boolean isAuthorized = currentUser .getRoles().stream()
                .anyMatch(role -> role.getRoleName().equals("TEACHER") || role.getRoleName().equals("ADMIN"));

        if (!isAuthorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("У вас нет прав для добавления книги.");
        }

        if (file.isEmpty() || file.getOriginalFilename() == null) {
            return ResponseEntity.badRequest().body("Файл не должен быть пустым и должен иметь имя.");
        }

        String fileLocation;
        try {
            fileLocation = saveFile(file);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при сохранении файла: " + e.getMessage());
        }

        Author author = authorService.findByFirstNameAndLastName(authorFirstName, authorLastName);
        if (author == null) {
            author = new Author();
            author.setFirstName(authorFirstName);
            author.setLastName(authorLastName);
            authorService.saveAuthor(author);
        }

        Book book = new Book();
        book.setTitle(title);
        book.setIsbn(isbn);
        book.setPublicationYear(publicationYear);
        book.setDescription(description);
        book.setPublisher(publisher);
        book.setStatus(BookStatus.AVAILABLE);

        if (facultyIds == null || facultyIds.isEmpty()) {
            return ResponseEntity.badRequest().body("Необходимо выбрать хотя бы один факультет.");
        }

        Set<Faculty> faculties = facultyIds.stream()
                .map(facultyService::findById)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (faculties.isEmpty()) {
            return ResponseEntity.badRequest().body("Некоторые факультеты не найдены.");
        }

        book.setFaculties(faculties);

        BookAuthor bookAuthor = new BookAuthor();
        bookAuthor.setAuthor(author);
        bookAuthor.setBook(book);
        book.setBookAuthors(Collections.singletonList(bookAuthor));

        try {
            bookService.uploadBook(book, currentUser ); // Передаем текущего пользователя
            Ebook ebook = new Ebook();
            ebook.setFileLocation(fileLocation);
            ebook.setBook(book);
            bookService.saveEbook(ebook);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при сохранении книги: " + e.getMessage());
        }

        return ResponseEntity.ok("Книга успешно добавлена!");
    }


    // Сохранение файла на сервере
    private String saveFile(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) {
            throw new IllegalArgumentException("Файл не должен быть null и должен иметь имя.");
        }

        try {
            String directory = "C:\\Users\\Дмитрий\\Desktop\\ProjectLibrary\\src\\main\\resources\\Storage";
            File dir = new File(directory);

            // Создаем директорию, если она не существует
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Не удалось создать директорию: " + dir.getAbsolutePath());
            }

            String fileName = file.getOriginalFilename();
            File destinationFile = new File(dir, fileName);

            // Сохраняем файл
            file.transferTo(destinationFile);

            return destinationFile.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при сохранении файла: " + e.getMessage(), e);
        }
    }


    // Получение содержимого книги
    @GetMapping("/{id}/content")
    public ResponseEntity<FileSystemResource> getBookContent(@PathVariable Long id) throws IOException {
        // Получите книгу из базы данных
        Book book = bookService.getBookById(id);
        if (book == null || book.getEbooks().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Ebook ebook = book.getEbooks().getFirst(); // Получаем первый eBook
        System.out.println("Путь к файлу: " + ebook.getFileLocation()); // Добавлено для отладки
        File pdfFile = new File(ebook.getFileLocation());

        if (!pdfFile.exists()) {
            return ResponseEntity.notFound().build();
        }

        // Кодируем имя файла
        String encodedFileName = URLEncoder.encode(pdfFile.getName(), StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + encodedFileName + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new FileSystemResource(pdfFile));
    }

    // Метод для скачивания электронной книги
    @GetMapping("/download/{id}")
    public ResponseEntity<Void> downloadEbook(@PathVariable Long id) {
        bookService.downloadEbook(id);
        return ResponseEntity.ok().build();
    }

    // Преобразование книги в DTO
    private BookDTO convertToDTO(Book book) {
        List<String> authorNames = book.getBookAuthors().stream()
                .map(bookAuthor -> bookAuthor.getAuthor().getFirstName() + " " + bookAuthor.getAuthor().getLastName())
                .collect(Collectors.toList());

        return new BookDTO(
                book.getBookId(),
                book.getTitle(),
                book.getIsbn(),
                book.getPublicationYear(),
                book.getDescription(),
                book.getPublisher(),
                book.getStatus().name(), // Преобразуем статус в строку
                authorNames
        );
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BookDTO>> getBooksForUser(@PathVariable Long userId) {
        System.out.println("Полученный userId: " + userId);

        if (userId <= 0) {
            return ResponseEntity.badRequest().body(Collections.emptyList()); // Неверный userId
        }

        LibraryUser currentUser = userService.findById(userId);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }

        try {
            List<Book> books = bookService.getBooksForUser(currentUser);
            List<BookDTO> bookDTOs = books.stream().map(this::convertToDTO).collect(Collectors.toList());
            return ResponseEntity.ok(bookDTOs);
        } catch (Exception e) {
            // Логируем ошибку (например, через Logger)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }


// Код из файла: C:\Users\Дмитрий\IdeaProjects\ProjectLibrary\src\main\java\org\example\library\controllers\BookController.java

    @PreAuthorize("hasRole('ADMIN') or (hasRole('TEACHER') and @bookService.isBookAddedByUser (#id, principal.username))")
    @GetMapping("/edit/{id}")
    public ResponseEntity<BookDTO> editBook(@PathVariable Long id) {
        Book book = bookService.getBookById(id);
        return ResponseEntity.ok(convertToDTO(book));
    }

    @PreAuthorize("hasRole('ADMIN') or (hasRole('TEACHER') and @bookService.isBookAddedByUser  (#bookId, principal.username))")
    @PostMapping("/update")
    public ResponseEntity<String> updateBook(
            @RequestParam Long bookId,
            @RequestParam String title,
            @RequestParam String isbn,
            @RequestParam int publicationYear,
            @RequestParam String description,
            @RequestParam String publisher,
            @RequestParam String authorFirstName,
            @RequestParam String authorLastName,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam List<Long> facultyIds,
            Authentication authentication) {

        LibraryUser  currentUser  = userService.findByUsername(authentication.getName());

        if (currentUser  == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Пользователь не найден.");
        }

        boolean isAuthorized = currentUser .getRoles().stream()
                .anyMatch(role -> role.getRoleName().equals("TEACHER") || role.getRoleName().equals("ADMIN"));

        if (!isAuthorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("У вас нет прав для обновления книги.");
        }

        Book book = bookService.getBookById(bookId);
        if (book == null) {
            return ResponseEntity.notFound().build();
        }

        // Обновление полей книги
        book.setTitle(title);
        book.setIsbn(isbn);
        book.setPublicationYear(publicationYear);
        book.setDescription(description);
        book.setPublisher(publisher);

        // Обработка файла, если он был загружен
        if (file != null && !file.isEmpty()) {
            try {
                String fileLocation = saveFile(file);
                Ebook ebook = new Ebook();
                ebook.setFileLocation(fileLocation);
                ebook.setBook(book);
                bookService.saveEbook(ebook);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при сохранении файла: " + e.getMessage());
            }
        }

        // Обновление авторов
        Author author = authorService.findByFirstNameAndLastName(authorFirstName, authorLastName);
        if (author == null) {
            author = new Author();
            author.setFirstName(authorFirstName);
            author.setLastName(authorLastName);
            authorService.saveAuthor(author);
        }

        // Обновление факультетов
        Set<Faculty> faculties = facultyIds.stream()
                .map(facultyService::findById)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (faculties.isEmpty()) {
            return ResponseEntity.badRequest().body("Некоторые факультеты не найдены.");
        }

        book.setFaculties(faculties);
        bookService.updateBook(book); // Сохранение обновленной книги

        return ResponseEntity.ok("Книга успешно обновлена!");
    }



}
