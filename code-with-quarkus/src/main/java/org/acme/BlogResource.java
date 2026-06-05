package org.acme;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/blog")
public class BlogResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String blog() {
        return "Willkommen auf meinem Blog!";
    }
}
