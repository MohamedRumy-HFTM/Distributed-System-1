package org.acme.boundary;

import org.acme.entity.Blog;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Ausgabe-Vertrag fuer Blogs.
 *
 * Bewusst ohne commentCount: die Kommentare sind lazy geladen, ein Zaehler
 * wuerde pro Blog eine Extra-Abfrage ausloesen (N+1). Wer die Kommentare
 * braucht, holt sie unter /blogs/{id}/comments.
 */
@Schema(name = "BlogResponse", description = "Ein Blog, wie die API ihn ausliefert.")
public record BlogResponse(Long id, String title, String content) {

    public static BlogResponse from(Blog blog) {
        return new BlogResponse(blog.getId(), blog.getTitle(), blog.getContent());
    }
}
