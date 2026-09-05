package org.acme.entity;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;

@Entity
@Schema(name = "Comment", description = "Ein Kommentar zu einem Blogeintrag.")
public class Comment {

    @Schema(nullable = true, examples = { "null" }, description = "Wird beim Anlegen vom Server vergeben.")
    @Id
    @GeneratedValue
    private Long id;

    @Schema(required = true, examples = { "Alice" }, description = "Name der kommentierenden Person.")
    @NotBlank(message = "Autor darf nicht leer sein.")
    private String author;

    @Schema(required = true, examples = { "Toller Beitrag!" }, description = "Der Kommentartext.")
    @NotBlank(message = "Kommentartext darf nicht leer sein.")
    private String text;

    /**
     * Rueckverweis auf den Blog. Aus der JSON-Ausgabe und dem OpenAPI-Schema
     * ausgeblendet, weil Blog und Comment sonst gegenseitig aufeinander zeigen
     * und die Serialisierung in eine Endlosschleife liefe.
     */
    @Schema(hidden = true)
    @JsonbTransient
    @ManyToOne
    private Blog blog;

    public Comment() {
    }

    public Comment(String author, String text) {
        this.author = author;
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Blog getBlog() {
        return blog;
    }

    public void setBlog(Blog blog) {
        this.blog = blog;
    }
}
