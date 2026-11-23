package com.portafolio.banca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portafolio.banca.dto.MoneyOperationRequest;
import com.portafolio.banca.entity.*;
import com.portafolio.banca.exception.BusinessRuleException;
import com.portafolio.banca.repository.*;
import com.portafolio.banca.service.OperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pruebas de aceptación de la guía (sección 7), de punta a punta contra la API con H2 en memoria. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankingApiIntegrationTest {

    static final String PASSWORD = "Password123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired UserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired AccountRepository accounts;
    @Autowired OperationService operations;
    @SpyBean TransactionRepository transactions;

    String anaToken;
    String luisToken;
    String operatorToken;
    Long anaMxn;      // cuenta MXN de Ana
    Long anaMxn2;     // segunda cuenta MXN de Ana
    Long anaUsd;      // cuenta USD de Ana
    Long luisMxn;     // cuenta MXN de Luis

    @BeforeEach
    void setUp() throws Exception {
        reset(transactions);
        for (String table : List.of("idempotency_keys", "audit_events", "transactions", "accounts", "users", "customers")) {
            jdbc.update("delete from " + table);
        }
        Customer ana = customers.save(new Customer("Ana Test", "ana@test.local", "+52 55 0000 0001"));
        Customer luis = customers.save(new Customer("Luis Test", "luis@test.local", "+52 55 0000 0002"));
        users.save(new User("Ana Test", "ana@test.local", encoder.encode(PASSWORD), Role.CUSTOMER, ana));
        users.save(new User("Luis Test", "luis@test.local", encoder.encode(PASSWORD), Role.CUSTOMER, luis));
        users.save(new User("Operador", "op@test.local", encoder.encode(PASSWORD), Role.OPERATOR, null));
        anaMxn = accounts.save(new Account(ana, "990000000001", AccountType.CHECKING, Currency.MXN)).getId();
        anaMxn2 = accounts.save(new Account(ana, "990000000002", AccountType.SAVINGS, Currency.MXN)).getId();
        anaUsd = accounts.save(new Account(ana, "990000000003", AccountType.SAVINGS, Currency.USD)).getId();
        luisMxn = accounts.save(new Account(luis, "990000000004", AccountType.CHECKING, Currency.MXN)).getId();

        anaToken = login("ana@test.local");
        luisToken = login("luis@test.local");
        operatorToken = login("op@test.local");
    }

    // ---------- helpers ----------

    String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    ResultActions send(MockHttpServletRequestBuilder req, String token, Object body, String idempotencyKey) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (idempotencyKey != null) req.header("Idempotency-Key", idempotencyKey);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    static String newKey() { return UUID.randomUUID().toString(); }

    ResultActions deposit(String token, Long accountId, Object amount) throws Exception {
        return send(post("/api/accounts/{id}/deposits", accountId), token, Map.of("amount", amount), newKey());
    }

    ResultActions withdraw(String token, Long accountId, Object amount) throws Exception {
        return send(post("/api/accounts/{id}/withdrawals", accountId), token, Map.of("amount", amount), newKey());
    }

    ResultActions transfer(String token, Long sourceId, String destinationNumber, Object amount, String key) throws Exception {
        return send(post("/api/transfers"), token,
                Map.of("sourceAccountId", sourceId, "destinationAccountNumber", destinationNumber, "amount", amount), key);
    }

    BigDecimal balance(Long accountId) {
        return accounts.findById(accountId).orElseThrow().getBalance();
    }

    long transactionCount() {
        return jdbc.queryForObject("select count(*) from transactions", Long.class);
    }

    User user(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow();
    }

    // ---------- 7. Pruebas mínimas de aceptación ----------

    @Test
    @DisplayName("Depósito aumenta saldo y crea movimiento")
    void depositIncreasesBalanceAndCreatesMovement() throws Exception {
        deposit(anaToken, anaMxn, "1500.50")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/transactions/TRX-")))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(1500.50))
                .andExpect(jsonPath("$.destinationBalanceAfter").value(1500.50));

        assertThat(balance(anaMxn)).isEqualByComparingTo("1500.50");
        send(get("/api/accounts/{id}/transactions", anaMxn), anaToken, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(1500.50));
    }

    @Test
    @DisplayName("Retiro válido disminuye saldo; retiro mayor al saldo se rechaza")
    void withdrawalRules() throws Exception {
        deposit(anaToken, anaMxn, 1000).andExpect(status().isCreated());

        withdraw(anaToken, anaMxn, "250.25").andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceBalanceAfter").value(749.75));
        assertThat(balance(anaMxn)).isEqualByComparingTo("749.75");

        withdraw(anaToken, anaMxn, "749.76")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.reference", startsWith("TRX-")));
        assertThat(balance(anaMxn)).isEqualByComparingTo("749.75");

        // El intento rechazado queda en el historial, sin alterar el saldo.
        send(get("/api/accounts/{id}/transactions", anaMxn).param("status", "REJECTED"), anaToken, null, null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].failureReason", containsString("Saldo insuficiente")))
                .andExpect(jsonPath("$.content[0].balanceAfter").doesNotExist());
    }

    @Test
    @DisplayName("Transferencia debita y acredita exactamente el mismo importe")
    void transferMovesExactAmount() throws Exception {
        deposit(anaToken, anaMxn, 1000).andExpect(status().isCreated());

        String body = transfer(anaToken, anaMxn, "9900-0000-0004", "333.33", newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.sourceBalanceAfter").value(666.67))
                // Ana no ve el saldo ni el número completo de la cuenta de Luis.
                .andExpect(jsonPath("$.destinationBalanceAfter").doesNotExist())
                .andExpect(jsonPath("$.destinationAccountNumber").value("•••• 0004"))
                .andReturn().getResponse().getContentAsString();

        assertThat(balance(anaMxn)).isEqualByComparingTo("666.67");
        assertThat(balance(luisMxn)).isEqualByComparingTo("333.33");

        String reference = json.readTree(body).get("reference").asText();
        // Luis ve el mismo movimiento como crédito, con su saldo resultante.
        send(get("/api/transactions/{ref}", reference), luisToken, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinationBalanceAfter").value(333.33))
                .andExpect(jsonPath("$.sourceAccountNumber").value("•••• 0001"));
        send(get("/api/accounts/{id}/transactions", luisMxn), luisToken, null, null)
                .andExpect(jsonPath("$.content[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.content[0].amount").value(333.33));
    }

    @Test
    @DisplayName("Un error durante la transferencia revierte todo (rollback total)")
    void transferFailureRollsBackEverything() throws Exception {
        deposit(anaToken, anaMxn, 1000).andExpect(status().isCreated());
        long before = transactionCount();

        // Falla al registrar el movimiento, DESPUÉS de haber escrito (flush) el débito y el crédito.
        doThrow(new RuntimeException("Fallo simulado de base de datos"))
                .when(transactions).save(argThat(t -> t.getType() == TransactionType.TRANSFER));

        String key = newKey();
        transfer(anaToken, anaMxn, "990000000004", 400, key)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(balance(anaMxn)).isEqualByComparingTo("1000.00");
        assertThat(balance(luisMxn)).isEqualByComparingTo("0.00");
        assertThat(transactionCount()).isEqualTo(before);

        // La clave de idempotencia también se revirtió: el reintento con la misma clave se ejecuta.
        reset(transactions);
        transfer(anaToken, anaMxn, "990000000004", 400, key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"));
        assertThat(balance(luisMxn)).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("Dos retiros concurrentes no pueden gastar el mismo saldo")
    void concurrentWithdrawalsCannotDoubleSpend() throws Exception {
        deposit(anaToken, anaMxn, 100).andExpect(status().isCreated());
        User ana = user("ana@test.local");

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    operations.withdraw(ana, anaMxn, new MoneyOperationRequest(new BigDecimal("70.00"), null), newKey());
                    return true;
                } catch (BusinessRuleException rejected) {
                    assertThat(rejected.getCode()).isEqualTo("INSUFFICIENT_FUNDS");
                    return false;
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> r : results) if (r.get(30, TimeUnit.SECONDS)) succeeded++;
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(balance(anaMxn)).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("Transferencias cruzadas concurrentes terminan sin interbloqueo y conservan el total")
    void crossTransfersKeepTotal() throws Exception {
        deposit(anaToken, anaMxn, 500).andExpect(status().isCreated());
        deposit(luisToken, luisMxn, 500).andExpect(status().isCreated());
        User ana = user("ana@test.local");
        User luis = user("luis@test.local");

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            boolean fromAna = i % 2 == 0;
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    operations.transfer(fromAna ? ana : luis, new com.portafolio.banca.dto.TransferRequest(
                            fromAna ? anaMxn : luisMxn, fromAna ? "990000000004" : "990000000001",
                            new BigDecimal("10.00"), null), newKey());
                } catch (BusinessRuleException ignored) {
                    // saldo insuficiente es válido; lo importante es el total
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(balance(anaMxn).add(balance(luisMxn))).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Repetir la petición con la misma Idempotency-Key no duplica la operación")
    void sameIdempotencyKeyDoesNotDuplicate() throws Exception {
        deposit(anaToken, anaMxn, 1000).andExpect(status().isCreated());
        String key = newKey();

        String first = transfer(anaToken, anaMxn, "990000000004", 100, key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andReturn().getResponse().getContentAsString();
        String second = transfer(anaToken, anaMxn, "990000000004", 100, key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(second).get("reference")).isEqualTo(json.readTree(first).get("reference"));
        assertThat(balance(anaMxn)).isEqualByComparingTo("900.00");
        assertThat(balance(luisMxn)).isEqualByComparingTo("100.00");

        // La misma clave con otros datos se rechaza.
        transfer(anaToken, anaMxn, "990000000004", 101, key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        // Las claves son por usuario: Luis puede usar la misma sin interferir.
        deposit(luisToken, luisMxn, 5).andExpect(status().isCreated());
        send(post("/api/accounts/{id}/deposits", luisMxn), luisToken, Map.of("amount", 5), key)
                .andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replayed", "false"));
    }

    @Test
    @DisplayName("Un rechazo también es idempotente: el reintento devuelve el mismo rechazo")
    void rejectionIsReplayed() throws Exception {
        String key = newKey();
        String ref = json.readTree(send(post("/api/accounts/{id}/withdrawals", anaMxn), anaToken, Map.of("amount", 50), key)
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString()).get("reference").asText();

        deposit(anaToken, anaMxn, 500).andExpect(status().isCreated());
        send(post("/api/accounts/{id}/withdrawals", anaMxn), anaToken, Map.of("amount", 50), key)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reference").value(ref));
        assertThat(balance(anaMxn)).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Peticiones simultáneas con la misma clave producen un solo movimiento")
    void concurrentSameKeyProducesOneMovement() throws Exception {
        deposit(anaToken, anaMxn, 1000).andExpect(status().isCreated());
        User ana = user("ana@test.local");
        String key = newKey();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> refs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            refs.add(pool.submit(() -> {
                start.await();
                return operations.withdraw(ana, anaMxn, new MoneyOperationRequest(new BigDecimal("100.00"), null), key)
                        .transaction().reference();
            }));
        }
        start.countDown();
        Set<String> distinct = new HashSet<>();
        for (Future<String> r : refs) distinct.add(r.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(distinct).hasSize(1);
        assertThat(balance(anaMxn)).isEqualByComparingTo("900.00");
    }

    @Test
    @DisplayName("Un usuario no puede consultar ni mover dinero de cuentas ajenas")
    void ownershipIsEnforced() throws Exception {
        deposit(anaToken, anaMxn, 1000).andExpect(status().isCreated());

        send(get("/api/accounts/{id}", anaMxn), luisToken, null, null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        send(get("/api/accounts/{id}/transactions", anaMxn), luisToken, null, null).andExpect(status().isNotFound());
        withdraw(luisToken, anaMxn, 10).andExpect(status().isNotFound());
        deposit(luisToken, anaMxn, 10).andExpect(status().isNotFound());
        transfer(luisToken, anaMxn, "990000000004", 10, newKey()).andExpect(status().isNotFound());

        // El listado de Luis solo contiene sus cuentas, aunque pida las de otro cliente.
        Long anaCustomerId = user("ana@test.local").getCustomerId();
        send(get("/api/accounts").param("customerId", anaCustomerId.toString()), luisToken, null, null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(luisMxn));
        send(get("/api/customers/{id}", anaCustomerId), luisToken, null, null).andExpect(status().isNotFound());

        // Comprobante de una operación en la que Luis no participa.
        String ref = json.readTree(withdraw(anaToken, anaMxn, 10).andReturn().getResponse().getContentAsString())
                .get("reference").asText();
        send(get("/api/transactions/{ref}", ref), luisToken, null, null).andExpect(status().isNotFound());

        assertThat(balance(anaMxn)).isEqualByComparingTo("990.00");
    }

    @Test
    @DisplayName("Roles: el operador consulta pero no mueve dinero; el cliente no administra")
    void rolesAreEnforced() throws Exception {
        send(get("/api/accounts/{id}", anaMxn), operatorToken, null, null).andExpect(status().isOk());
        deposit(operatorToken, anaMxn, 10)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        send(post("/api/customers"), anaToken,
                Map.of("fullName", "X", "email", "x@test.local", "phone", "5550000000", "password", "Password123"), null)
                .andExpect(status().isForbidden());
        send(patch("/api/accounts/{id}/status", luisMxn), anaToken, Map.of("status", "BLOCKED"), null)
                .andExpect(status().isForbidden());
        send(get("/api/audit-events"), operatorToken, null, null).andExpect(status().isForbidden());
        send(get("/api/accounts"), null, null, null)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Valida importes negativos, cero, precisión, límite y cabecera Idempotency-Key")
    void validatesAmounts() throws Exception {
        deposit(anaToken, anaMxn, -5).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
        deposit(anaToken, anaMxn, 0).andExpect(status().isBadRequest());
        deposit(anaToken, anaMxn, "10.001").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message", containsString("2 decimales")));
        deposit(anaToken, anaMxn, "1000000.01").andExpect(status().isBadRequest());
        send(post("/api/accounts/{id}/deposits", anaMxn), anaToken, Map.of(), newKey()).andExpect(status().isBadRequest());

        send(post("/api/accounts/{id}/deposits", anaMxn), anaToken, Map.of("amount", 10), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        send(post("/api/accounts/{id}/deposits", anaMxn), anaToken, Map.of("amount", 10), "corta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"));

        assertThat(transactionCount()).isZero();
    }

    @Test
    @DisplayName("Reglas de transferencia: misma cuenta, moneda distinta y cuentas bloqueadas")
    void transferRules() throws Exception {
        deposit(anaToken, anaMxn, 1000).andExpect(status().isCreated());
        deposit(anaToken, anaUsd, 50).andExpect(status().isCreated());

        transfer(anaToken, anaMxn, "990000000001", 10, newKey())
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SAME_ACCOUNT"));
        transfer(anaToken, anaUsd, "990000000004", 10, newKey())
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
        transfer(anaToken, anaMxn, "990000009999", 10, newKey())
                .andExpect(status().isNotFound());

        // Cuenta destino bloqueada por un operador.
        send(patch("/api/accounts/{id}/status", luisMxn), operatorToken,
                Map.of("status", "BLOCKED", "reason", "prueba"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOCKED"));
        transfer(anaToken, anaMxn, "990000000004", 10, newKey())
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DESTINATION_NOT_ACTIVE"));
        // La cuenta bloqueada tampoco puede operar.
        deposit(luisToken, luisMxn, 10)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));

        // Cliente bloqueado: no puede operar ninguna de sus cuentas.
        Long anaCustomerId = user("ana@test.local").getCustomerId();
        send(patch("/api/customers/{id}/status", anaCustomerId), operatorToken, Map.of("status", "BLOCKED"), null)
                .andExpect(status().isOk());
        transfer(anaToken, anaMxn, "990000000002", 10, newKey())
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CUSTOMER_BLOCKED"));

        assertThat(balance(anaMxn)).isEqualByComparingTo("1000.00");
        assertThat(balance(anaMxn2)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Cerrar una cuenta exige saldo cero y es definitivo")
    void closingAccount() throws Exception {
        deposit(anaToken, anaMxn, 10).andExpect(status().isCreated());
        send(patch("/api/accounts/{id}/status", anaMxn), operatorToken, Map.of("status", "CLOSED"), null)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ACCOUNT_HAS_BALANCE"));

        send(patch("/api/accounts/{id}/status", anaMxn2), operatorToken, Map.of("status", "CLOSED"), null)
                .andExpect(status().isOk());
        send(patch("/api/accounts/{id}/status", anaMxn2), operatorToken, Map.of("status", "ACTIVE"), null)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ACCOUNT_CLOSED"));
    }

    @Nested
    @DisplayName("Criterio de proyecto completo (sección 10)")
    class EndToEnd {

        @Test
        @DisplayName("Crear cliente y dos cuentas, depositar, transferir, consultar saldos e historial")
        void fullFlow() throws Exception {
            String created = send(post("/api/customers"), operatorToken, Map.of(
                    "fullName", "Marta Nueva (demo)", "email", "marta@test.local",
                    "phone", "+52 55 1234 5678", "password", "Secreta123"), null)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(created).doesNotContain("Secreta123");
            send(post("/api/customers"), operatorToken, Map.of(
                    "fullName", "Otra", "email", "MARTA@test.local", "phone", "5550000000", "password", "Secreta123"), null)
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

            String marta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", "marta@test.local", "password", "Secreta123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                    .andReturn().getResponse().getContentAsString();
            String token = json.readTree(marta).get("token").asText();

            JsonNode a1 = json.readTree(send(post("/api/accounts"), token, Map.of("type", "CHECKING", "currency", "MXN"), null)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.number", matchesPattern("99\\d{10}")))
                    .andExpect(jsonPath("$.balance").value(0))
                    .andReturn().getResponse().getContentAsString());
            JsonNode a2 = json.readTree(send(post("/api/accounts"), token, Map.of("type", "SAVINGS", "currency", "MXN"), null)
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

            deposit(token, a1.get("id").asLong(), "2000.00").andExpect(status().isCreated());
            transfer(token, a1.get("id").asLong(), a2.get("number").asText(), "750.00", newKey())
                    .andExpect(status().isCreated());

            send(get("/api/accounts/{id}", a1.get("id").asLong()), token, null, null)
                    .andExpect(jsonPath("$.balance").value(1250.00));
            send(get("/api/accounts/{id}", a2.get("id").asLong()), token, null, null)
                    .andExpect(jsonPath("$.balance").value(750.00));
            send(get("/api/accounts/{id}/transactions", a1.get("id").asLong()), token, null, null)
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].type").value("TRANSFER"))
                    .andExpect(jsonPath("$.content[0].direction").value("DEBIT"))
                    .andExpect(jsonPath("$.content[1].type").value("DEPOSIT"));

            // La auditoría registra el flujo sin datos sensibles.
            String adminAudit = jdbc.queryForList("select action || ' ' || coalesce(detail, '') as line from audit_events",
                    String.class).toString();
            assertThat(adminAudit).contains("CUSTOMER_CREATE", "ACCOUNT_OPEN", "DEPOSIT", "TRANSFER")
                    .doesNotContain("Secreta123").doesNotContain(a2.get("number").asText());
        }
    }
}
