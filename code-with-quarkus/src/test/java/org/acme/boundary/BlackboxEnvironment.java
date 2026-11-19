package org.acme.boundary;

import java.util.Map;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Stellt die Umsysteme fuer die Blackbox-Tests bereit: eine echte MySQL und ein
 * echtes Keycloak als Container.
 *
 * Warum das hier noetig ist: {@code @QuarkusIntegrationTest} startet das
 * GEPACKTE Artefakt im Prod-Profil. Dort laufen keine Dev Services, und
 * {@code @TestSecurity} greift nicht - der Test spricht nur ueber HTTP mit einem
 * fremden Prozess. Also braucht er echte Umsysteme und ein echtes Token.
 *
 * Die zurueckgegebenen Eigenschaften werden dem gestarteten Artefakt
 * uebergeben und ueberschreiben dort die %prod-Defaults.
 *
 * Anwendung und Test laufen beide auf dem Host und erreichen Keycloak unter
 * derselben Adresse. Damit stimmt der Issuer im Token automatisch - die
 * KC_HOSTNAME-Akrobatik aus docker-compose.yml ist hier nicht noetig.
 */
public class BlackboxEnvironment implements QuarkusTestResourceLifecycleManager {

    /** Der Test liest hierueber die Keycloak-Adresse, um Tokens zu holen. */
    public static final String KEYCLOAK_URL_PROPERTY = "blackbox.keycloak.url";

    private MySQLContainer mysql;
    private GenericContainer<?> keycloak;

    @Override
    public Map<String, String> start() {
        mysql = new MySQLContainer(DockerImageName.parse("mysql:9.5"))
                .withDatabaseName("quarkus")
                .withUsername("quarkus")
                .withPassword("quarkus");
        mysql.start();

        keycloak = new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.0"))
                .withExposedPorts(8080)
                .withCommand("start-dev", "--import-realm")
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("keycloak/realm-blog.json"),
                        "/opt/keycloak/data/import/realm-blog.json")
                .waitingFor(Wait.forHttp("/realms/blog/.well-known/openid-configuration")
                        .forPort(8080)
                        .forStatusCode(200));
        keycloak.start();

        String keycloakUrl = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
        System.setProperty(KEYCLOAK_URL_PROPERTY, keycloakUrl);

        return Map.of(
                "quarkus.datasource.jdbc.url", mysql.getJdbcUrl(),
                "quarkus.datasource.username", mysql.getUsername(),
                "quarkus.datasource.password", mysql.getPassword(),
                "quarkus.oidc.auth-server-url", keycloakUrl + "/realms/blog");
    }

    @Override
    public void stop() {
        if (keycloak != null) {
            keycloak.stop();
        }
        if (mysql != null) {
            mysql.stop();
        }
        System.clearProperty(KEYCLOAK_URL_PROPERTY);
    }
}
