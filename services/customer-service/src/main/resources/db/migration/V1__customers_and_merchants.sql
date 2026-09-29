-- Customer system of record (docs/data-model.md §3). PII columns hold AES-256-GCM ciphertext
-- produced by the application; the database never sees plaintext PII.

create table customers (
    id                   uuid         primary key,
    tenant_id            uuid         not null,
    full_name_enc        bytea        not null,
    email_enc            bytea        not null,
    phone_enc            bytea,
    email_hmac           char(64)     not null,
    country_code         char(2)      not null,
    birth_year           smallint     not null constraint customers_birth_year_chk check (birth_year >= 1900),
    email_opt_in         boolean      not null,
    push_opt_in          boolean      not null,
    sms_opt_in           boolean      not null,
    preferred_categories varchar(16)[] not null,
    language             varchar(5)   not null,
    marketing_consent    boolean      not null,
    consent_updated_at   timestamptz,
    status               varchar(8)   not null
        constraint customers_status_chk check (status in ('ACTIVE','CLOSED')),
    created_at           timestamptz  not null,
    updated_at           timestamptz  not null,
    version              bigint       not null,
    constraint customers_consent_ts_chk check (not marketing_consent or consent_updated_at is not null),
    constraint customers_tenant_email_uk unique (tenant_id, email_hmac)
);

create index customers_tenant_status_idx on customers (tenant_id, status);

-- Row-level security as defense in depth for tenant isolation. FORCE applies it to the owning
-- service role too. The application sets app.tenant_id per transaction (SET LOCAL semantics);
-- without it no rows are visible.
alter table customers enable row level security;
alter table customers force row level security;
create policy customers_tenant_isolation on customers
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

-- Network-wide merchant reference data (not tenant-owned, no PII).
create table merchants (
    id           uuid         primary key,
    name         varchar(150) not null,
    mcc          char(4)      not null constraint merchants_mcc_chk check (mcc ~ '^[0-9]{4}$'),
    category     varchar(16)  not null,
    country_code char(2)      not null,
    city         varchar(100),
    active       boolean      not null,
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null,
    version      bigint       not null
);

create index merchants_category_idx on merchants (category) where active;
