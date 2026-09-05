package org.acme.control;

import java.util.List;
import java.util.Set;

import org.acme.entity.Blog;
import org.acme.entity.Comment;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;

@ApplicationScoped
public class BlogService {

    /**
     * Felder, nach denen sortiert werden darf. Die Whitelist ist notwendig, weil
     * ein Sortierfeld als Bezeichner in die JPQL-order-by-Klausel eingesetzt wird
     * und dort - anders als ein Suchbegriff - nicht als Parameter gebunden werden
     * kann. Ohne diese Pruefung waere ?sortBy=... eine Injection-Flaeche.
     */
    public static final Set<String> SORTABLE_FIELDS = Set.of("id", "title", "content");

    /** Obergrenze fuer die Seitengroesse, damit ein Client nicht die ganze Tabelle anfordert. */
    public static final int MAX_PAGE_SIZE = 100;

    @Inject
    BlogRepository blogRepository;

    @Inject
    CommentRepository commentRepository;

    public boolean isSortable(String field) {
        return SORTABLE_FIELDS.contains(field);
    }

    public List<Blog> getBlogs() {
        var blogs = blogRepository.listAll();
        Log.info("Returning " + blogs.size() + " blogs");
        return blogs;
    }

    /**
     * Sucht, sortiert und paginiert in einem Durchgang. Gesamtzahl und
     * Seiteninhalt stammen aus derselben Abfrage, sind also garantiert konsistent.
     * Der Aufrufer muss sortBy zuvor mit isSortable(...) geprueft haben.
     */
    public PagedResult<Blog> findBlogs(String searchString, String sortBy, boolean descending, int page, int size) {
        Sort sort = Sort.by(sortBy, descending ? Sort.Direction.Descending : Sort.Direction.Ascending);
        PanacheQuery<Blog> query = blogRepository.search(searchString, sort).page(Page.of(page, size));

        long totalCount = query.count();
        int pageCount = query.pageCount();
        List<Blog> data = query.list();

        Log.info("Returning page " + page + " with " + data.size() + " of " + totalCount + " blogs");
        return new PagedResult<>(data, totalCount, page, size, pageCount);
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

    /**
     * @Valid greift hier als CDI-Methodenvalidierung. Sie schuetzt den Service
     * auch dann, wenn er nicht ueber die REST-Schicht aufgerufen wird - die
     * Boundary allein abzusichern liesse programmatische Aufrufe ungeprueft.
     */
    @Transactional
    public void addBlog(@Valid Blog blog) {
        Log.info("Adding blog " + blog.getTitle());
        blogRepository.persist(blog);
    }

    /**
     * Aktualisiert Titel und Inhalt eines bestehenden Blogs.
     * Die geladene Entity ist innerhalb der Transaktion managed, daher genuegt
     * das Setzen der Felder - Hibernate schreibt die Aenderung beim Flush selbst
     * (Dirty Checking), ein explizites persist() waere ueberfluessig.
     */
    @Valid
    @Transactional
    public Blog updateBlog(Long id, @Valid Blog changed) {
        Blog blog = blogRepository.findById(id);
        if (blog == null) {
            Log.warn("Blog " + id + " not found, nothing updated");
            return null;
        }
        blog.setTitle(changed.getTitle());
        blog.setContent(changed.getContent());
        Log.info("Updated blog " + id + " to title " + blog.getTitle());
        return blog;
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
    public Comment addComment(Long blogId, @Valid Comment comment) {
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
