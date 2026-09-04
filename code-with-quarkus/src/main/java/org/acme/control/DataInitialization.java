package org.acme.control;

import org.acme.entity.Blog;
import org.acme.entity.Comment;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DataInitialization {

    @Inject
    BlogRepository blogRepository;

    /**
     * Legt Beispieldaten an, aber nur wenn die Datenbank leer ist.
     * Diese Pruefung macht die Initialisierung idempotent: bei einem Redeploy
     * gegen eine bestehende Datenbank entstehen keine Duplikate.
     */
    @Transactional
    public void init(@Observes StartupEvent event) {
        long existing = blogRepository.count();
        if (existing > 0) {
            Log.info("Data initialization skipped, " + existing + " blogs already present");
            return;
        }

        Blog first = new Blog("First Blog", "This is my first blog");
        first.addComment(new Comment("Alice", "Great first post!"));
        first.addComment(new Comment("Bob", "Looking forward to more."));

        Blog second = new Blog("Second Blog", "This is my second blog");
        second.addComment(new Comment("Carol", "Nice write-up."));

        blogRepository.persist(first, second);
        Log.info("Data initialization created " + blogRepository.count() + " blogs");
    }
}
