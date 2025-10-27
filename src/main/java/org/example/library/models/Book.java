package org.example.library.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "title")
    private String title;

    @Column(name = "isbn")
    private String isbn;

    @Column(name = "publication_year")
    private int publicationYear;

    @Column(name = "description")
    private String description;

    @Column(name = "publisher")
    private String publisher;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private BookStatus status;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL)
    @JsonManagedReference // Указываем, что это родительская часть
    private List<BookAuthor> bookAuthors;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL)
    private List<PhysicalCopy> physicalCopies;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL)
    private List<Ebook> ebooks;

    @ManyToMany
    @JoinTable(
            name = "book_faculty",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "faculty_id")
    )
    private Set<Faculty> faculties; // Связь с факультетами

    @OneToOne(mappedBy = "book", cascade = CascadeType.ALL)
    @JsonIgnore // Игнорируем это поле при сериализации
    private BookEntry entry; // Запись о добавлении книги

    @Override
    public int hashCode() {
        return bookId != null ? bookId.hashCode() : 0; // Используем bookId для хэш-кода
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Book book)) return false;
        return bookId != null && bookId.equals(book.getBookId());
    }

    @Override
    public String toString() {
        return "Book{" +
                "bookId=" + bookId +
                ", title='" + title + '\'' +
                ", isbn='" + isbn + '\'' +
                ", publicationYear=" + publicationYear +
                ", description='" + description + '\'' +
                ", publisher='" + publisher + '\'' +
                ", status=" + status +
                ", authorsCount=" + (bookAuthors != null ? bookAuthors.size() : 0) + // Указываем только количество авторов
                '}';
    }


}
