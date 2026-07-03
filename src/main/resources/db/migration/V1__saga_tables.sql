CREATE TABLE saga
(
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    payload    TEXT,
    status     VARCHAR(32)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ,
    version    BIGINT       NOT NULL
);

CREATE TABLE saga_step
(
    id            UUID PRIMARY KEY,
    saga_id       UUID         NOT NULL REFERENCES saga (id),
    sequence      INT          NOT NULL,
    name          VARCHAR(255) NOT NULL,
    compensation  VARCHAR(255) NOT NULL,
    status        VARCHAR(32)  NOT NULL,
    attempts      INT          NOT NULL,
    dispatched_at TIMESTAMPTZ,
    UNIQUE (saga_id, sequence)
);

CREATE INDEX idx_saga_step_saga_id ON saga_step (saga_id);
