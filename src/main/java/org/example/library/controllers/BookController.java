package org.example.library.controllers;

import jakarta.persistence.EntityNotFoundException;
import org.example.library.exceptions.ForbiddenAccessException;
import org.example.library.exceptions.ResourceNotFoundException;
import org.example.library.exceptions.UnauthorizedAccessException;
import org.example.library.models.DTO.BookDTO;
import org.example.library.models.*;
import org.example.library.services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
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
    @GetMapping("/search")
    public ResponseEntity<Page<BookDTO>> searchBooks(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Double minRating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<Book> books;

        if (query != null && !query.isEmpty()) {
            books = bookService.searchBooks(query, page, size);
        } else {
            books = bookService.getAllBooks(page, size);
        }

        List<Book> filteredBooks = books.getContent();

        if (minRating != null) {
            filteredBooks = filteredBooks.stream()
                    .filter(book -> bookService.calculateAverageRating(book.getBookId()) >= minRating)
                    .toList();
        }

        Long currentUserId = customUserDetailsService.getCurrentUser() != null
                ? customUserDetailsService.getCurrentUser().getId()
                : null;

        List<BookDTO> dtos = filteredBooks.stream()
                .map(book -> bookService.convertToDTO(book, currentUserId))
                .toList();

        Page<BookDTO> bookDTOs = new PageImpl<>(dtos, books.getPageable(), filteredBooks.size());

        return ResponseEntity.ok(bookDTOs);
    }

    @GetMapping("/all")
    public ResponseEntity<Page<BookDTO>> getAllBooks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<Book> books = bookService.getAllBooks(page, size);

        Long currentUserId = customUserDetailsService.getCurrentUser() != null
                ? customUserDetailsService.getCurrentUser().getId()
                : null;

        List<BookDTO> dtos = books.stream()
                .map(book -> bookService.convertToDTO(book, currentUserId))
                .toList();

        Page<BookDTO> bookDTOs = new PageImpl<>(dtos, books.getPageable(), books.getTotalElements());

        return ResponseEntity.ok(bookDTOs);
    }

    @GetMapping("/editable")
    public ResponseEntity<Page<BookDTO>> getEditableBooks(@RequestParam Long userId,
                                                          @RequestParam int page,
                                                          @RequestParam int size) {
        Page<Book> books = bookService.getBooksForEditing(userId, page, size);
        return ResponseEntity.ok(books.map(book -> bookService.convertToDTO(book, userId)));
    }


    // Получение книги по ID
    @GetMapping("/{id}")
    public ResponseEntity<BookDTO> getBookById(@PathVariable Long id) {
        Book book = bookService.getBookById(id);
        if (book == null) {
            return ResponseEntity.notFound().build();
        }
        Long currentUserId = customUserDetailsService.getCurrentUser() != null
                ? customUserDetailsService.getCurrentUser().getId()
                : null;
        return ResponseEntity.ok(bookService.convertToDTO(book, currentUserId));
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
            // Получаем путь к корню проекта
            String projectRoot = new File("").getAbsolutePath();
            String directory = projectRoot + "/src/main/resources/Storage";

            File dir = new File(directory);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Не удалось создать директорию: " + dir.getAbsolutePath());
            }

            String fileName = StringUtils.cleanPath(file.getOriginalFilename());
            File destinationFile = new File(dir, fileName);

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





    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<BookDTO>> getBooksForUser  (
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false, defaultValue = "title") String sort,
            @RequestParam(required = false, defaultValue = "asc") String direction,
            @RequestParam(required = false) Integer minRating) { // Добавляем параметр minRating

        LibraryUser  user = userService.findById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        List<BookDTO> bookDTOs = bookService.getBooksForUser (user);

        // Фильтрация по названию и авторам
        if (filter != null && !filter.isEmpty()) {
            String lowerCaseFilter = filter.toLowerCase();
            bookDTOs = bookDTOs.stream().filter(bookDTO ->
                    bookDTO.getTitle().toLowerCase().contains(lowerCaseFilter) ||
                            bookDTO.getAuthorNames().stream()
                                    .map(String::toLowerCase) // Приведение к нижнему регистру
                                    .anyMatch(authorName -> authorName.contains(lowerCaseFilter))
            ).collect(Collectors.toList());
        }

        // Фильтрация по рейтингу
        if (minRating != null) {
            bookDTOs = bookDTOs.stream()
                    .filter(bookDTO -> bookDTO.getAverageRating() >= minRating)
                    .collect(Collectors.toList());
        }

        // Сортировка
        Comparator<BookDTO> comparator = switch (sort) {
            case "year" -> Comparator.comparing(BookDTO::getPublicationYear);
            case "title" -> Comparator.comparing(BookDTO::getTitle, String.CASE_INSENSITIVE_ORDER);
            case "author" -> Comparator.comparing(bookDTO ->
                    String.join(", ", bookDTO.getAuthorNames()), String.CASE_INSENSITIVE_ORDER);
            case "publisher" -> Comparator.comparing(BookDTO::getPublisher, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(BookDTO::getTitle, String.CASE_INSENSITIVE_ORDER);
        };

        if ("desc".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        }

        bookDTOs.sort(comparator);

        // Пагинация
        int start = (int) PageRequest.of(page, size).getOffset();
        int end = Math.min(start + size, bookDTOs.size());
        Page<BookDTO> bookPage = new PageImpl<>(bookDTOs.subList(start, end), PageRequest.of(page, size), bookDTOs.size());

        return ResponseEntity.ok(bookPage);
    }





    @GetMapping("/edit/{id}")
    public ResponseEntity<BookDTO> editBook(@PathVariable Long id) {
        Long currentUserId = customUserDetailsService.getCurrentUser () != null
                ? customUserDetailsService.getCurrentUser ().getId()
            : null;

        try {
            BookDTO bookDTO = bookService.editBook(id, currentUserId);
            return ResponseEntity.ok(bookDTO);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (UnauthorizedAccessException | ForbiddenAccessException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
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

        LibraryUser currentUser = userService.findByUsername(authentication.getName());

        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Пользователь не найден.");
        }

        boolean isAuthorized = currentUser.getRoles().stream()
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

        // Обновление связей BookAuthor
        for (BookAuthor bookAuthor : book.getBookAuthors()) {
            bookAuthor.setAuthor(author);
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

    @PostMapping("/user/{userId}/last-read/{bookId}")
    public ResponseEntity<Void> setLastReadBook(
            @PathVariable Long userId,
            @PathVariable Long bookId) {

        Book book = bookService.getBookById(bookId);
        if (book == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            userService.updateLastReadBook(userId, bookId);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }


    @GetMapping("/user/{userId}/last-read")
    public ResponseEntity<BookDTO> getLastReadBook(@PathVariable Long userId) {
        LibraryUser user = userService.findById(userId);
        Book lastReadBook = user.getLastReadBook();
        if (lastReadBook == null) {
            return ResponseEntity.noContent().build();
        }
        Book book = bookService.getBookById(lastReadBook.getBookId());
        if (book == null) {
            return ResponseEntity.noContent().build();
        }


        Long currentUserId = customUserDetailsService.getCurrentUser() != null
                ? customUserDetailsService.getCurrentUser().getId()
                : null;

        return ResponseEntity.ok(bookService.convertToDTO(book, currentUserId));
    }


    @GetMapping("/by-author/{authorId}")
    public ResponseEntity<Page<BookDTO>> getBooksByAuthor(
            @PathVariable Long authorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<Book> books = bookService.getBooksByAuthor(authorId, page, size);
        Long currentUserId = customUserDetailsService.getCurrentUser() != null
                ? customUserDetailsService.getCurrentUser().getId()
                : null;

        List<BookDTO> dtos = books.stream()
                .map(book -> bookService.convertToDTO(book, currentUserId))
                .toList();

        Page<BookDTO> bookDTOs = new PageImpl<>(dtos, books.getPageable(), books.getTotalElements());
        return ResponseEntity.ok(bookDTOs);
    }

}
