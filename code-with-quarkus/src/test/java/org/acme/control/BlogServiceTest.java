package org.acme.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.acme.entity.Blog;
import org.acme.entity.Comment;
import org.junit.jupiter.api.Test;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

@QuarkusTest
public class BlogServiceTest {

    @Inject
    BlogService blogService;

    @Inject
    CommentRepository commentRepository;

    @Test
    @TestTransaction
    void listingAndAddingBlogs() {
        // Arrange
        Blog blog = new Blog("Testing Blog", "This is my testing blog");
        int blogsBefore = blogService.getBlogs().size();

        // Act
        blogService.addBlog(blog);
        List<Blog> blogs = blogService.getBlogs();

        // Assert
        assertEquals(blogsBefore + 1, blogs.size());
    }

    @Test
    @TestTransaction
    void addingCommentLinksItToTheBlog() {
        // Arrange
        Blog blog = new Blog("Blog with comments", "A blog that will receive a comment");
        blogService.addBlog(blog);

        // Act
        Comment comment = blogService.addComment(blog.getId(), new Comment("Alice", "Nice post!"));

        // Assert
        assertNotNull(comment);
        assertNotNull(comment.getId());
        assertEquals(blog.getId(), comment.getBlog().getId());
        assertEquals(1, blogService.getComments(blog.getId()).size());
    }

    @Test
    @TestTransaction
    void deletingBlogAlsoRemovesItsComments() {
        // Arrange
        Blog blog = new Blog("Doomed Blog", "This blog will be deleted");
        blogService.addBlog(blog);
        Long blogId = blog.getId();
        blogService.addComment(blogId, new Comment("Bob", "First!"));
        blogService.addComment(blogId, new Comment("Carol", "Second!"));
        assertEquals(2, blogService.getComments(blogId).size());

        // Act
        boolean deleted = blogService.deleteBlog(blogId);

        // Assert
        assertTrue(deleted);
        assertNull(blogService.getBlog(blogId));
        assertEquals(0, commentRepository.findByBlog(blogId).size());
    }

    @Test
    @TestTransaction
    void updatingBlogChangesTitleAndContent() {
        // Arrange
        Blog blog = new Blog("Alter Titel", "Alter Inhalt");
        blogService.addBlog(blog);

        // Act
        Blog updated = blogService.updateBlog(blog.getId(), new Blog("Neuer Titel", "Neuer Inhalt"));

        // Assert
        assertNotNull(updated);
        assertEquals("Neuer Titel", updated.getTitle());
        assertEquals("Neuer Inhalt", blogService.getBlog(blog.getId()).getContent());
    }

    @Test
    @TestTransaction
    void updatingMissingBlogIsRejected() {
        // Gueltige Nutzdaten, damit wirklich die fehlende Id geprueft wird und
        // nicht vorher schon die Validierung zuschlaegt.
        assertNull(blogService.updateBlog(999999L, new Blog("Beliebiger Titel", "Beliebiger Inhalt")));
    }

    @Test
    @TestTransaction
    void serviceRejectsBlogWithTooShortTitle() {
        // Die Methodenvalidierung schuetzt den Service auch ohne REST-Schicht.
        ConstraintViolationException thrown = assertThrows(ConstraintViolationException.class,
                () -> blogService.addBlog(new Blog("kurz", "Titel hat nur vier Zeichen")));

        assertEquals(1, thrown.getConstraintViolations().size());
        assertEquals("Titel braucht mindestens 5 Zeichen.",
                thrown.getConstraintViolations().iterator().next().getMessage());
    }

    @Test
    @TestTransaction
    void serviceRejectsCommentWithoutText() {
        Blog blog = new Blog("Blog fuer Kommentartest", "Inhalt");
        blogService.addBlog(blog);

        assertThrows(ConstraintViolationException.class,
                () -> blogService.addComment(blog.getId(), new Comment("Alice", "  ")));
    }

    @Test
    @TestTransaction
    void commentOnMissingBlogIsRejected() {
        // Arrange / Act
        Comment orphan = blogService.addComment(999999L, new Comment("Mallory", "Ghost comment"));

        // Assert
        assertNull(orphan);
    }

    @Test
    @TestTransaction
    void searchingBlogsByTitleFragment() {
        // Arrange
        blogService.addBlog(new Blog("Quarkus Panache Guide", "How to use Panache"));
        blogService.addBlog(new Blog("Unrelated topic", "Nothing to see here"));

        // Act
        List<Blog> hits = blogService.searchByTitle("panache");

        // Assert
        assertEquals(1, hits.size());
        assertEquals("Quarkus Panache Guide", hits.get(0).getTitle());
    }

    @Test
    void countingBlogsMatchesListing() {
        // Arrange / Act / Assert
        assertEquals(blogService.getBlogs().size(), blogService.countBlogs());
    }

    @Test
    void startupInitializationCreatedSampleData() {
        // Arrange / Act / Assert - DataInitialization laeuft beim Start
        assertTrue(blogService.countBlogs() >= 2);
    }
}
