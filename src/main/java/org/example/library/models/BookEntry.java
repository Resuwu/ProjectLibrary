package org.example.library.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "book_entries")
public class BookEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id")
    private Long entryId;

    @ManyToOne
    @JoinColumn(name = "user_id") // Предполагается, что у вас есть сущность LibraryUser
    @JsonBackReference // Указываем, что это обратная часть
    private LibraryUser  addedBy; // Пользователь, который добавил книгу

    @OneToOne // Каждая книга может иметь только одну запись о добавлении
    @JoinColumn(name = "book_id", unique = true)
    private Book book; // Книга, которая была добавлена

    @Column(name = "added_at")
    private LocalDateTime addedAt; // Дата и время добавления

    @Override
    public int hashCode() {
        return entryId != null ? entryId.hashCode() : 0; // Используем entryId для хэш-кода
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BookEntry entry)) return false;
        return entryId != null && entryId.equals(entry.getEntryId());
    }

    @Override
    public String toString() {
        return "BookEntry{" +
                "entryId=" + entryId +
                ", addedBy=" + (addedBy != null ? addedBy.getUsername() : "null") + // Указываем имя пользователя, если оно есть
                ", bookId=" + (book != null ? book.getBookId() : "null") + // Указываем ID книги, если она есть
                ", addedAt=" + addedAt +
                '}';
    }
}
