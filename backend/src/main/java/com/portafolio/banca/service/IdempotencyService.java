package com.portafolio.banca.service;

import com.portafolio.banca.config.AppProperties;
import com.portafolio.banca.entity.IdempotencyKey;
import com.portafolio.banca.entity.User;
import com.portafolio.banca.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyKeyRepository repository;
    private final Clock clock;
    private final Duration ttl;

    public IdempotencyService(IdempotencyKeyRepository repository, Clock clock, AppProperties props) {
        this.repository = repository;
        this.clock = clock;
        this.ttl = Duration.ofHours(props.idempotency().ttlHours());
    }

    /** Clave + huella de la petición. La huella detecta que se reutiliza una clave con otros datos. */
    public record Request(String key, String hash) {}

    /** SHA-256 (hex) de las partes de la petición, separadas para evitar ambigüedades. */
    public static String fingerprint(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) sb.append(p == null ? "" : p.toString()).append('\u001F');
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Registro vigente para (usuario, clave). Uno caducado se elimina y se trata como inexistente. */
    @Transactional
    public Optional<IdempotencyKey> findActive(Long userId, String key) {
        Optional<IdempotencyKey> found = repository.findByUserIdAndKey(userId, key);
        if (found.isPresent() && found.get().isExpired(clock.instant())) {
            repository.delete(found.get());
            repository.flush();
            return Optional.empty();
        }
        return found;
    }

    /**
     * Guarda la clave dentro de la transacción de la operación y fuerza el INSERT inmediato: si otra petición
     * con la misma clave ya la guardó, la restricción única lanza aquí la excepción y todo se revierte.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(User actor, Request request, int responseStatus, String reference) {
        Instant now = clock.instant();
        repository.saveAndFlush(new IdempotencyKey(request.key(), actor.getId(), request.hash(), responseStatus,
                reference, now, now.plus(ttl)));
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteExpired(clock.instant());
        if (deleted > 0) log.info("Claves de idempotencia caducadas eliminadas: {}", deleted);
    }
}
