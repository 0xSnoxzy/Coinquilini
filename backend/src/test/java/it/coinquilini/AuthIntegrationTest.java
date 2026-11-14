package it.coinquilini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.coinquilini.dao.UserDAO;
import it.coinquilini.model.User;
import it.coinquilini.service.AuthService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** API e DAO reali, con dati confinati in uno schema PostgreSQL generato dalla suite. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {
    private static final String DATABASE_URL = env("TEST_DATABASE_URL",
            "jdbc:postgresql://127.0.0.1:15432/postgres?connectTimeout=5");
    private static final String DATABASE_USER = env("TEST_DATABASE_USER", "postgres");
    private static final String DATABASE_PASSWORD = env("TEST_DATABASE_PASSWORD", "");
    private static final String SCHEMA = "auth_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = "Password di prova 123!";
    private static boolean schemaCreated;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserDAO users;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) throws SQLException {
        try (var connection = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        registry.add("spring.datasource.url", () -> DATABASE_URL
                + (DATABASE_URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> DATABASE_USER);
        registry.add("spring.datasource.password", () -> DATABASE_PASSWORD);
    }

    @AfterAll
    static void removeOnlyOwnedSchema() throws SQLException {
        if (schemaCreated) {
            try (var connection = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
                 var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
            }
        }
    }

    @Test
    void registrationHashesPasswordNormalizesEmailAndRenewsSession() throws Exception {
        String email = email();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", "vecchio oggetto utente");
        session.setAttribute("houseId", 123L);
        String previousId = session.getId();
        var result = mvc.perform(post("/api/auth/register").session(session).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("name", " Antonio ",
                                "email", "  " + email.toUpperCase(Locale.ROOT) + "  ", "password", PASSWORD,
                                "role", "ADMIN", "active", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("name").value("Antonio"))
                .andExpect(jsonPath("email").value(email))
                .andExpect(jsonPath("role").value("USER"))
                .andExpect(jsonPath("active").value(true)).andReturn();
        JsonNode body = publicUser(result);
        User stored = users.findById(body.get("id").asLong());
        assertThat(stored.getPassword()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, stored.getPassword())).isTrue();
        assertThat(session.getId()).isNotEqualTo(previousId);
        assertThat(Collections.list(session.getAttributeNames())).containsExactly("userId");
        assertThat(session.getAttribute("userId")).isEqualTo(stored.getId());
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(SCHEMA);
    }

    @Test
    void loginNormalizesEmailChecksHashAndRenewsExistingSession() throws Exception {
        User user = registered();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", -1L);
        session.setAttribute("user", "obsolete");
        String previousId = session.getId();
        var result = mvc.perform(post("/api/auth/login").session(session).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", " " + user.getEmail().toUpperCase(Locale.ROOT) + " ",
                                "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        assertThat(publicUser(result).get("id").asLong()).isEqualTo(user.getId());
        assertThat(session.getId()).isNotEqualTo(previousId);
        assertThat(Collections.list(session.getAttributeNames())).containsExactly("userId");
        assertThat(session.getAttribute("userId")).isEqualTo(user.getId());
    }

    @Test
    void duplicateEmailIsRejectedRegardlessOfCaseAndSurroundingSpaces() throws Exception {
        User user = registered();
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("name", "Altro", "email",
                                " " + user.getEmail().toUpperCase(Locale.ROOT) + " ", "password", PASSWORD))))
                .andExpect(status().isConflict()).andExpect(jsonPath("error").isString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE email = ?",
                Integer.class, user.getEmail())).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRegistrationsProduceOneAccountAndOneConflict() throws Exception {
        String email = email();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentRegister(email, ready, start));
            var second = executor.submit(() -> concurrentRegister(" " + email.toUpperCase(Locale.ROOT) + " ", ready, start));
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE email = ?", Integer.class, email))
                .isEqualTo(1);
    }

    @Test
    void preexistingMixedCaseEmailWithHashCanLoginAndCannotBeRegisteredAgain() throws Exception {
        User user = registered();
        jdbc.update("UPDATE app_user SET email = ? WHERE id = ?", " " + user.getEmail().toUpperCase(Locale.ROOT) + " ", user.getId());
        login(user.getEmail(), PASSWORD).andExpect(status().isOk());
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("name", "Altro", "email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Password di prova 123!", "not-a-bcrypt-hash", ""})
    void plaintextAndMalformedStoredPasswordsNeverProvideAnAlternativeLogin(String stored) throws Exception {
        User user = registered();
        jdbc.update("UPDATE app_user SET password = ? WHERE id = ?", stored, user.getId());
        var result = login(user.getEmail(), PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("error").isString()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void wrongPasswordAndUnknownAccountAreUnauthorizedWithoutSession() throws Exception {
        User user = registered();
        for (String email : List.of(user.getEmail(), email())) {
            var result = login(email, "sbagliata").andExpect(status().isUnauthorized()).andReturn();
            assertThat(result.getRequest().getSession(false)).isNull();
        }
    }

    @Test
    void disabledAccountCannotLogin() throws Exception {
        User user = registered();
        jdbc.update("UPDATE app_user SET active = FALSE WHERE id = ?", user.getId());
        var result = login(user.getEmail(), PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("error").isString()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void protectedEndpointsRequireSessionWithoutCreatingOne() throws Exception {
        var me = mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andReturn();
        var profile = mvc.perform(put("/api/profile/save").contentType("application/json").content("{\"name\":\"Altro\"}"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(me.getRequest().getSession(false)).isNull();
        assertThat(profile.getRequest().getSession(false)).isNull();
    }

    @Test
    void legacySessionContainingAUserObjectIsRejectedAndInvalidated() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", registered());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void sessionForNonexistentUserIsRejectedAndInvalidated() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", -1L);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"me", "profile"})
    void disablingAccountAfterLoginBlocksBothProtectedEndpoints(String endpoint) throws Exception {
        User user = registered();
        MockHttpSession session = session(user);
        jdbc.update("UPDATE app_user SET active = FALSE WHERE id = ?", user.getId());
        if (endpoint.equals("me")) {
            mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isForbidden());
        } else {
            mvc.perform(put("/api/profile/save").session(session).contentType("application/json").content("{\"name\":\"Vietato\"}"))
                    .andExpect(status().isForbidden());
            assertThat(users.findById(user.getId()).getName()).isEqualTo(user.getName());
        }
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void currentUserIsReloadedFromDatabaseRatherThanCachedInSession() throws Exception {
        User user = registered();
        MockHttpSession session = session(user);
        jdbc.update("UPDATE app_user SET name = ?, role = ? WHERE id = ?", "Aggiornato nel DB", "ADMIN", user.getId());
        var result = mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("name").value("Aggiornato nel DB"))
                .andExpect(jsonPath("role").value("ADMIN")).andReturn();
        publicUser(result);
        assertThat(Collections.list(session.getAttributeNames())).containsExactly("userId");
    }

    @Test
    void profileUpdatesOnlyPermittedFieldsAndPreservesPasswordAndSession() throws Exception {
        User user = registered();
        MockHttpSession session = session(user);
        String sessionId = session.getId();
        var result = mvc.perform(put("/api/profile/save").session(session).contentType("application/json")
                        .content("{\"name\":\" Antonio nuovo \",\"color\":\"#abcdef\",\"photo\":\"foto\","
                                + "\"role\":\"ADMIN\",\"active\":false,\"email\":\"altro@example.invalid\",\"password\":\"cambiata\"}"))
                .andExpect(status().isOk()).andReturn();
        publicUser(result);
        User updated = users.findById(user.getId());
        assertThat(updated.getName()).isEqualTo("Antonio nuovo");
        assertThat(updated.getColor()).isEqualTo("#abcdef");
        assertThat(updated.getPhoto()).isEqualTo("foto");
        assertThat(updated.getRole()).isEqualTo("USER");
        assertThat(updated.isActive()).isTrue();
        assertThat(updated.getEmail()).isEqualTo(user.getEmail());
        assertThat(updated.getPassword()).isEqualTo(user.getPassword());
        assertThat(session.getId()).isEqualTo(sessionId);
        assertThat(Collections.list(session.getAttributeNames())).containsExactly("userId");
    }

    @Test
    void partialProfileUpdatePreservesOmittedFieldsAndAllowsClearingPhoto() throws Exception {
        User user = registered();
        user.setPhoto("foto iniziale");
        users.update(user);
        MockHttpSession session = session(user);
        mvc.perform(put("/api/profile/save").session(session).contentType("application/json").content("{\"photo\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("name").value(user.getName()))
                .andExpect(jsonPath("color").value(user.getColor())).andExpect(jsonPath("photo").value(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\":\" \"}", "{\"color\":\"red\"}", "{\"color\":\"#abc\"}"})
    void invalidProfileDoesNotModifyStoredUser(String body) throws Exception {
        User user = registered();
        mvc.perform(put("/api/profile/save").session(session(user)).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("error").isString());
        assertThat(users.findById(user.getId())).usingRecursiveComparison().isEqualTo(user);
    }

    @Test
    void logoutInvalidatesSessionAndIsIdempotent() throws Exception {
        MockHttpSession session = session(registered());
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isOk());
        assertThat(session.isInvalid()).isTrue();
        var result = mvc.perform(post("/api/auth/logout")).andExpect(status().isOk()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\"Antonio\"}",
            "{\"name\":\" \",\"email\":\"a@example.invalid\",\"password\":\"password\"}",
            "{\"name\":\"Antonio\",\"email\":\"invalid\",\"password\":\"password\"}",
            "{\"name\":\"Antonio\",\"email\":\"a b@example.invalid\",\"password\":\"password\"}",
            "{\"name\":\"Antonio\",\"email\":\"a@example.invalid\",\"password\":\" \"}"})
    void invalidRegistrationReturnsFrontendCompatibleErrorWithoutSession(String body) throws Exception {
        var result = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("error").isString()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ascii", "unicode"})
    void bcryptBoundaryCountsUtf8BytesAndRejectsLongerPasswords(String kind) throws Exception {
        String accepted = kind.equals("ascii") ? "x".repeat(72) : "é".repeat(36);
        String email = email();
        var created = mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("name", "Antonio", "email", email, "password", accepted))))
                .andExpect(status().isCreated()).andReturn();
        publicUser(created);
        login(email, accepted).andExpect(status().isOk());
        login(email, accepted + "x").andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("name", "Antonio", "email", email(), "password", accepted + "x"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordSpacesAreSignificant() throws Exception {
        String email = email();
        auth.register("Antonio", email, " password ");
        login(email, " password ").andExpect(status().isOk());
        login(email, "password").andExpect(status().isUnauthorized());
    }

    @Test
    void userSerializationNeverIncludesPasswordOrHash() throws Exception {
        User user = registered();
        String serialized = json.writeValueAsString(user);
        assertThat(json.readTree(serialized).has("password")).isFalse();
        assertThat(serialized).doesNotContain(user.getPassword()).doesNotContain(PASSWORD);
    }

    @Test
    void existingAngularOriginsCanUseCredentialedRequests() throws Exception {
        for (String origin : List.of("http://localhost:4200", "http://localhost:3000")) {
            mvc.perform(options("/api/auth/login").header("Origin", origin)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }
    }

    private int concurrentRegister(String email, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout barriera");
        try {
            auth.register("Antonio", email, PASSWORD);
            return 201;
        } catch (ResponseStatusException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            return ex.getStatusCode().value();
        }
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private User registered() {
        return auth.register("Antonio", email(), PASSWORD);
    }

    private MockHttpSession session(User user) throws Exception {
        return (MockHttpSession) login(user.getEmail(), PASSWORD).andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private JsonNode publicUser(MvcResult result) throws Exception {
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "name", "email", "role", "active", "color", "photo");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(PASSWORD).doesNotContain("$2");
        return body;
    }

    private static String email() {
        return UUID.randomUUID() + "@example.invalid";
    }

    private static String env(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }
}
