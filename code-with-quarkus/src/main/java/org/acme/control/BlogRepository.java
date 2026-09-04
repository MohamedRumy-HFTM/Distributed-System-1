package org.acme.control;

import java.util.List;

import org.acme.entity.Blog;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BlogRepository implements PanacheRepository<Blog> {

    public List<Blog> findByTitle(String title) {
        return list("title", title);
    }

    public List<Blog> searchByTitle(String fragment) {
        return list("lower(title) like ?1", "%" + fragment.toLowerCase() + "%");
    }
}
