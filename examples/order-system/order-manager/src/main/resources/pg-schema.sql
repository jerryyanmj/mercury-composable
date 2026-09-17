-- Required by ReactivePgConfig (framework health probe).
-- The business schema is owned by the seeded DDL (manager.sql / steward.sql)
-- and is deliberately NOT duplicated here.
CREATE TABLE IF NOT EXISTS health_check (
    id VARCHAR(40) PRIMARY KEY,
    app_name VARCHAR(100) NOT NULL,
    app_instance VARCHAR(256) NOT NULL,
    created TIMESTAMP NOT NULL,
    updated TIMESTAMP NOT NULL
);
