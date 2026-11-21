-- Basisschema der Blog-API.
--
-- Abgeleitet aus dem Stand, den Hibernate zuvor per drop-and-create erzeugt
-- hat (per mysqldump ausgelesen, nicht von Hand geschrieben). Ab dieser
-- Migration besitzt Flyway das Schema; Hibernate darf es nur noch pruefen
-- (quarkus.hibernate-orm.schema-management.strategy=validate). Eine
-- Abweichung zwischen Entity und Migration faellt damit beim Start auf.
--
-- Backticks um `Comment` und `text`: beides sind Schluesselwoerter in MySQL.
-- Unquoted funktioniert es hier zwar, aber nur weil sie nicht reserviert sind -
-- darauf sollte man sich nicht verlassen.

CREATE TABLE Blog (
    id      BIGINT       NOT NULL,
    title   VARCHAR(255) NOT NULL,
    content VARCHAR(255) NOT NULL,
    -- Nullable mit Absicht: der Autor wird vom Server aus dem Token gesetzt,
    -- Altbestand aus der Zeit vor der Authentifizierung hat keinen.
    author  VARCHAR(255) NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE `Comment` (
    id      BIGINT       NOT NULL,
    author  VARCHAR(255) NOT NULL,
    `text`  VARCHAR(255) NOT NULL,
    blog_id BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_comment_blog FOREIGN KEY (blog_id) REFERENCES Blog (id)
) ENGINE = InnoDB;

-- Sequenztabellen fuer @GeneratedValue ohne Strategie-Angabe. MySQL kennt
-- keine Sequences, deshalb haelt Hibernate den Zaehler je Entity in einer
-- eigenen Tabelle. Die Startzeile muss existieren, sonst findet der Generator
-- beim ersten Insert keinen Wert vor.
CREATE TABLE Blog_SEQ (
    next_val BIGINT
) ENGINE = InnoDB;
INSERT INTO Blog_SEQ (next_val) VALUES (1);

CREATE TABLE Comment_SEQ (
    next_val BIGINT
) ENGINE = InnoDB;
INSERT INTO Comment_SEQ (next_val) VALUES (1);
