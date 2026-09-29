-- Consumer-side idempotency: an event is applied at most once per consumer group, recorded in the
-- same transaction as the state change it causes.

create table processed_events (
    consumer_group varchar(128) not null,
    event_id       varchar(64)  not null,
    processed_at   timestamptz  not null default now(),
    primary key (consumer_group, event_id)
);

create index processed_events_age_idx on processed_events (processed_at);
