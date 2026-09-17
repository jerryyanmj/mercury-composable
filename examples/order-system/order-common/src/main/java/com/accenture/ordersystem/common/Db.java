package com.accenture.ordersystem.common;

import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.system.PostOffice;
import org.platformlambda.postgres.models.PgQueryStatement;
import org.platformlambda.postgres.models.PgTransactionStatement;
import org.platformlambda.postgres.models.PgUpdateStatement;
import org.platformlambda.postgres.services.PgService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A thin, intention-revealing wrapper over the {@code postgres.service} route.
 *
 * Every database call in this system is an event sent to a route - that is what keeps functions
 * decoupled from the database, and it is not something to hide. What IS worth hiding is the
 * ceremony around it: building a statement object, wrapping it in an envelope, addressing the
 * route, awaiting the future, and casting an {@code Object} body back to rows. That was seven
 * lines and an unchecked cast at each of thirteen call sites.
 *
 * The single unchecked cast now lives here rather than in every caller.
 *
 * <pre>
 *   var db = Db.using(headers, instance);
 *
 *   var row = db.queryOne("SELECT status FROM fulfillment WHERE fulfillment_id = :id",
 *                         Map.of("id", fulfillmentId));
 *
 *   db.transaction()
 *     .add("UPDATE fulfillment SET status = :s WHERE fulfillment_id = :id", params)
 *     .add("INSERT INTO outbox(...) VALUES (...)", outboxParams)
 *     .execute();
 * </pre>
 */
public final class Db {

    private static final long DEFAULT_TIMEOUT_MS = 10_000;

    private final PostOffice po;
    private final long timeoutMs;

    private Db(PostOffice po, long timeoutMs) {
        this.po = po;
        this.timeoutMs = timeoutMs;
    }

    /** The headers and instance a function receives carry its trace context - always pass them on. */
    public static Db using(Map<String, String> headers, int instance) {
        return new Db(new PostOffice(headers, instance), DEFAULT_TIMEOUT_MS);
    }

    public static Db using(Map<String, String> headers, int instance, long timeoutMs) {
        return new Db(new PostOffice(headers, instance), timeoutMs);
    }

    /** The PostOffice behind this helper, for callers that also need to send non-database events. */
    public PostOffice postOffice() {
        return po;
    }

    /** Rows for a query; an empty list when nothing matched (never null). */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> query(String sql, Map<String, Object> parameters) throws Exception {
        PgQueryStatement statement = new PgQueryStatement(sql);
        if (parameters != null && !parameters.isEmpty()) {
            statement.bindNamedParameters(parameters);
        }
        EventEnvelope response = po.request(
                new EventEnvelope().setTo(PgService.ROUTE).setBody(statement), timeoutMs).get();
        Object body = response.getBody();
        return body instanceof List<?> rows ? (List<Map<String, Object>>) rows : List.of();
    }

    public List<Map<String, Object>> query(String sql) throws Exception {
        return query(sql, Map.of());
    }

    /** The first row, or empty - the shape of nearly every lookup in this system. */
    public Optional<Map<String, Object>> queryOne(String sql, Map<String, Object> parameters) throws Exception {
        List<Map<String, Object>> rows = query(sql, parameters);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * The first row, or a failure carrying {@code notFoundMessage}. Use when absence is a
     * programming or consistency error rather than an expected outcome.
     */
    public Map<String, Object> queryOneOrFail(String sql, Map<String, Object> parameters,
                                              String notFoundMessage) throws Exception {
        return queryOne(sql, parameters)
                .orElseThrow(() -> new IllegalStateException(notFoundMessage));
    }

    /** Start a transaction. Every statement added runs in ONE atomic commit. */
    public Transaction transaction() {
        return new Transaction();
    }

    /**
     * A set of statements committed atomically - the mechanism behind the transactional outbox,
     * where a state change and the event announcing it must never disagree.
     *
     * Statements are NOT guaranteed to see each other's writes, so never add a statement that
     * reads what an earlier one in the same transaction wrote; pass the value in as a parameter
     * instead.
     */
    public final class Transaction {
        private final PgTransactionStatement transaction = new PgTransactionStatement();

        public Transaction add(String sql, Map<String, Object> parameters) {
            PgUpdateStatement statement = new PgUpdateStatement(sql);
            if (parameters != null && !parameters.isEmpty()) {
                statement.bindNamedParameters(parameters);
            }
            transaction.addStatement(statement);
            return this;
        }

        public Transaction add(String sql) {
            return add(sql, Map.of());
        }

        public void execute() throws Exception {
            po.request(new EventEnvelope().setTo(PgService.ROUTE).setBody(transaction), timeoutMs).get();
        }
    }
}
