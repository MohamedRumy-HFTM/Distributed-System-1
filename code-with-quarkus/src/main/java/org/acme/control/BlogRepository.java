package org.acme.control;

import java.util.List;

import org.acme.entity.Blog;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BlogRepository implements PanacheRepository<Blog> {

    public List<Blog> findByTitle(String title) {
        return list("title", title);
    }

    public List<Blog> searchByTitle(String fragment) {
        return list("lower(title) like ?1", "%" + fragment.toLowerCase() + "%");
    }

    /**
     * Liefert die noch unpaginierte Abfrage. Erst der Aufrufer entscheidet ueber
     * die Seite, damit sich Gesamtzahl und Seiteninhalt aus derselben Abfrage
     * ableiten lassen.
     *
     * Der Suchbegriff wird als Parameter gebunden (?1) und ist damit nicht
     * injizierbar. Das Sortierfeld dagegen kann NICHT gebunden werden - es landet
     * als Bezeichner in der order-by-Klausel. Deshalb muss der Aufrufer es gegen
     * BlogService.SORTABLE_FIELDS pruefen, bevor er es hierher reicht.
     */
    public PanacheQuery<Blog> search(String searchString, Sort sort) {
        if (searchString == null || searchString.isBlank()) {
            return findAll(sort);
        }
        String pattern = "%" + searchString.toLowerCase() + "%";
        return find("lower(title) like ?1 or lower(content) like ?1", sort, pattern);
    }
}
