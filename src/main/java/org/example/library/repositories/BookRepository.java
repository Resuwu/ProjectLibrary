package org.example.library.repositories;

import org.example.library.models.Book;
import org.example.library.models.BookStatus;
import org.example.library.models.Faculty;
import org.example.library.models.FacultyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findByTitleContaining(String title);
    List<Book> findByStatus(BookStatus status); // Метод для получения книг по статусу

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
    @Query("SELECT b FROM Book b JOIN b.faculties f WHERE f IN :faculties")
    List<Book> findByFacultiesIn(Set<Faculty> faculties);

    // Метод для получения книг, добавленных конкретным пользователем через BookEntry
    @Query("SELECT be.book FROM BookEntry be WHERE be.addedBy.userId = :userId")
    List<Book> findByAddedById(Long userId);
}
