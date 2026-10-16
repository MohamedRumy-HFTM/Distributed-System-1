package org.acme.boundary;

import java.util.List;
import java.util.Set;

import org.acme.control.BlogService;
import org.acme.control.PagedResult;
import org.acme.entity.Blog;
import org.acme.entity.Comment;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

@Path("blogs")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Blogs", description = "Blogeintraege anlegen, lesen, aendern und loeschen")
public class BlogResource {

    /** Name des Headers, der die Berechtigung fuer loeschende Zugriffe traegt. */
    public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    @Inject
    BlogService blogService;

    /**
     * Wird fuer den Pruef-Endpunkt gebraucht: dort soll die Validierung ein
     * Ergebnis LIEFERN, statt den Aufruf mit einer Exception abzubrechen.
     * Genau dafuer loest man sie von Hand aus statt ueber @Valid.
     */
    @Inject
    Validator validator;

    /**
     * LERNKONSTRUKT, keine echte Absicherung: ein fest konfiguriertes Token im
     * Klartext ersetzt keine Authentifizierung. Es dient hier nur dazu,
     * @HeaderParam zusammen mit den Statuscodes 401 und 403 zu zeigen.
     */
    @ConfigProperty(name = "blog.admin-token", defaultValue = "local-dev-token")
    String adminToken;

    @GET
    @Operation(summary = "Blogs auflisten",
            description = "Liefert eine Seite von Blogeintraegen zusammen mit der Gesamtzahl der Treffer. "
                    + "Unterstuetzt Volltextsuche ueber Titel und Inhalt, Sortierung und Paging.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Seite mit Datensaetzen und Metadaten"),
            @APIResponse(responseCode = "400", description = "Ungueltiger Such-, Sortier- oder Paging-Parameter") })
    public PagedResult<BlogResponse> list(
            @Parameter(description = "Volltextsuche in Titel und Inhalt") @QueryParam("searchString") String searchString,
            @Parameter(description = "Sortierfeld: id, title oder content") @QueryParam("sortBy") @DefaultValue("id") String sortBy,
            @Parameter(description = "Sortierrichtung: asc oder desc") @QueryParam("orderBy") @DefaultValue("asc") String orderBy,
            @Parameter(description = "Nullbasierte Seitennummer") @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Datensaetze pro Seite, hoechstens 100") @QueryParam("size") @DefaultValue("10") int size) {

        if (page < 0) {
            throw new ResponseException(Status.BAD_REQUEST, "page darf nicht negativ sein");
        }
        if (size < 1) {
            throw new ResponseException(Status.BAD_REQUEST, "size muss mindestens 1 sein");
        }
        if (size > BlogService.MAX_PAGE_SIZE) {
            throw new ResponseException(Status.BAD_REQUEST, "size darf hoechstens " + BlogService.MAX_PAGE_SIZE + " sein");
        }
        if (!blogService.isSortable(sortBy)) {
            throw new ResponseException(Status.BAD_REQUEST, "sortBy muss eines von " + BlogService.SORTABLE_FIELDS + " sein");
        }
        boolean descending = toDescending(orderBy);

        return blogService.findBlogs(searchString, sortBy, descending, page, size)
                .map(BlogResponse::from);
    }

    /**
     * Variante 1 der Path-Parameter: @PathParam mit explizitem Bezeichner (JAX-RS-Standard).
     */
    @GET
    @Path("{id}")
    @Operation(summary = "Einen Blog lesen", description = "Liefert einen einzelnen Blogeintrag anhand seiner Id.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Blog gefunden"),
            @APIResponse(responseCode = "404", description = "Kein Blog mit dieser Id") })
    public BlogResponse getById(@PathParam("id") Long id) {
        Blog blog = blogService.getBlog(id);
        if (blog == null) {
            throw new ResponseException(Status.NOT_FOUND, "Kein Blog mit der Id " + id);
        }
        return BlogResponse.from(blog);
    }

    @POST
    @Operation(summary = "Neuen Blog anlegen",
            description = "Legt einen Blogeintrag an. Die Id vergibt der Server, sie darf im Request-Body fehlen. "
                    + "Die Antwort nennt im Location-Header die URI der neuen Ressource.")
    @RequestBody(content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = BlogRequest.class),
            examples = { @ExampleObject(
                    name = "Neuer Blog",
                    value = "{\"title\": \"Mein erster Blogbeitrag\", \"content\": \"Hallo Welt\"}") }))
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Angelegt, Location-Header verweist auf die neue Ressource"),
            @APIResponse(responseCode = "400", description = "Eingabe verletzt eine Regel",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ValidationErrorResponse.class))) })
    public Response create(@Valid BlogRequest request, @Context UriInfo uriInfo) {
        // Die Feldregeln pruefte bereits @Valid, bevor diese Methode lief.
        // Ein voellig fehlender Body kommt aber als null an und loest keine
        // Verletzung aus - dieser Fall bleibt daher von Hand zu pruefen.
        if (request == null) {
            throw new ResponseException(Status.BAD_REQUEST, "Request-Body fehlt");
        }
        Blog blog = new Blog(request.title(), request.content());
        blogService.addBlog(blog);

        // Aus /blogs wird /blogs/{neueId} - der Client muss die Id nicht erraten.
        UriBuilder builder = uriInfo.getAbsolutePathBuilder().path(Long.toString(blog.getId()));
        return Response.created(builder.build()).entity(BlogResponse.from(blog)).build();
    }

    /**
     * Prueft einen Blog, ohne ihn zu speichern. Hier wird die Validierung
     * bewusst von Hand ueber den injizierten Validator ausgeloest: mit @Valid
     * waere der Aufruf schon vor dem Methodenrumpf abgebrochen, das Ergebnis der
     * Pruefung ist hier aber genau das, was der Client wissen will. Ein Frontend
     * kann so ein Formular pruefen, bevor der Nutzer es abschickt.
     */
    @POST
    @Path("validate")
    @Operation(summary = "Blog pruefen, ohne zu speichern",
            description = "Antwortet mit 204, wenn der Blog gueltig waere, sonst mit 400 und der Liste der verletzten Regeln.")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Der Blog wuerde die Validierung bestehen"),
            @APIResponse(responseCode = "400", description = "Liste der verletzten Regeln",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ValidationErrorResponse.class))) })
    public Response validate(BlogRequest request) {
        if (request == null) {
            throw new ResponseException(Status.BAD_REQUEST, "Request-Body fehlt");
        }
        Set<ConstraintViolation<BlogRequest>> violations = validator.validate(request);
        if (violations.isEmpty()) {
            return Response.noContent().build();
        }
        int status = Status.BAD_REQUEST.getStatusCode();
        return Response.status(status)
                .entity(ValidationErrorResponse.of(status, violations))
                .build();
    }

    @PUT
    @Path("{id}")
    @Operation(summary = "Blog aendern", description = "Ersetzt Titel und Inhalt eines bestehenden Blogeintrags.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Blog wurde aktualisiert"),
            @APIResponse(responseCode = "400", description = "Eingabe verletzt eine Regel",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ValidationErrorResponse.class))),
            @APIResponse(responseCode = "404", description = "Kein Blog mit dieser Id") })
    public BlogResponse update(@PathParam("id") Long id, @Valid BlogRequest request) {
        if (request == null) {
            throw new ResponseException(Status.BAD_REQUEST, "Request-Body fehlt");
        }
        Blog updated = blogService.updateBlog(id, new Blog(request.title(), request.content()));
        if (updated == null) {
            throw new ResponseException(Status.NOT_FOUND, "Kein Blog mit der Id " + id);
        }
        return BlogResponse.from(updated);
    }

    @DELETE
    @Path("{id}")
    @Operation(summary = "Blog loeschen",
            description = "Loescht einen Blogeintrag samt seiner Kommentare. Erfordert den Header " + ADMIN_TOKEN_HEADER + ".")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Blog wurde geloescht"),
            @APIResponse(responseCode = "401", description = "Header " + ADMIN_TOKEN_HEADER + " fehlt"),
            @APIResponse(responseCode = "403", description = "Token ist ungueltig"),
            @APIResponse(responseCode = "404", description = "Kein Blog mit dieser Id") })
    public Response delete(
            @PathParam("id") Long id,
            @Parameter(description = "Berechtigungstoken fuer loeschende Zugriffe")
            @HeaderParam(ADMIN_TOKEN_HEADER) String token) {

        requireAdminToken(token);
        if (!blogService.deleteBlog(id)) {
            throw new ResponseException(Status.NOT_FOUND, "Kein Blog mit der Id " + id);
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
    @Operation(summary = "Kommentare eines Blogs auflisten", description = "Liefert alle Kommentare zum angegebenen Blogeintrag.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Liste der Kommentare"),
            @APIResponse(responseCode = "404", description = "Kein Blog mit dieser Id") })
    public List<Comment> comments(@RestPath Long id) {
        requireExistingBlog(id);
        return blogService.getComments(id);
    }

    @POST
    @Path("{id}/comments")
    @Operation(summary = "Kommentar hinzufuegen", description = "Haengt einen Kommentar an einen bestehenden Blogeintrag.")
    @RequestBody(content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = Comment.class),
            examples = { @ExampleObject(
                    name = "Neuer Kommentar",
                    value = "{\"author\": \"Alice\", \"text\": \"Toller Beitrag!\"}") }))
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Kommentar wurde angelegt"),
            @APIResponse(responseCode = "400", description = "Eingabe verletzt eine Regel",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ValidationErrorResponse.class))),
            @APIResponse(responseCode = "404", description = "Kein Blog mit dieser Id") })
    public Response addComment(@RestPath Long id, @Valid Comment comment, @Context UriInfo uriInfo) {
        if (comment == null) {
            throw new ResponseException(Status.BAD_REQUEST, "Request-Body fehlt");
        }
        Comment created = blogService.addComment(id, comment);
        if (created == null) {
            throw new ResponseException(Status.NOT_FOUND, "Kein Blog mit der Id " + id);
        }
        return Response.created(uriInfo.getAbsolutePathBuilder().path(Long.toString(created.getId())).build())
                .entity(created)
                .build();
    }

    private void requireExistingBlog(Long id) {
        if (blogService.getBlog(id) == null) {
            throw new ResponseException(Status.NOT_FOUND, "Kein Blog mit der Id " + id);
        }
    }

    private void requireAdminToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseException(Status.UNAUTHORIZED, "Header " + ADMIN_TOKEN_HEADER + " fehlt");
        }
        if (!adminToken.equals(token)) {
            throw new ResponseException(Status.FORBIDDEN, "Ungueltiges Token im Header " + ADMIN_TOKEN_HEADER);
        }
    }

    private boolean toDescending(String orderBy) {
        if ("desc".equalsIgnoreCase(orderBy)) {
            return true;
        }
        if ("asc".equalsIgnoreCase(orderBy)) {
            return false;
        }
        throw new ResponseException(Status.BAD_REQUEST, "orderBy muss asc oder desc sein");
    }
}
