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

## Berechtigungskonzept

**Implementiert** mit `quarkus-oidc` und `@RolesAllowed`/`@PermitAll` in
`BlogResource`, abgesichert durch `BlogAuthTest`.

### Rollen

Drei Rollen, kumulativ gedacht: jede höhere kann alles, was die niedrigere kann.

| Rolle | Darf |
|---|---|
| *(ohne Login)* | veröffentlichte Blogs und deren Kommentare lesen |
| `reader` | zusätzlich kommentieren |
| `author` | zusätzlich Blogs anlegen und ändern |
| `admin` | zusätzlich Blogs löschen |

Mehr Rollen wären für diese Domäne erfundene Komplexität. Ein eigener
`moderator` (fremde Kommentare löschen) wird erst sinnvoll, wenn Kommentare
überhaupt löschbar sind — heute sind sie es nicht.

### Zugriffsregeln je Endpunkt

| Methode | Pfad | Annotation | Zusätzlich | Begründung |
|---|---|---|---|---|
| GET | `/blogs` | `@PermitAll` | – | Ein Blog ist zum Lesen da. |
| GET | `/blogs/{id}` | `@PermitAll` | – | dito |
| GET | `/blogs/{id}/comments` | `@PermitAll` | – | Kommentare sind Teil des öffentlichen Beitrags. |
| POST | `/blogs/{id}/comments` | `@RolesAllowed("reader")` | – | Schreiben braucht eine zurechenbare Identität, sonst ist die API ein Spam-Ziel. |
| POST | `/blogs` | `@RolesAllowed("author")` | Autor = Token-Benutzer | Inhalte erstellen ist redaktionell. |
| POST | `/blogs/validate` | `@RolesAllowed("author")` | – | Hilfsendpunkt für das Autoren-Formular. |
| PUT | `/blogs/{id}` | `@RolesAllowed({"author","admin"})` | **Eigentümer oder admin** | Fremde Beiträge ändert man nicht. |
| DELETE | `/blogs/{id}` | `@RolesAllowed({"author","admin"})` | **Eigentümer oder admin** | Löschen ist unwiderruflich und kaskadiert auf die Kommentare. |

### Benutzeridentifikation: nur eigene Blogs

Der Anwendungsfall, für den der angemeldete Benutzer konkret bekannt sein muss.

- **Beim Anlegen** setzt der Server das Feld `Blog.author` aus
  `SecurityIdentity.getPrincipal().getName()`. Der Wert steht **nicht** in
  `BlogRequest` — ein Client kann sich keinen fremden Autor zuschreiben.
- **Beim Ändern und Löschen** vergleicht `requireOwnerOrAdmin(blog)` den
  Benutzernamen aus dem Token mit `blog.getAuthor()`. Ein `admin` umgeht die
  Prüfung, ein `author` bekommt `403` mit der Meldung, wem der Blog gehört.
- **Reihenfolge ist Absicht:** erst `404` prüfen, dann die Eigentümerschaft.
  Sonst verriete ein `403` einem Fremden, dass eine Id existiert.

Die Startdaten legen deshalb zwei Blogs mit **verschiedenen** Autoren an
(`author1` und `admin1`) — sonst wäre die Regel nicht prüfbar.

Gegenüber dem Konzept der letzten Einheit ist das eine **bewusste Verschärfung
und gleichzeitig Lockerung**: Löschen ist nicht mehr `admin`-exklusiv, dafür
darf ein `author` nur an die eigenen Beiträge. Das setzt die Aufgabenstellung
„nur seine eigenen Blogs bearbeiten oder löschen" genauer um als die alte
Rollen-Regel.

### Bekannte Lücken dieses Konzepts

- **Kein Veröffentlichungsstatus.** „Veröffentlichte Blogs sind öffentlich"
  stimmt heute trivial, weil es keine Entwürfe gibt. Mit einem `draft`-Status
  müsste GET zwischen öffentlich und `author` unterscheiden.
- **Kommentare haben keinen Eigentümer.** Das Feld `Comment.author` ist ein
  freier Text aus dem Request, keine geprüfte Identität. Wer Kommentare
  löschbar machen will, muss ihn erst wie bei `Blog` aus dem Token setzen.
- **Der `X-Admin-Token` ist weg.** Er war ein Lernkonstrukt für `@HeaderParam`
  und ist durch echte Rollenprüfung ersetzt.

## Auth-Setup austesten

Es gibt **zwei Wege**, Keycloak anzubinden. Beide nutzen dieselbe
Realm-Definition `src/main/resources/keycloak/realm-blog.json` — eine Quelle,
kein Abgleich per Hand.

### Variante A: Dev Services (Standard, nichts zu starten)

```bash
export JAVA_HOME=$PWD/.jdks/jdk-25.0.3+9/Contents/Home
./mvnw quarkus:dev
```

Quarkus startet selbst einen Keycloak-Container und importiert den Realm.
Bequem zum Entwickeln, aber nicht wiederverwendbar — mit dem Prozess ist der
Container weg.

Der Port wechselt bei jedem Lauf. Zu finden ist er in der Dev UI
(<http://localhost:8080/q/dev-ui>, Karte *OpenID Connect*) oder im Terminal:

```bash
docker ps --format '{{.Image}} {{.Ports}}' | grep keycloak
```

Im Startlog steht die URL **nicht** — dort erscheint nur, dass der Container
hochgefahren ist.

Nötige Konfiguration: nur `quarkus.oidc.client-id` und
`quarkus.keycloak.devservices.realm-path`. **Kein** `auth-server-url` — sobald
der gesetzt ist, schalten die Dev Services ab.

### Variante B: eigener Container (fester Port, überlebt Neustarts)

```bash
docker compose up -d                                   # Keycloak auf :8180
./mvnw quarkus:dev -Dquarkus.profile=keycloak          # Backend dagegen
```

Das Profil `keycloak` setzt `auth-server-url` auf
`http://localhost:8180/realms/blog` und schaltet die Dev Services ab. Admin-Konsole:
<http://localhost:8180>, `admin` / `admin`.

Da der Container mit `start-dev` im Speicher läuft, ist nach einem Neustart
wieder genau der Stand aus der Realm-Datei aktiv. Wer etwas ändern will, ändert
die Datei, nicht die Admin-Konsole.

### Test-Benutzer

| Benutzer | Passwort | Rollen |
|---|---|---|
| `reader1` | `reader1` | `reader` |
| `author1` | `author1` | `reader`, `author` |
| `admin1` | `admin1` | `reader`, `author`, `admin` |

Client: `blog-api`, public, mit aktivierten Direct Access Grants — deshalb
braucht der Token-Abruf kein Client-Secret. Nur für die Entwicklung gedacht;
produktiv holt ein Frontend das Token über den Authorization-Code-Flow.

### Access-Token abholen

```bash
http --form POST :8180/realms/blog/protocol/openid-connect/token \
  grant_type=password client_id=blog-api username=author1 password=author1
```

Das `access_token` aus der Antwort auf <https://jwt.io> einfügen. Im Payload
muss die Rolle unter `realm_access.roles` stehen:

```json
"realm_access": {
  "roles": ["author", "reader"]
}
```

So sieht es für `author1` aus (nachgeprüft). `reader1` hat nur `reader`,
`admin1` hat alle drei.

Ohne jwt.io geht es auch im Terminal — ein JWT-Payload ist nur base64url:

```bash
TOKEN=$(http --form POST :8180/realms/blog/protocol/openid-connect/token \
  grant_type=password client_id=blog-api username=author1 password=author1 \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])")

echo "$TOKEN" | cut -d. -f2 | python3 -c "
import sys, base64, json
p = sys.stdin.read().strip()
print(json.dumps(json.loads(base64.urlsafe_b64decode(p + '=' * (-len(p) % 4))), indent=2))
" | grep -A6 realm_access
```

Das Auffüllen mit `=` ist nötig: JWTs verwenden base64url **ohne** Padding, ein
schlichtes `base64 -d` bricht daran ab.

### Geschützte Endpunkte mit HTTPie testen

`$TOKEN` wie oben holen, dann:

```bash
# Öffentlich - geht ohne Token
http :8080/blogs

# Ohne Token schreiben -> 401
http --ignore-stdin POST :8080/blogs title="Ohne Token" content="geht nicht"

# Als author1 anlegen -> 201, author kommt aus dem Token
http --ignore-stdin POST :8080/blogs "Authorization:Bearer $TOKEN" \
  title="Mit Token angelegt" content="Hallo Welt"

# Eigenen Blog ändern -> 200
http --ignore-stdin PUT :8080/blogs/1 "Authorization:Bearer $TOKEN" \
  title="Eigener Blog geaendert" content="darf ich"

# Fremden Blog ändern -> 403 (Blog 2 gehört admin1)
http --ignore-stdin PUT :8080/blogs/2 "Authorization:Bearer $TOKEN" \
  title="Fremder Blog" content="darf ich nicht"
```

Mit einem `reader1`-Token schlägt `POST /blogs` mit **403** fehl (angemeldet,
aber falsche Rolle) — im Unterschied zu **401** ohne Token. Mit einem
`admin1`-Token geht auch der fremde Blog.

### Geschützte Endpunkte in der Swagger UI testen

1. `./mvnw quarkus:dev` starten, <http://localhost:8080/q/swagger-ui> öffnen.
2. Token wie oben per HTTPie holen und kopieren.
3. Oben rechts auf **Authorize**, bei `bearerAuth` `Bearer <TOKEN>` eintragen.
4. Jetzt liefern `POST`/`PUT`/`DELETE` statt `401` die echten Antworten. Ohne
   den Schritt lassen sich nur die `@PermitAll`-Endpunkte ausprobieren.

## Tests

68 Tests, Coverage 87,6 % Zeilen / 87,1 % Zweige. Laufen **ohne Keycloak**:
`%test.quarkus.oidc.enabled=false`, die Identität kommt aus `@TestSecurity`.
Damit kostet der Testlauf keinen zusätzlichen Container.

| Klasse | Prüft |
|---|---|
| `BlogAuthTest` | Berechtigungskonzept und Eigentümer-Regel je Rolle |
| `BlogSystemTest` | API von außen, geordneter Ablauf, Text Blocks + JSON-P |
| `BlogResourceTest` | Endpunkte einzeln, inkl. Paging, Suche, Sortierung, Validierung |
| `OpenApiTest` | das generierte OpenAPI-Dokument |
| `BlogServiceTest` | Service- und Repository-Ebene |
| `ExceptionMappersTest` | Mapper-Zweige, die über HTTP nicht erreichbar sind |
| `DataInitializationTest` | Idempotenz der Startdaten |
