package org.acme.entity;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

@Entity
@Schema(name = "Blog", description = "Ein Blogeintrag mit Titel und Inhalt.")
public class Blog {

    /**
     * Wrapper-Typ Long statt long: ein noch nicht gespeicherter Blog hat null als
     * Id. Beim Anlegen vergibt der Server die Id, der Client sendet sie nicht mit.
     */
    @Schema(nullable = true, examples = { "null" }, description = "Wird beim Anlegen vom Server vergeben.")
    @Id
    @GeneratedValue
    private Long id;

    @Schema(required = true, examples = { "Mein erster Blogbeitrag" }, description = "Titel des Beitrags.")
    private String title;

    @Schema(examples = { "Das ist der Inhalt meines Beitrags." }, description = "Fliesstext des Beitrags.")
    private String content;

    @Schema(hidden = true)
    @JsonbTransient
    @OneToMany(mappedBy = "blog", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    public Blog() {
    }

    public Blog(String title, String content) {
        this.title = title;
        this.content = content;
    }

    /**
     * Haengt einen Kommentar an und haelt dabei beide Seiten der Beziehung synchron.
     * Ohne das Setzen der Gegenseite bliebe der Fremdschluessel leer, da die
     * ManyToOne-Seite die besitzende ist.
     */
    public void addComment(Comment comment) {
        comments.add(comment);
        comment.setBlog(this);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<Comment> getComments() {
        return comments;
    }
}
