package com.example.elklogging.service;

import com.example.elklogging.model.Author;
import com.example.elklogging.repository.AuthorRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Note: every log line in this class will automatically include the {@code traceId}
 * MDC value set by HttpLoggingFilter — without this class knowing anything about tracing.
 * That's the whole point of MDC. See the README and the Medium article.
 */
@Service
@RequiredArgsConstructor
public class AuthorService {

    private static final Logger log = LoggerFactory.getLogger(AuthorService.class);

    private final AuthorRepository authorRepository;

    public List<Author> findAll() {
        log.info("Fetching all authors");
        return authorRepository.findAll();
    }

    public Optional<Author> findById(Long id) {
        log.info("Looking up author by id={}", id);
        return authorRepository.findById(id);
    }

    public Author save(Author author) {
        log.info("Saving author name='{}'", author.getName());
        return authorRepository.save(author);
    }

    public void deleteById(Long id) {
        log.warn("Deleting author id={}", id);
        authorRepository.deleteById(id);
    }
}
