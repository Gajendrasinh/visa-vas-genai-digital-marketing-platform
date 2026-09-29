-- Transactional outbox (docs/kafka.md §3). Rows are written in the same transaction as the state
-- change and published in (created_at, id) order by a single leader per service.

create table outbox_events (
    id             uuid         primary key,          -- the event's eventId
    topic          varchar(128) not null,
    message_key    varchar(128) not null,
    event_type     varchar(128) not null,
    payload_class  varchar(200) not null,              -- Avro SpecificRecord class
    payload        bytea        not null,              -- Avro binary (no registry needed at write time)
    correlation_id varchar(64),
    created_at     timestamptz  not null default clock_timestamp(),
    published_at   timestamptz,
    attempts       integer      not null default 0,
    last_error     varchar(1000)
);

create index outbox_events_unpublished_idx on outbox_events (created_at, id) where published_at is null;
create index outbox_events_published_idx on outbox_events (published_at) where published_at is not null;
