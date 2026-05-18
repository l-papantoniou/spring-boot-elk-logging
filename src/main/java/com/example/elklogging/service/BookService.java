package com.example.elklogging.service;

import com.example.elklogging.model.Author;
import com.example.elklogging.model.Book;
import com.example.elklogging.repository.AuthorRepository;
import com.example.elklogging.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookService {

    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;

    public List<Book> findAll() {
        log.info("Fetching all books");
        return bookRepository.findAll();
    }

    public Optional<Book> findById(Long id) {
        log.info("Looking up book by id={}", id);
        return bookRepository.findById(id);
    }

    public Book save(Book book) {
        if (book.getAuthor() == null || book.getAuthor().getId() == null) {
            log.error("Cannot save book without an author reference: title='{}'", book.getTitle());
            throw new IllegalArgumentException("Author id is required");
        }

        Author author = authorRepository.findById(book.getAuthor().getId())
                .orElseThrow(() -> {
                    log.error("Author not found: id={}", book.getAuthor().getId());
                    return new IllegalArgumentException(
                            "Author not found: " + book.getAuthor().getId());
                });

        book.setAuthor(author);
        log.info("Saving book title='{}' by author='{}'", book.getTitle(), author.getName());
        return bookRepository.save(book);
    }

    public void deleteById(Long id) {
        log.warn("Deleting book id={}", id);
        bookRepository.deleteById(id);
    }
}
