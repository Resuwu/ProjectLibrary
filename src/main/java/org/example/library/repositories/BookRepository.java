package org.example.library.repositories;

import org.example.library.models.Book;
import org.example.library.models.BookStatus;
import org.example.library.models.Faculty;
import org.example.library.models.FacultyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    @Query("SELECT b FROM Book b JOIN b.bookAuthors ba JOIN ba.author a WHERE b.title LIKE %:title% AND a.lastName LIKE %:author%")
    Page<Book> findByTitleContainingAndBookAuthors_Author_LastNameContaining(String title, String author, Pageable pageable);

    @Query("SELECT b FROM Book b JOIN b.bookAuthors ba JOIN ba.author a WHERE b.title LIKE %:query% OR a.firstName LIKE %:query% OR a.lastName LIKE %:query%")
    Page<Book> findByTitleContainingOrBookAuthors_Author_FirstNameContainingOrBookAuthors_Author_LastNameContaining(String query, Pageable pageable);

    Page<Book> findByTitleContaining(String title, Pageable pageable);
    Page<Book> findByBookAuthors_Author_LastNameContaining(String author, Pageable pageable);
    // Метод для получения списка выданных книг
    @Query("SELECT b FROM Book b WHERE b.status = :status")
    List<Book> findRentedBooks(BookStatus status); // Используем статус для фильтрации



    // Новый метод для получения книг по факультету
    @Query("SELECT b FROM Book b JOIN b.faculties f WHERE f = :faculty")
    List<Book> findByFaculty(Faculty faculty);

    // Метод для получения книг по типу факультета (например, общедоступный)
    @Query("SELECT b FROM Book b JOIN b.faculties f WHERE f.type = :facultyType")
    List<Book> findByFacultiesType(FacultyType facultyType);

    // Метод для получения книг по множеству факультетов
    @Query("SELECT DISTINCT b FROM Book b JOIN b.faculties f WHERE f IN :faculties")
    List<Book> findByFacultiesIn(@Param("faculties") Set<Faculty> faculties);

    @Query("SELECT DISTINCT b FROM Book b JOIN b.faculties f WHERE f.type = 'COMMON'")
    List<Book> findCommonBooks();

    // Метод для получения книг, добавленных конкретным пользователем через BookEntry
    @Query("SELECT be.book FROM BookEntry be WHERE be.addedBy.userId = :userId")
    List<Book> findByAddedById(Long userId);

    Page<Book> findAll(Pageable pageable); // Метод для пагинации
}
