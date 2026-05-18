INSERT INTO author (id, name, nationality) VALUES (1, 'Robert C. Martin', 'American');
INSERT INTO author (id, name, nationality) VALUES (2, 'Joshua Bloch', 'American');
INSERT INTO author (id, name, nationality) VALUES (3, 'Martin Fowler', 'British');

INSERT INTO book (id, title, isbn, author_id) VALUES (1, 'Clean Code', '978-0132350884', 1);
INSERT INTO book (id, title, isbn, author_id) VALUES (2, 'Clean Architecture', '978-0134494166', 1);
INSERT INTO book (id, title, isbn, author_id) VALUES (3, 'Effective Java', '978-0134685991', 2);
INSERT INTO book (id, title, isbn, author_id) VALUES (4, 'Refactoring', '978-0134757599', 3);
