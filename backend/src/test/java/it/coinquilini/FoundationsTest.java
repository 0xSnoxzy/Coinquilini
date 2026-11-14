package it.coinquilini;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.coinquilini.dao.HouseDAO;
import it.coinquilini.dao.JdbcTransactions;
import it.coinquilini.dao.MembershipDAO;
import it.coinquilini.dao.UserDAO;
import it.coinquilini.model.House;
import it.coinquilini.model.Member;
import it.coinquilini.model.Membership;
import it.coinquilini.model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Test su PostgreSQL reale; ogni esecuzione possiede uno schema nuovo e isolato. */
@SpringBootTest(properties = "spring.sql.init.mode=always")
@AutoConfigureMockMvc
@Transactional
class FoundationsTest {
    private static final String DATABASE_URL = env("TEST_DATABASE_URL",
            "jdbc:postgresql://127.0.0.1:15432/postgres?connectTimeout=5");
    private static final String DATABASE_USER = env("TEST_DATABASE_USER", "postgres");
    private static final String DATABASE_PASSWORD = env("TEST_DATABASE_PASSWORD", "");
    private static final String SCHEMA = "foundation_test_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean schemaCreated;

    @Autowired HouseDAO houses;
    @Autowired MembershipDAO memberships;
    @Autowired UserDAO users;
    @Autowired JdbcTransactions transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) throws SQLException {
        try (var connection = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        // currentSchema contiene solo lo schema appena creato, senza fallback a public.
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
                // Il nome non è configurabile: è generato da questa classe prima dei test.
                statement.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
            }
        }
    }

    @Test
    void repeatedSchemaInitializationPreservesExistingUsersAndData() {
        User existing = user("Prima dello schema");
        existing.setColor("#112233");
        existing.setPhoto("foto-utente");
        users.update(existing);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);

        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(SCHEMA);
        assertThat(users.findById(existing.getId())).usingRecursiveComparison().isEqualTo(existing);
    }

    @Test
    void houseRoundTripPreservesAllFieldsAndGeneratedId() {
        House house = house();
        house.setPhoto("foto-casa");
        house.setEmoji("🌻");
        house.setArchived(true);
        houses.save(house);

        assertThat(house.getId()).isPositive();
        assertThat(houses.findById(house.getId())).usingRecursiveComparison().isEqualTo(house);
    }

    @Test
    void inviteCodeIsNormalizedOnInsertAndLookup() {
        House house = house();
        house.setInviteCode(" aB 12\tcD\n");
        houses.save(house);

        assertThat(house.getInviteCode()).isEqualTo("AB12CD");
        assertThat(houses.findByInviteCode(" ab12 cd ").getId()).isEqualTo(house.getId());
    }

    @Test
    void missingRecordsReturnNullOrEmptyLists() {
        assertThat(houses.findById(-1L)).isNull();
        assertThat(houses.findByInviteCode("ZZZZZZ")).isNull();
        assertThat(houses.findActiveByUserId(-1L)).isNull();
        assertThat(memberships.findActiveByUserId(-1L)).isNull();
        assertThat(memberships.findMembersByHouseId(-1L)).isEmpty();
        assertThat(users.findById(-1L)).isNull();
        assertThat(users.findByEmail("missing@example.invalid")).isNull();
    }

    @Test
    void activeMembershipFindsArchivedHouseAndDisabledAccountWithoutHidingIt() {
        User user = new User(null, "Disattivato", email(), "test-password", "USER", false, "#123456", "");
        users.save(user);
        House house = savedHouse();
        jdbc.update("UPDATE house SET archived = TRUE WHERE id = ?", house.getId());
        Membership owner = membership(house, user, "OWNER", true);
        memberships.save(owner);

        assertThat(owner.getId()).isPositive();
        assertThat(memberships.findActiveByUserId(user.getId()))
                .usingRecursiveComparison().isEqualTo(owner);
        assertThat(houses.findActiveByUserId(user.getId()).getId()).isEqualTo(house.getId());
        assertThat(houses.findActiveByUserId(user.getId()).isArchived()).isTrue();
    }

    @Test
    void memberProjectionIncludesFormerMembersAndExcludesOtherHousesAndPasswords() throws Exception {
        House house = savedHouse();
        User owner = user("Owner");
        User former = new User(null, "Ex", email(), "secret", "ADMIN", false, "#abcdef", "foto");
        users.save(former);
        memberships.save(membership(house, former, "MEMBER", false));
        memberships.save(membership(house, owner, "OWNER", true));
        memberships.save(membership(savedHouse(), user("Altra casa"), "OWNER", true));

        List<Member> members = memberships.findMembersByHouseId(house.getId());
        assertThat(members).extracting(Member::id).containsExactly(owner.getId(), former.getId());
        assertThat(members.getFirst().membershipRole()).isEqualTo("OWNER");
        Member ex = members.get(1);
        assertThat(ex.memberActive()).isFalse();
        assertThat(ex.active()).isFalse();
        assertThat(ex.role()).isEqualTo("ADMIN");
        assertThat(ex.color()).isEqualTo("#abcdef");
        assertThat(ex.photo()).isEqualTo("foto");
        assertThat(ex.membershipId()).isPositive();
        assertThat(json.readTree(json.writeValueAsString(ex)).has("password")).isFalse();
    }

    @Test
    void databaseDefaultsMatchModels() {
        Long houseId = jdbc.queryForObject("INSERT INTO house (name, invite_code) VALUES (?, ?) RETURNING id",
                Long.class, "Default", code());
        User user = user("Membro");
        jdbc.update("INSERT INTO membership (house_id, user_id) VALUES (?, ?)", houseId, user.getId());

        House house = houses.findById(houseId);
        assertThat(house.getEmoji()).isEqualTo(new House().getEmoji());
        assertThat(house.getPhoto()).isEmpty();
        assertThat(house.isArchived()).isFalse();
        Membership membership = memberships.findActiveByUserId(user.getId());
        assertThat(membership.getRole()).isEqualTo(new Membership().getRole());
        assertThat(membership.isActive()).isTrue();
    }

    @Test
    void duplicateInviteCodeIsRejected() {
        House first = savedHouse();
        House duplicate = house();
        duplicate.setInviteCode(first.getInviteCode().toLowerCase());
        assertThatThrownBy(() -> houses.save(duplicate)).isInstanceOf(DuplicateKeyException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ABC12", "ABC1234", "ABC!23", "àBC123"})
    void invalidInviteCodeIsRejected(String code) {
        House house = house();
        house.setInviteCode(code);
        assertThatThrownBy(() -> houses.save(house)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "USER", "owner", ""})
    void invalidMembershipRoleIsRejected(String role) {
        Membership membership = membership(savedHouse(), user("Membro"), role, true);
        assertThatThrownBy(() -> memberships.save(membership)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"house", "user"})
    void nonexistentForeignKeysAreRejected(String missing) {
        Membership membership = membership(savedHouse(), user("Membro"), "MEMBER", true);
        if (missing.equals("house")) membership.setHouseId(-1L);
        else membership.setUserId(-1L);
        assertThatThrownBy(() -> memberships.save(membership)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateUserHousePairIsRejectedEvenWhenInactive() {
        House house = savedHouse();
        User user = user("Membro");
        memberships.save(membership(house, user, "MEMBER", false));
        assertThatThrownBy(() -> memberships.save(membership(house, user, "MEMBER", false)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void secondActiveMembershipIsRejectedAcrossHouses() {
        User user = user("Membro");
        memberships.save(membership(savedHouse(), user, "OWNER", true));
        House second = savedHouse();
        assertThatThrownBy(() -> memberships.save(membership(second, user, "MEMBER", true)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void multipleInactiveMembershipsDoNotPreventOneActiveMembership() {
        User user = user("Membro");
        memberships.save(membership(savedHouse(), user, "MEMBER", false));
        memberships.save(membership(savedHouse(), user, "MEMBER", false));
        assertThat(memberships.findActiveByUserId(user.getId())).isNull();
        assertThat(houses.findActiveByUserId(user.getId())).isNull();
        House active = savedHouse();
        memberships.save(membership(active, user, "OWNER", true));
        assertThat(houses.findActiveByUserId(user.getId()).getId()).isEqualTo(active.getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void multiDaoTransactionCommitsAndAutocommitWorks() {
        User user = user("Prima");
        House house = transactions.execute(() -> {
            user.setName("Dopo");
            users.update(user);
            House saved = savedHouse();
            memberships.save(membership(saved, user, "OWNER", true));
            return saved;
        });
        assertThat(users.findById(user.getId()).getName()).isEqualTo("Dopo");
        assertThat(houses.findActiveByUserId(user.getId()).getId()).isEqualTo(house.getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void runtimeFailureRollsBackUserUpdateAndNewHouseAndMembership() {
        User user = user("Prima");
        House house = house();
        assertThatThrownBy(() -> transactions.run(() -> {
            user.setName("Non salvato");
            users.update(user);
            houses.save(house);
            memberships.save(membership(house, user, "OWNER", true));
            throw new IllegalStateException("Rollback richiesto");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(users.findById(user.getId()).getName()).isEqualTo("Prima");
        assertThat(houses.findById(house.getId())).isNull();
        assertThat(memberships.findActiveByUserId(user.getId())).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sqlFailureRollsBackNewUserAndHouseAcrossDaos() {
        User user = newUser("Nuovo");
        House house = house();
        assertThatThrownBy(() -> transactions.run(() -> {
            users.save(user);
            houses.save(house);
            Membership invalid = membership(house, user, "OWNER", true);
            invalid.setUserId(-1L);
            memberships.save(invalid);
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(users.findById(user.getId())).isNull();
        assertThat(houses.findById(house.getId())).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void nestedTransactionsJoinOuterTransactionAndErrorsRollback() {
        House house = house();
        assertThatThrownBy(() -> transactions.run(() -> {
            transactions.execute(() -> {
                houses.save(house);
                return house.getId();
            });
            throw new AssertionError("Errore dopo il callback annidato");
        })).isInstanceOf(AssertionError.class);
        assertThat(houses.findById(house.getId())).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentMembershipInsertionsAllowExactlyOneWinner() throws Exception {
        User user = user("Concorrenza");
        House first = savedHouse();
        House second = savedHouse();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> concurrentJoin(first, user, ready, start));
            var right = executor.submit(() -> concurrentJoin(second, user, ready, start));
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM membership WHERE user_id = ? AND active",
                Integer.class, user.getId())).isEqualTo(1);
    }

    @Test
    void existingAuthAndProfileEndpointsStillWorkWithJdbcTemplate() throws Exception {
        String email = email();
        var registered = mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of(
                                "name", "David test", "email", email, "password", "test-password"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("password").doesNotExist()).andReturn();
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("email").value(email));
        mvc.perform(put("/api/profile/save").session(session).contentType("application/json")
                        .content("{\"name\":\"David aggiornato\",\"color\":\"#abcdef\",\"photo\":\"foto\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("name").value("David aggiornato"));
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isOk());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "test-password"))))
                .andExpect(status().isOk()).andExpect(jsonPath("name").value("David aggiornato"))
                .andExpect(jsonPath("color").value("#abcdef")).andExpect(jsonPath("photo").value("foto"))
                .andExpect(jsonPath("password").doesNotExist());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    private boolean concurrentJoin(House house, User user, CountDownLatch ready, CountDownLatch start) {
        try {
            transactions.run(() -> {
                jdbc.execute("SET LOCAL lock_timeout = '5s'");
                ready.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout barriera");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                memberships.save(membership(house, user, "MEMBER", true));
            });
            return true;
        } catch (DuplicateKeyException expected) {
            return false;
        }
    }

    private House savedHouse() {
        House house = house();
        houses.save(house);
        return house;
    }

    private House house() {
        House house = new House();
        house.setName("Casa test");
        house.setInviteCode(code());
        return house;
    }

    private Membership membership(House house, User user, String role, boolean active) {
        Membership membership = new Membership();
        membership.setHouseId(house.getId());
        membership.setUserId(user.getId());
        membership.setRole(role);
        membership.setActive(active);
        return membership;
    }

    private User user(String name) {
        User user = newUser(name);
        users.save(user);
        return user;
    }

    private User newUser(String name) {
        return new User(null, name, email(), "test-password", "USER", true, "#2563eb", "");
    }

    private static String code() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase(java.util.Locale.ROOT);
    }

    private static String email() {
        return UUID.randomUUID() + "@example.invalid";
    }

    private static String env(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }
}
