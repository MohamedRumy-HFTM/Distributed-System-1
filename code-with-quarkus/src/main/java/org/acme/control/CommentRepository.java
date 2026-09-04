package org.acme.control;

import java.util.List;

import org.acme.entity.Comment;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CommentRepository implements PanacheRepository<Comment> {

    public List<Comment> findByBlog(Long blogId) {
        return list("blog.id", blogId);
    }

    public long countByBlog(Long blogId) {
        return count("blog.id", blogId);
    }
}
