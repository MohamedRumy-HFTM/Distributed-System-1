package org.acme.boundary;

import java.util.List;

import org.acme.control.BlogService;
import org.acme.entity.Blog;
import org.acme.entity.Comment;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/blogs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BlogResource {

    @Inject
    BlogService blogService;

    @GET
    public List<Blog> list(@QueryParam("title") String title) {
        if (title == null || title.isBlank()) {
            return blogService.getBlogs();
        }
        return blogService.searchByTitle(title);
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") Long id) {
        Blog blog = blogService.getBlog(id);
        if (blog == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(blog).build();
    }

    @POST
    public Response create(Blog blog) {
        blogService.addBlog(blog);
        return Response.status(Response.Status.CREATED).entity(blog).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        if (!blogService.deleteBlog(id)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/comments")
    public Response comments(@PathParam("id") Long id) {
        if (blogService.getBlog(id) == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(blogService.getComments(id)).build();
    }

    @POST
    @Path("/{id}/comments")
    public Response addComment(@PathParam("id") Long id, Comment comment) {
        Comment created = blogService.addComment(id, comment);
        if (created == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.status(Response.Status.CREATED).entity(created).build();
    }
}
