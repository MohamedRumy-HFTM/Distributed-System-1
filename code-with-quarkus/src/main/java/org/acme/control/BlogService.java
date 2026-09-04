package org.acme.control;

import java.util.List;

import org.acme.entity.Blog;
import org.acme.entity.Comment;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BlogService {
    @Inject
    BlogRepository blogRepository;

    @Inject
    CommentRepository commentRepository;

    public List<Blog> getBlogs() {
        var blogs = blogRepository.listAll();
        Log.info("Returning " + blogs.size() + " blogs");
        return blogs;
    }

    public Blog getBlog(Long id) {
        return blogRepository.findById(id);
    }

    public List<Blog> searchByTitle(String fragment) {
        var blogs = blogRepository.searchByTitle(fragment);
        Log.info("Found " + blogs.size() + " blogs matching '" + fragment + "'");
        return blogs;
    }

    public long countBlogs() {
        return blogRepository.count();
    }

    @Transactional
    public void addBlog(Blog blog) {
        Log.info("Adding blog " + blog.getTitle());
        blogRepository.persist(blog);
    }

    /**
     * Loescht ueber die Entity statt ueber deleteById, damit Cascade und
     * orphanRemoval greifen und die Kommentare mitgeloescht werden.
     */
    @Transactional
    public boolean deleteBlog(Long id) {
        Blog blog = blogRepository.findById(id);
        if (blog == null) {
            Log.warn("Blog " + id + " not found, nothing deleted");
            return false;
        }
        Log.info("Deleting blog " + blog.getTitle() + " with " + blog.getComments().size() + " comments");
        blogRepository.delete(blog);
        return true;
    }

    public List<Comment> getComments(Long blogId) {
        return commentRepository.findByBlog(blogId);
    }

    @Transactional
    public Comment addComment(Long blogId, Comment comment) {
        Blog blog = blogRepository.findById(blogId);
        if (blog == null) {
            Log.warn("Blog " + blogId + " not found, comment rejected");
            return null;
        }
        blog.addComment(comment);
        commentRepository.persist(comment);
        Log.info("Added comment by " + comment.getAuthor() + " to blog " + blog.getTitle());
        return comment;
    }
}
