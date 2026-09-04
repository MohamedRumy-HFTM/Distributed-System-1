package org.acme.boundary;

import java.util.List;

import org.acme.control.BlogService;
import org.acme.entity.Blog;
import org.acme.entity.Comment;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("blogs")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Blogs", description = "Blogeintraege anlegen, lesen, aendern und loeschen")
public class BlogResource {

    @Inject
    BlogService blogService;

    @GET
    @Operation(summary = "Alle Blogs auflisten, optional nach Titel gefiltert")
    public List<Blog> list(@QueryParam("title") String title) {
        if (title == null || title.isBlank()) {
            return blogService.getBlogs();
        }
        return blogService.searchByTitle(title);
    }

    /**
     * Variante 1 der Path-Parameter: @PathParam mit explizitem Bezeichner (JAX-RS-Standard).
     */
    @GET
    @Path("{id}")
    @Operation(summary = "Einen Blog anhand seiner Id lesen")
    @APIResponse(responseCode = "200", description = "Blog gefunden")
    @APIResponse(responseCode = "404", description = "Kein Blog mit dieser Id")
    public Response getById(@PathParam("id") Long id) {
        Blog blog = blogService.getBlog(id);
        if (blog == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(blog).build();
    }

    @POST
    @Operation(summary = "Neuen Blog anlegen")
    @APIResponse(responseCode = "201", description = "Blog wurde angelegt")
    public Response create(Blog blog) {
        blogService.addBlog(blog);
        return Response.status(Response.Status.CREATED).entity(blog).build();
    }

    @PUT
    @Path("{id}")
    @Operation(summary = "Titel und Inhalt eines bestehenden Blogs ersetzen")
    @APIResponse(responseCode = "200", description = "Blog wurde aktualisiert")
    @APIResponse(responseCode = "404", description = "Kein Blog mit dieser Id")
    public Response update(@PathParam("id") Long id, Blog blog) {
        Blog updated = blogService.updateBlog(id, blog);
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("{id}")
    @Operation(summary = "Blog samt seiner Kommentare loeschen")
    @APIResponse(responseCode = "204", description = "Blog wurde geloescht")
    @APIResponse(responseCode = "404", description = "Kein Blog mit dieser Id")
    public Response delete(@PathParam("id") Long id) {
        if (!blogService.deleteBlog(id)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    /**
     * Variante 2 der Path-Parameter: @RestPath ohne Bezeichner - Quarkus leitet den
     * Namen aus dem Parameter ab, der damit zwingend "id" heissen muss.
     * Variante 3 waere, die Annotation ganz wegzulassen (Quarkus REST erkennt den
     * Parameter allein am uebereinstimmenden Namen).
     */
    @GET
    @Path("{id}/comments")
    @Operation(summary = "Kommentare eines Blogs auflisten")
    @APIResponse(responseCode = "404", description = "Kein Blog mit dieser Id")
    public Response comments(@RestPath Long id) {
        if (blogService.getBlog(id) == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(blogService.getComments(id)).build();
    }

    @POST
    @Path("{id}/comments")
    @Operation(summary = "Kommentar zu einem Blog hinzufuegen")
    @APIResponse(responseCode = "201", description = "Kommentar wurde angelegt")
    @APIResponse(responseCode = "404", description = "Kein Blog mit dieser Id")
    public Response addComment(@RestPath Long id, Comment comment) {
        Comment created = blogService.addComment(id, comment);
        if (created == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.status(Response.Status.CREATED).entity(created).build();
    }
}
