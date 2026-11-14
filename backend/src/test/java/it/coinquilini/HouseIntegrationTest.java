package it.coinquilini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.coinquilini.dao.HouseDAOJDBC;
import it.coinquilini.dao.MembershipDAOJDBC;
import it.coinquilini.model.House;
import it.coinquilini.model.Membership;
import it.coinquilini.model.User;
import it.coinquilini.service.AuthService;
import it.coinquilini.service.HouseService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HouseIntegrationTest {
    private static final String URL = env("TEST_DATABASE_URL", "jdbc:postgresql://127.0.0.1:15432/postgres?connectTimeout=5");
    private static final String DB_USER = env("TEST_DATABASE_USER", "postgres");
    private static final String DB_PASSWORD = env("TEST_DATABASE_PASSWORD", "");
    private static final String SCHEMA = "house_test_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean schemaCreated;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthService auth;
    @Autowired HouseService houses;
    @Autowired JdbcTemplate jdbc;
    @SpyBean HouseDAOJDBC houseWrites;
    @SpyBean MembershipDAOJDBC membershipWrites;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) throws SQLException {
        try (var connection = DriverManager.getConnection(URL, DB_USER, DB_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        registry.add("spring.datasource.url", () -> URL + (URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> DB_USER);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
    }

    @AfterAll
    static void cleanupOwnedSchema() throws SQLException {
        if (schemaCreated) {
            try (var connection = DriverManager.getConnection(URL, DB_USER, DB_PASSWORD);
                 var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
            }
        }
    }

    @Test
    void newUserSnapshotHasNullHouseAndTheCompleteFrontendContract() throws Exception {
        User user = user("Nuovo");
        JsonNode snapshot = snapshot(session(user));
        assertThat(snapshot.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder(
                "user", "house", "members", "expenses", "repayments", "items", "frequent", "tasks", "turns",
                "notices", "myBalance", "settlements", "adminUsers", "adminHouses");
        assertThat(snapshot.get("user").get("id").asLong()).isEqualTo(user.getId());
        assertThat(snapshot.get("house").isNull()).isTrue();
        for (String field : List.of("members", "expenses", "repayments", "items", "frequent", "tasks", "turns",
                "notices", "settlements", "adminUsers", "adminHouses")) {
            assertThat(snapshot.get(field).isArray()).isTrue();
            assertThat(snapshot.get(field).size()).isZero();
        }
        assertThat(snapshot.get("myBalance").asLong()).isZero();
    }

    @Test
    void createPersistsHouseAndOwnerAndSnapshotKeepsIdentity() throws Exception {
        User owner = user("Antonio");
        MockHttpSession session = session(owner);
        mvc.perform(post("/api/houses/create").session(session).contentType("application/json")
                        .content("{\"name\":\" Casa vera \",\"emoji\":\"🧊\",\"userId\":999,\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("ok").value(true));
        JsonNode first = snapshot(session);
        assertThat(first.get("house").get("name").asText()).isEqualTo("Casa vera");
        assertThat(first.get("house").get("emoji").asText()).isEqualTo("🧊");
        assertThat(first.get("house").get("inviteCode").asText()).matches("[A-Z0-9]{6}");
        assertThat(first.get("members").size()).isEqualTo(1);
        assertThat(first.get("members").get(0).get("id").asLong()).isEqualTo(owner.getId());
        assertThat(first.get("members").get(0).get("membershipRole").asText()).isEqualTo("OWNER");
        assertThat(snapshot(session)).isEqualTo(first);
        assertThat(session.getAttribute("userId")).isEqualTo(owner.getId());
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(SCHEMA);
    }

    @Test
    void twoUsersJoinTheSameHouseWithNormalizedInviteAndBothSeeTheMembers() throws Exception {
        User owner = user("Owner");
        User guest = user("Ospite");
        House house = houses.create(session(owner), "Casa condivisa", null);
        String code = house.getInviteCode().toLowerCase(java.util.Locale.ROOT);
        MockHttpSession guestSession = session(guest);
        mvc.perform(post("/api/houses/join").session(guestSession).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("code", " " + code.substring(0, 3) + " \t" + code.substring(3) + " "))))
                .andExpect(status().isOk());
        JsonNode ownerSnapshot = snapshot(session(owner));
        JsonNode guestSnapshot = snapshot(guestSession);
        assertThat(ownerSnapshot.get("house")).isEqualTo(guestSnapshot.get("house"));
        assertThat(ownerSnapshot.get("members")).isEqualTo(guestSnapshot.get("members"));
        assertThat(guestSnapshot.get("members").size()).isEqualTo(2);
        assertThat(guestSnapshot.get("members").get(1).get("membershipRole").asText()).isEqualTo("MEMBER");
        assertThat(guestSnapshot.get("user").get("id").asLong()).isEqualTo(guest.getId());
        assertThat(guestSnapshot.get("house").get("emoji").asText()).isEqualTo("🏠");
    }

    @Test
    void snapshotIgnoresForeignHouseIdsAndDoesNotLeakOtherMembers() throws Exception {
        User first = user("Prima casa");
        User second = user("Seconda casa");
        House own = houses.create(session(first), "Prima", "🏠");
        House foreign = houses.create(session(second), "Seconda", "🏡");
        mvc.perform(get("/api/snapshot").session(session(first)).param("houseId", foreign.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("house.id").value(own.getId()))
                .andExpect(jsonPath("members.length()").value(1)).andExpect(jsonPath("members[0].id").value(first.getId()));
    }

    @Test
    void allNewEndpointsRejectUnauthenticatedRequestsWithoutCreatingSessions() throws Exception {
        var snapshot = mvc.perform(get("/api/snapshot")).andExpect(status().isUnauthorized()).andReturn();
        var create = mvc.perform(post("/api/houses/create").contentType("application/json").content("{\"name\":\"Casa\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("error").isString()).andReturn();
        var join = mvc.perform(post("/api/houses/join").contentType("application/json").content("{\"code\":\"ABC123\"}"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(snapshot.getRequest().getSession(false)).isNull();
        assertThat(create.getRequest().getSession(false)).isNull();
        assertThat(join.getRequest().getSession(false)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"snapshot", "create", "join"})
    void disabledUsersAreRejectedAndTheirSessionIsInvalidated(String operation) throws Exception {
        User user = user("Disattivato");
        MockHttpSession session = session(user);
        jdbc.update("UPDATE app_user SET active = FALSE WHERE id = ?", user.getId());
        var request = operation.equals("snapshot") ? get("/api/snapshot")
                : post("/api/houses/" + operation).contentType("application/json")
                .content(operation.equals("create") ? "{\"name\":\"Casa\"}" : "{\"code\":\"ABC123\"}");
        mvc.perform(request.session(session)).andExpect(status().isForbidden()).andExpect(jsonPath("error").isString());
        assertThat(session.isInvalid()).isTrue();
        assertThat(membershipWrites.findActiveByUserId(user.getId())).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\"\"}", "{\"name\":\"   \"}",
            "{\"name\":\"Casa\",\"emoji\":\"123456789012345678901\"}"})
    void invalidHouseDataDoesNotCreateMembership(String body) throws Exception {
        User user = user("Invalidi");
        mvc.perform(post("/api/houses/create").session(session(user)).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("error").isString());
        assertThat(membershipWrites.findActiveByUserId(user.getId())).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ABC12", "ABC1234", "ABC!23", "àBC123"})
    void invalidInviteFormatsAreRejected(String code) throws Exception {
        User user = user("Codice");
        mvc.perform(post("/api/houses/join").session(session(user)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isBadRequest());
        assertThat(membershipWrites.findActiveByUserId(user.getId())).isNull();
    }

    @Test
    void nonexistentAndArchivedInvitesAreRejected() throws Exception {
        User guest = user("Ospite");
        mvc.perform(post("/api/houses/join").session(session(guest)).contentType("application/json").content("{\"code\":\"000000\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("error").isString());
        House house = houses.create(session(user("Owner")), "Archiviata", null);
        jdbc.update("UPDATE house SET archived = TRUE WHERE id = ?", house.getId());
        mvc.perform(post("/api/houses/join").session(session(guest)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("code", house.getInviteCode()))))
                .andExpect(status().isForbidden());
        assertThat(membershipWrites.findActiveByUserId(guest.getId())).isNull();
    }

    @Test
    void existingMembershipPreventsCreatingOrJoiningAnotherHouse() throws Exception {
        User user = user("Già membro");
        House own = houses.create(session(user), "Prima", null);
        House other = houses.create(session(user("Altro")), "Altra", null);
        int before = houseCount();
        mvc.perform(post("/api/houses/create").session(session(user)).contentType("application/json").content("{\"name\":\"Seconda\"}"))
                .andExpect(status().isConflict());
        for (String code : List.of(own.getInviteCode(), other.getInviteCode())) {
            mvc.perform(post("/api/houses/join").session(session(user)).contentType("application/json")
                            .content(json.writeValueAsString(Map.of("code", code))))
                    .andExpect(status().isConflict());
        }
        assertThat(houseCount()).isEqualTo(before);
        assertThat(membershipWrites.findActiveByUserId(user.getId()).getHouseId()).isEqualTo(own.getId());
    }

    @Test
    void rejoiningReactivatesExistingMembershipWithoutResurrectingAnOldOwnerRole() throws Exception {
        User owner = user("Owner");
        User former = user("Ex membro");
        House house = houses.create(session(owner), "Casa", null);
        Membership previous = new Membership();
        previous.setHouseId(house.getId());
        previous.setUserId(former.getId());
        previous.setRole("OWNER");
        previous.setActive(false);
        membershipWrites.save(previous);
        assertThat(snapshot(session(owner)).get("members").get(1).get("memberActive").asBoolean()).isFalse();
        houses.join(session(former), house.getInviteCode());
        Membership current = membershipWrites.findActiveByUserId(former.getId());
        assertThat(current.getId()).isEqualTo(previous.getId());
        assertThat(current.getRole()).isEqualTo("MEMBER");
        assertThat(snapshot(session(owner)).get("members").size()).isEqualTo(2);
    }

    @Test
    void snapshotShowsArchivedHouseAndUpdatedProfileWithoutLosingMembership() throws Exception {
        User user = user("Prima");
        House house = houses.create(session(user), "Casa", null);
        MockHttpSession session = session(user);
        mvc.perform(put("/api/profile/save").session(session).contentType("application/json").content("{\"name\":\"Dopo\"}"))
                .andExpect(status().isOk());
        jdbc.update("UPDATE house SET archived = TRUE WHERE id = ?", house.getId());
        JsonNode snapshot = snapshot(session);
        assertThat(snapshot.get("house").get("archived").asBoolean()).isTrue();
        assertThat(snapshot.get("user").get("name").asText()).isEqualTo("Dopo");
        assertThat(snapshot.get("members").get(0).get("name").asText()).isEqualTo("Dopo");
        assertThatThrownBy(() -> houses.create(session, "Altra", null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void membershipFailureRollsBackTheNewHouse() {
        User user = user("Rollback");
        int before = houseCount();
        doThrow(new DataIntegrityViolationException("Errore membership simulato"))
                .when(membershipWrites).save(any(Membership.class));
        assertThatThrownBy(() -> houses.create(session(user), "Non deve restare", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(houseCount()).isEqualTo(before);
        assertThat(membershipWrites.findActiveByUserId(user.getId())).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void inviteCollisionRetriesInANewTransactionWithoutLeavingOrphanHouses() {
        House existing = houses.create(session(user("Esistente")), "Già presente", null);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            House pending = invocation.getArgument(0);
            if (calls.getAndIncrement() == 0) pending.setInviteCode(existing.getInviteCode());
            return invocation.callRealMethod();
        }).when(houseWrites).save(any(House.class));
        User owner = user("Nuovo owner");
        int before = houseCount();
        House created = houses.create(session(owner), "Nuova", null);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(houseCount()).isEqualTo(before + 1);
        assertThat(created.getInviteCode()).isNotEqualTo(existing.getInviteCode());
        assertThat(membershipWrites.findActiveByUserId(owner.getId()).getHouseId()).isEqualTo(created.getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCreatesForOneUserCreateExactlyOneHouseAndOwner() throws Exception {
        User user = user("Concorrenza create");
        int before = houseCount();
        race(() -> { houses.create(session(user), "Una", null); return 201; },
                () -> { houses.create(session(user), "Due", null); return 201; }, 201);
        assertThat(houseCount()).isEqualTo(before + 1);
        assertThat(activeMembershipCount(user)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentJoinsToDifferentHousesAllowExactlyOneMembership() throws Exception {
        House first = houses.create(session(user("Owner 1")), "Prima", null);
        House second = houses.create(session(user("Owner 2")), "Seconda", null);
        User guest = user("Concorrenza join");
        race(() -> { houses.join(session(guest), first.getInviteCode()); return 200; },
                () -> { houses.join(session(guest), second.getInviteCode()); return 200; }, 200);
        assertThat(activeMembershipCount(guest)).isEqualTo(1);
    }

    @Test
    void newRoutesSupportCredentialedCorsFromAngular() throws Exception {
        for (String path : List.of("/api/houses/create", "/api/houses/join", "/api/snapshot")) {
            mvc.perform(options(path).header("Origin", "http://localhost:3000")
                            .header("Access-Control-Request-Method", path.endsWith("snapshot") ? "GET" : "POST"))
                    .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }
    }

    private void race(Callable<Integer> first, Callable<Integer> second, int success) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> concurrent(first, ready, start));
            var right = executor.submit(() -> concurrent(second, ready, start));
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(success, 409);
        }
    }

    private int concurrent(Callable<Integer> action, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout barriera");
        try {
            return action.call();
        } catch (ResponseStatusException ex) {
            return ex.getStatusCode().value();
        }
    }

    private int activeMembershipCount(User user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM membership WHERE user_id = ? AND active", Integer.class, user.getId());
    }

    private int houseCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM house", Integer.class);
    }

    private JsonNode snapshot(MockHttpSession session) throws Exception {
        String content = mvc.perform(get("/api/snapshot").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode snapshot = json.readTree(content);
        assertThat(snapshot.get("user").has("password")).isFalse();
        assertThat(content).doesNotContain("password", "$2a$", "$2b$", "password_hash");
        return snapshot;
    }

    private User user(String name) {
        return auth.register(name, UUID.randomUUID() + "@example.invalid", "PasswordProva123!");
    }

    private MockHttpSession session(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID, user.getId());
        return session;
    }

    private static String env(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }
}
