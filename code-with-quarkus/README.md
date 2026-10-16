# Blog-Backend (code-with-quarkus)

REST-Backend für Blogeinträge und deren Kommentare. Quarkus 3.36, Java 25,
Hibernate ORM mit Panache, MySQL über Dev Services.

## Starten

Docker muss laufen (Dev Services starten den MySQL-Container), und `JAVA_HOME`
muss auf das JDK 25 zeigen:

```bash
cd code-with-quarkus
export JAVA_HOME=$PWD/.jdks/jdk-25.0.3+9/Contents/Home
./mvnw quarkus:dev
```

- Swagger UI: <http://localhost:8080/q/swagger-ui> (nur im Dev-Modus)
- OpenAPI: <http://localhost:8080/q/openapi>

Tests mit Coverage-Report nach `target/jacoco-report`:

```bash
./mvnw test
```

## Architektur

Boundary-Control-Entity:

| Paket | Inhalt |
|---|---|
| `org.acme.entity` | `Blog`, `Comment` — JPA-Entities mit Bean-Validation-Constraints |
| `org.acme.control` | `BlogService`, `BlogRepository`, `CommentRepository`, `DataInitialization`, `PagedResult` |
| `org.acme.boundary` | `BlogResource`, DTOs, `ExceptionMappers`, `ResponseException` |

## API

| Methode | Pfad | Body rein | Body raus |
|---|---|---|---|
| GET | `/blogs` | – | `PagedResult<BlogResponse>` |
| GET | `/blogs/{id}` | – | `BlogResponse` |
| POST | `/blogs` | `BlogRequest` | `BlogResponse` + `Location` |
| PUT | `/blogs/{id}` | `BlogRequest` | `BlogResponse` |
| DELETE | `/blogs/{id}` | – | – (braucht Header `X-Admin-Token`) |
| POST | `/blogs/validate` | `BlogRequest` | – (204) bzw. `ValidationErrorResponse` |
| GET | `/blogs/{id}/comments` | – | `Comment[]` |
| POST | `/blogs/{id}/comments` | `Comment` | `Comment` + `Location` |

`GET /blogs` unterstützt `?searchString=`, `?sortBy=` (nur `id`, `title`,
`content`), `?orderBy=asc|desc`, `?page=`, `?size=` (max. 100).

## DTOs und Mapping

Die Blog-Endpunkte sprechen **nicht** mit der Entity, sondern mit eigenen
Verträgen:

| DTO | Felder | Zweck |
|---|---|---|
| `BlogRequest` | `title`, `content` | Eingabe. **Kein `id`** — die vergibt der Server, ein Client kann keine fremde Id unterschieben. |
| `BlogResponse` | `id`, `title`, `content` | Ausgabe. Enthält keine Kommentare. |
| `ValidationErrorResponse` | `status`, `title`, `violations[]` | Validierungsfehler, pro verletztem Feld ein Eintrag. |

**Mapping** ist absichtlich von Hand geschrieben, ohne Mapping-Framework — bei
drei Feldern kostet eine Bibliothek mehr, als sie spart:

- rein: `new Blog(request.title(), request.content())` in `BlogResource`
- raus: `BlogResponse.from(blog)`, für Listen `PagedResult.map(BlogResponse::from)`

**Warum überhaupt DTOs**, obwohl die Entity vorher funktionierte:

1. **Die Id ist nicht verhandelbar.** Mit der Entity als Eingabe konnte ein
   Client `{"id": 42, ...}` senden. Hibernate ignorierte das, aber der Vertrag
   log.
2. **Kein Over-Posting.** Neue Entity-Felder landen nicht automatisch in der API.
3. **Die Entity darf sich ändern**, ohne die API zu brechen — und umgekehrt.
4. **Die Lazy-Beziehung bleibt drinnen.** `BlogResponse` hat keinen
   Kommentar-Zähler, weil der pro Blog eine Extra-Abfrage auslösen würde (N+1).
   Kommentare holt man unter `/blogs/{id}/comments`.

Die Kommentar-Endpunkte nutzen weiterhin die `Comment`-Entity direkt. Das ist
eine bewusste Grenze des Umfangs, nicht ein Versehen — dieselben Argumente
gelten dort genauso.

Validierung sitzt an **beiden** Stellen: auf `BlogRequest` (schützt die API,
liefert 400) und auf der Entity (schützt die Datenbank, auch bei
programmatischen Aufrufen).

## CORS

Konfiguriert in `application.properties`:

```properties
quarkus.http.cors.enabled=true
quarkus.http.cors.origins=http://localhost:3000,http://localhost:5173
quarkus.http.cors.methods=GET,POST,PUT,DELETE
quarkus.http.cors.headers=Content-Type,X-Admin-Token
```

Erlaubt sind nur die Dev-Server der üblichen Frontend-Toolchains (Next.js 3000,
Vite 5173). **Bewusst kein `*`:** Mit einem Wildcard könnte jede beliebige Seite
im Browser eines Nutzers Requests an diese API stellen. Da `DELETE` lediglich
durch einen Header-Token geschützt ist und nicht durch ein Cookie, wäre der
Schaden hier begrenzt — aber ein Wildcard als Gewohnheit ist genau die
Gewohnheit, die später mit Cookie-Authentifizierung zum CSRF-Problem wird.

`X-Admin-Token` muss explizit erlaubt werden; nicht aufgeführte Header lehnt der
Browser im Preflight ab.

Produktive Origins gehören über eine Umgebungsvariable gesetzt
(`QUARKUS_HTTP_CORS_ORIGINS`), nicht in diese Datei eingecheckt.

## OWASP-Risiken

Bewertet gegen die OWASP API Security Top 10 (2023). Ehrlich auch dort, wo
nichts getan ist:

| Risiko | Stand | Begründung |
|---|---|---|
| **API1 Broken Object Level Authorization** | ⚠️ offen | Jeder darf jeden Blog lesen und ändern. Es gibt keine Benutzer, also auch keine Eigentümerschaft. Mit Auth wäre eine Prüfung „gehört dieser Blog dem Aufrufer" nötig. |
| **API2 Broken Authentication** | ⚠️ **bekannt unzureichend** | `DELETE` prüft einen festen Klartext-Token aus der Konfiguration. Das ist ein Lernkonstrukt für `@HeaderParam` und 401/403, **keine Authentifizierung**: kein Ablauf, keine Rotation, kein Benutzerbezug, im Repo nachlesbar. Ersatz wäre OIDC (`quarkus-oidc`). |
| **API3 Broken Object Property Level Authorization** | ✅ adressiert | `BlogRequest` lässt kein `id` zu, `BlogResponse` gibt keine internen Felder heraus. Genau dafür sind die DTOs da. |
| **API4 Unrestricted Resource Consumption** | ✅ teilweise | `size` ist auf 100 begrenzt, Paging ist Pflicht. Kein Rate-Limiting — bei öffentlichem Betrieb nötig. |
| **API5 Broken Function Level Authorization** | ⚠️ offen | Lesen und Schreiben sind offen, nur Löschen verlangt den Token. Ohne Rollenmodell nicht sauber lösbar. |
| **API6 Unrestricted Access to Sensitive Business Flows** | ✅ n/a | Keine sensiblen Abläufe (kein Kauf, kein Versand). |
| **API7 Server Side Request Forgery** | ✅ n/a | Die Anwendung ruft keine vom Client gelieferten URLs ab. |
| **API8 Security Misconfiguration** | ✅ adressiert | CORS ohne Wildcard, Swagger UI nur im Dev-Modus, keine Zugangsdaten im Repo (Dev Services erzeugen sie). |
| **API9 Improper Inventory Management** | ✅ adressiert | OpenAPI-Dokument ist vollständig und getestet (`OpenApiTest`), inklusive Fehlerformaten. |
| **API10 Unsafe Consumption of APIs** | ✅ n/a | Es werden keine Fremd-APIs konsumiert. |

Zusätzlich geprüft und abgedeckt:

- **Injection:** Suchbegriffe sind als Query-Parameter gebunden. Das
  Sortierfeld lässt sich *nicht* binden (Spaltennamen sind keine Parameter),
  deshalb eine Whitelist in `BlogService.SORTABLE_FIELDS`. Panache setzt den
  Wert zwar in Backticks, ein Fremdwert erzeugte aber HTTP 500 statt 400.
  Ein Test hält das fest.
- **Fehlermeldungen:** Validierungsfehler nennen Feld und Regel, aber keine
  Stacktraces oder SQL. Rückgabe-Verletzungen werden zu 500 mit generischer
  Meldung, Details nur ins Log.

**Vor einem produktiven Einsatz** fehlen damit mindestens: echte
Authentifizierung und Autorisierung (API1, API2, API5), Rate-Limiting (API4) und
HTTPS-Terminierung.

## Tests

56 Tests, Coverage 86,5 % Zeilen / 84,4 % Zweige.

| Klasse | Prüft |
|---|---|
| `BlogSystemTest` | API von außen, geordneter Ablauf, Text Blocks + JSON-P |
| `BlogResourceTest` | Endpunkte einzeln, inkl. Paging, Suche, Sortierung, Validierung |
| `OpenApiTest` | das generierte OpenAPI-Dokument |
| `BlogServiceTest` | Service- und Repository-Ebene |
| `ExceptionMappersTest` | Mapper-Zweige, die über HTTP nicht erreichbar sind |
| `DataInitializationTest` | Idempotenz der Startdaten |
