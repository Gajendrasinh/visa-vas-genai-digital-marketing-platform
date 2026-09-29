-- Idempotency records (docs/api.md §1). Platform tables have their own Flyway history table
-- (platform_schema_history), so their versions never collide with a service's own migrations.

create table idempotency_keys (
    principal       varchar(64)  not null,
    idem_key        varchar(128) not null,
    request_hash    char(64)     not null,
    status          varchar(12)  not null
        constraint idempotency_keys_status_chk check (status in ('IN_PROGRESS','COMPLETED')),
    response_status integer,
    content_type    varchar(100),
    location        varchar(500),
    response_body   bytea,
    created_at      timestamptz  not null default now(),
    expires_at      timestamptz  not null,
    primary key (principal, idem_key)
);

create index idempotency_keys_expiry_idx on idempotency_keys (expires_at);
