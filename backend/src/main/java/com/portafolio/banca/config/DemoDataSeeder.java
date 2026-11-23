package com.portafolio.banca.config;

import com.portafolio.banca.dto.*;
import com.portafolio.banca.dto.StatusChangeRequests.AccountStatusRequest;
import com.portafolio.banca.entity.*;
import com.portafolio.banca.exception.BusinessRuleException;
import com.portafolio.banca.repository.UserRepository;
import com.portafolio.banca.service.AccountService;
import com.portafolio.banca.service.CustomerService;
import com.portafolio.banca.service.OperationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Carga datos 100 % FICTICIOS si la base está vacía. Todo pasa por los mismos servicios que usa la API
 * (reglas, auditoría e idempotencia incluidas): no se escriben saldos a mano.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppProperties props;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final CustomerService customers;
    private final AccountService accounts;
    private final OperationService operations;
    private final AtomicInteger keySeq = new AtomicInteger();

    public DemoDataSeeder(AppProperties props, UserRepository users, PasswordEncoder encoder,
                          CustomerService customers, AccountService accounts, OperationService operations) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
        this.customers = customers;
        this.accounts = accounts;
        this.operations = operations;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled() || users.count() > 0) return;
        String pwd = props.seed().demoPassword();

        User admin = users.save(new User("Administración (demo)", "admin@banca.demo", encoder.encode(pwd), Role.ADMIN, null));
        User operator = users.save(new User("Operador (demo)", "operador@banca.demo", encoder.encode(pwd), Role.OPERATOR, null));

        CustomerResponse ana = customers.create(operator,
                new CustomerRequest("Ana García (demo)", "ana.garcia@banca.demo", "+52 55 0000 0001", pwd));
        CustomerResponse luis = customers.create(operator,
                new CustomerRequest("Luis Pérez (demo)", "luis.perez@banca.demo", "+52 55 0000 0002", pwd));
        CustomerResponse carla = customers.create(operator,
                new CustomerRequest("Carla Ruiz (demo)", "carla.ruiz@banca.demo", "+52 55 0000 0003", pwd));

        AccountResponse anaSavings = accounts.open(operator, new AccountRequest(ana.id(), AccountType.SAVINGS, Currency.MXN));
        AccountResponse anaChecking = accounts.open(operator, new AccountRequest(ana.id(), AccountType.CHECKING, Currency.MXN));
        AccountResponse anaUsd = accounts.open(operator, new AccountRequest(ana.id(), AccountType.SAVINGS, Currency.USD));
        AccountResponse luisChecking = accounts.open(operator, new AccountRequest(luis.id(), AccountType.CHECKING, Currency.MXN));
        AccountResponse carlaSavings = accounts.open(operator, new AccountRequest(carla.id(), AccountType.SAVINGS, Currency.MXN));

        User anaUser = users.findByEmailIgnoreCase("ana.garcia@banca.demo").orElseThrow();
        User luisUser = users.findByEmailIgnoreCase("luis.perez@banca.demo").orElseThrow();
        User carlaUser = users.findByEmailIgnoreCase("carla.ruiz@banca.demo").orElseThrow();

        deposit(anaUser, anaChecking, "25000.00", "Nómina (ficticia)");
        deposit(anaUser, anaSavings, "12000.00", "Ahorro inicial (ficticio)");
        deposit(anaUser, anaUsd, "850.00", "Depósito en dólares (ficticio)");
        deposit(luisUser, luisChecking, "8000.00", "Nómina (ficticia)");
        deposit(carlaUser, carlaSavings, "3200.00", "Depósito inicial (ficticio)");

        transfer(anaUser, anaChecking, anaSavings, "3000.00", "Apartado para ahorro");
        transfer(anaUser, anaChecking, luisChecking, "1250.50", "Pago de renta compartida");
        withdraw(anaUser, anaChecking, "1800.00", "Retiro en cajero (simulado)");
        transfer(luisUser, luisChecking, anaChecking, "420.00", "Cena del viernes");
        withdraw(luisUser, luisChecking, "500.00", "Retiro en ventanilla (simulado)");
        // Ejemplos de rechazos: quedan en el historial con status REJECTED.
        withdraw(luisUser, luisChecking, "99999.00", "Intento con saldo insuficiente");
        transfer(anaUser, anaUsd, luisChecking, "100.00", "Intento entre monedas distintas");

        // Una cuenta bloqueada para demostrar las reglas de estado.
        accounts.changeStatus(operator, carlaSavings.id(),
                new AccountStatusRequest(AccountStatus.BLOCKED, "Revisión de seguridad (demo)"));

        log.info("Datos demo FICTICIOS cargados: 3 clientes, 5 cuentas. Usuarios: admin@banca.demo, "
                + "operador@banca.demo, ana.garcia@banca.demo, luis.perez@banca.demo, carla.ruiz@banca.demo");
    }

    private String key() {
        return "seed-" + String.format("%04d", keySeq.incrementAndGet());
    }

    private void deposit(User actor, AccountResponse account, String amount, String description) {
        tolerate(() -> operations.deposit(actor, account.id(),
                new MoneyOperationRequest(new BigDecimal(amount), description), key()));
    }

    private void withdraw(User actor, AccountResponse account, String amount, String description) {
        tolerate(() -> operations.withdraw(actor, account.id(),
                new MoneyOperationRequest(new BigDecimal(amount), description), key()));
    }

    private void transfer(User actor, AccountResponse from, AccountResponse to, String amount, String description) {
        tolerate(() -> operations.transfer(actor,
                new TransferRequest(from.id(), to.number(), new BigDecimal(amount), description), key()));
    }

    /** Los rechazos de ejemplo son esperados: quedan registrados y el seed continúa. */
    private static void tolerate(Runnable op) {
        try {
            op.run();
        } catch (BusinessRuleException expected) {
            log.debug("Rechazo de ejemplo registrado: {}", expected.getCode());
        }
    }
}
