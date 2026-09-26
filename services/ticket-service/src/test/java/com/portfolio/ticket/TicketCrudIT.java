package com.portfolio.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.ticket.api.dto.CreateTicketRequest;
import com.portfolio.ticket.api.dto.UpdateTicketRequest;
import com.portfolio.ticket.domain.Priority;
import com.portfolio.ticket.domain.TicketStatus;
import com.portfolio.ticket.infrastructure.security.PlatformJwtAuthenticationConverter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TicketCrudIT {

    private static final String DESCRIPTION = "A sufficiently detailed ticket description";
    private static final UUID ACME_TENANT_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_TENANT_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000001");

    private static final Actor ACME_CUSTOMER =
            actor("10000000-0000-4000-8000-000000000101", ACME_TENANT_ID, "CUSTOMER");
    private static final Actor SECOND_ACME_CUSTOMER =
            actor("10000000-0000-4000-8000-000000000102", ACME_TENANT_ID, "CUSTOMER");
    private static final Actor OTHER_TENANT_CUSTOMER =
            actor("20000000-0000-4000-8000-000000000101", OTHER_TENANT_ID, "CUSTOMER");
    private static final Actor ACME_AGENT =
            actor("10000000-0000-4000-8000-000000000201", ACME_TENANT_ID, "AGENT");
    private static final Actor ACME_ADMIN =
            actor("10000000-0000-4000-8000-000000000301", ACME_TENANT_ID, "ADMIN");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:0.8.1-pg16")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("ticket_test")
                    .withUsername("ticket_test")
                    .withPassword("ticket_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("debug", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM ticket.ticket");
        jdbc.execute("ALTER SEQUENCE ticket.ticket_number_seq RESTART WITH 1");
    }

    @Test
    @DisplayName("Health is public, but ticket APIs require authentication")
    void protectsTicketApis() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());

        mvc.perform(
                        post("/v1/tickets")
                                .contentType("application/json")
                                .content(json(createRequest("Cannot sign in", null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Client cannot choose the tenant in a create request")
    void rejectsClientSuppliedTenant() throws Exception {
        var request =
                Map.of(
                        "tenantId", OTHER_TENANT_ID,
                        "subject", "Cannot sign in",
                        "description", DESCRIPTION);

        mvc.perform(
                        post("/v1/tickets")
                                .with(authenticatedAs(ACME_CUSTOMER))
                                .contentType("application/json")
                                .content(json(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A new ticket belongs to the authenticated customer and starts OPEN")
    void createsOpenTicketForAuthenticatedCustomer() throws Exception {
        var result =
                createTicketAs(ACME_CUSTOMER, "Cannot sign in", null)
                        .andExpect(status().isCreated())
                        .andExpect(
                                jsonPath("$.requesterId").value(ACME_CUSTOMER.userId().toString()))
                        .andExpect(jsonPath("$.status").value(TicketStatus.OPEN.name()))
                        .andExpect(jsonPath("$.priority").value(Priority.MEDIUM.name()))
                        .andReturn();

        var ticketId = ticketIdFrom(result.getResponse().getContentAsString());
        assertThat(storedTenantId(ticketId)).isEqualTo(ACME_TENANT_ID);
    }

    @Test
    @DisplayName("Customers see their own tickets; support sees tickets in its tenant")
    void appliesCustomerAndSupportVisibility() throws Exception {
        var customerTicket = createdTicketIdAs(ACME_CUSTOMER, "Customer ticket", Priority.HIGH);
        var otherCustomerTicket =
                createdTicketIdAs(SECOND_ACME_CUSTOMER, "Other customer ticket", Priority.LOW);
        var otherTenantTicket =
                createdTicketIdAs(OTHER_TENANT_CUSTOMER, "Other tenant ticket", Priority.URGENT);

        mvc.perform(get("/v1/tickets").with(authenticatedAs(ACME_CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(customerTicket.toString()));

        mvc.perform(get("/v1/tickets").with(authenticatedAs(ACME_AGENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        getTicketAs(ACME_CUSTOMER, otherCustomerTicket).andExpect(status().isNotFound());
        getTicketAs(ACME_AGENT, otherTenantTicket).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Customer cannot change ticket management fields")
    void preventsCustomerFromChangingPriority() throws Exception {
        var ticketId = createdTicketIdAs(ACME_CUSTOMER, "Priority policy", Priority.HIGH);

        updateTicketAs(ACME_CUSTOMER, ticketId, changePriorityTo(Priority.URGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Customer and support can complete the documented ticket lifecycle")
    void followsTicketLifecycle() throws Exception {
        var ticketId = createdTicketIdAs(ACME_CUSTOMER, "State flow", Priority.HIGH);

        assignTicketAs(ACME_AGENT, ticketId, ACME_AGENT.userId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(TicketStatus.IN_PROGRESS.name()));
        changeStatusAs(ACME_AGENT, ticketId, TicketStatus.WAITING_CUSTOMER)
                .andExpect(status().isOk());
        changeStatusAs(ACME_CUSTOMER, ticketId, TicketStatus.IN_PROGRESS)
                .andExpect(status().isOk());
        changeStatusAs(ACME_AGENT, ticketId, TicketStatus.RESOLVED).andExpect(status().isOk());
        changeStatusAs(ACME_CUSTOMER, ticketId, TicketStatus.CLOSED).andExpect(status().isOk());

        changeStatusAs(ACME_AGENT, ticketId, TicketStatus.IN_PROGRESS)
                .andExpect(status().isForbidden());
        changeStatusAs(ACME_ADMIN, ticketId, TicketStatus.IN_PROGRESS).andExpect(status().isOk());
    }

    @Test
    @DisplayName("An undocumented status transition returns TICKET_INVALID_TRANSITION")
    void rejectsInvalidStatusTransition() throws Exception {
        var ticketId = createdTicketIdAs(ACME_CUSTOMER, "Invalid flow", Priority.MEDIUM);

        changeStatusAs(ACME_AGENT, ticketId, TicketStatus.CLOSED)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_INVALID_TRANSITION"));
    }

    @Test
    @DisplayName("Filters and pagination never expose another tenant's rows")
    void filtersOnlyAuthorizedTenantRows() throws Exception {
        createdTicketIdAs(ACME_CUSTOMER, "Payment failed", Priority.HIGH);
        createdTicketIdAs(SECOND_ACME_CUSTOMER, "Password reset", Priority.LOW);
        createdTicketIdAs(OTHER_TENANT_CUSTOMER, "Payment in other tenant", Priority.HIGH);

        mvc.perform(
                        get("/v1/tickets")
                                .with(authenticatedAs(ACME_AGENT))
                                .queryParam("priority", Priority.HIGH.name())
                                .queryParam("q", "payment")
                                .queryParam("page", "0")
                                .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("Payment failed"));
    }

    private ResultActions createTicketAs(Actor actor, String subject, Priority priority)
            throws Exception {
        return mvc.perform(
                post("/v1/tickets")
                        .with(authenticatedAs(actor))
                        .contentType("application/json")
                        .content(json(createRequest(subject, priority))));
    }

    private UUID createdTicketIdAs(Actor actor, String subject, Priority priority)
            throws Exception {
        var result =
                createTicketAs(actor, subject, priority)
                        .andExpect(status().isCreated())
                        .andReturn();
        return ticketIdFrom(result.getResponse().getContentAsString());
    }

    private ResultActions getTicketAs(Actor actor, UUID ticketId) throws Exception {
        return mvc.perform(get("/v1/tickets/{ticketId}", ticketId).with(authenticatedAs(actor)));
    }

    private ResultActions assignTicketAs(Actor actor, UUID ticketId, UUID assigneeId)
            throws Exception {
        return updateTicketAs(actor, ticketId, assignTo(assigneeId));
    }

    private ResultActions changeStatusAs(Actor actor, UUID ticketId, TicketStatus status)
            throws Exception {
        return updateTicketAs(actor, ticketId, changeStatusTo(status));
    }

    private ResultActions updateTicketAs(Actor actor, UUID ticketId, UpdateTicketRequest request)
            throws Exception {
        return mvc.perform(
                patch("/v1/tickets/{ticketId}", ticketId)
                        .with(authenticatedAs(actor))
                        .contentType("application/json")
                        .content(json(request)));
    }

    private RequestPostProcessor authenticatedAs(Actor actor) {
        var now = Instant.now();
        var jwt =
                Jwt.withTokenValue("test-token")
                        .header("alg", "RS256")
                        .subject(actor.userId().toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .claim("tid", actor.tenantId().toString())
                        .claim("roles", List.of(actor.role()))
                        .build();
        return authentication(new PlatformJwtAuthenticationConverter().convert(jwt));
    }

    private CreateTicketRequest createRequest(String subject, Priority priority) {
        return new CreateTicketRequest(subject, DESCRIPTION, priority);
    }

    private UpdateTicketRequest changePriorityTo(Priority priority) {
        return new UpdateTicketRequest(null, null, null, priority, null, null);
    }

    private UpdateTicketRequest assignTo(UUID assigneeId) {
        return new UpdateTicketRequest(null, null, null, null, null, assigneeId);
    }

    private UpdateTicketRequest changeStatusTo(TicketStatus status) {
        return new UpdateTicketRequest(null, null, null, null, status, null);
    }

    private UUID ticketIdFrom(String responseBody) throws Exception {
        return UUID.fromString(objectMapper.readTree(responseBody).get("id").asText());
    }

    private UUID storedTenantId(UUID ticketId) {
        return jdbc.queryForObject(
                "SELECT tenant_id FROM ticket.ticket WHERE id = ?", UUID.class, ticketId);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static Actor actor(String userId, UUID tenantId, String role) {
        return new Actor(UUID.fromString(userId), tenantId, role);
    }

    private record Actor(UUID userId, UUID tenantId, String role) {}
}
