-- Campaign aggregate (docs/data-model.md §6). Enumerations are enforced with CHECK constraints so
-- that invalid states cannot be written even by code that bypasses the domain model.

create table campaigns (
    id              uuid          primary key,
    tenant_id       uuid          not null,
    name            varchar(120)  not null,
    objective       varchar(500)  not null,
    status          varchar(20)   not null
        constraint campaigns_status_chk check (status in
            ('DRAFT','PENDING_APPROVAL','APPROVED','SCHEDULED','ACTIVE','PAUSED','COMPLETED','ARCHIVED')),
    segment_id      uuid          not null,
    offer_id        uuid          not null,
    start_at        timestamptz   not null,
    end_at          timestamptz   not null,
    budget_amount   numeric(19,4) not null constraint campaigns_budget_chk check (budget_amount >= 0),
    currency        char(3)       not null,
    origin          varchar(16)   not null
        constraint campaigns_origin_chk check (origin in ('MANUAL','AI_ASSISTED')),
    ai_request_id   uuid,
    created_by      varchar(64)   not null,
    published_by    varchar(64),
    published_at    timestamptz,
    created_at      timestamptz   not null,
    updated_at      timestamptz   not null,
    version         bigint        not null,
    constraint campaigns_schedule_chk check (end_at > start_at),
    constraint campaigns_ai_origin_chk check (origin <> 'AI_ASSISTED' or ai_request_id is not null),
    constraint campaigns_published_chk check ((published_at is null) = (published_by is null)),
    constraint campaigns_tenant_name_uk unique (tenant_id, name)
);

-- Dashboards and the activation scheduler filter by tenant + status + start time.
create index campaigns_tenant_status_start_idx on campaigns (tenant_id, status, start_at);

create table campaign_channels (
    campaign_id uuid        not null references campaigns (id) on delete cascade,
    channel     varchar(16) not null
        constraint campaign_channels_channel_chk check (channel in ('EMAIL','PUSH','IN_APP','SMS')),
    primary key (campaign_id, channel)
);

create table content_variants (
    id                uuid          primary key,
    campaign_id       uuid          not null references campaigns (id) on delete cascade,
    channel           varchar(16)   not null
        constraint content_variants_channel_chk check (channel in ('EMAIL','PUSH','IN_APP','SMS')),
    variant_key       varchar(32)   not null,
    language          varchar(5)    not null,
    subject           varchar(150),
    body_template     varchar(5000) not null,
    status            varchar(20)   not null
        constraint content_variants_status_chk check (status in
            ('DRAFT','COMPLIANCE_PASSED','COMPLIANCE_FAILED','APPROVED')),
    generated_by      varchar(8)    not null
        constraint content_variants_author_chk check (generated_by in ('HUMAN','AI')),
    prompt_id         varchar(100),
    prompt_version    varchar(20),
    model_id          varchar(100),
    compliance_report text,
    constraint content_variants_provenance_chk check (
        (generated_by = 'AI') = (prompt_id is not null and prompt_version is not null and model_id is not null)),
    constraint content_variants_slot_uk unique (campaign_id, channel, variant_key, language)
);

create table campaign_approvals (
    id          uuid         primary key,
    campaign_id uuid         not null references campaigns (id) on delete cascade,
    decision    varchar(8)   not null
        constraint campaign_approvals_decision_chk check (decision in ('APPROVE','REJECT')),
    decided_by  varchar(64)  not null,
    comment     varchar(1000),
    decided_at  timestamptz  not null
);

create index campaign_approvals_campaign_idx on campaign_approvals (campaign_id, decided_at);

-- Four-eyes backstop: even a bug or a direct write cannot record an approval by the creator.
create function campaign_approvals_enforce_four_eyes() returns trigger
    language plpgsql
    set search_path from current
as $$
begin
    if new.decision = 'APPROVE' and new.decided_by =
            (select created_by from campaigns where id = new.campaign_id) then
        raise exception 'campaign % cannot be approved by its creator', new.campaign_id
            using errcode = 'check_violation';
    end if;
    return new;
end;
$$;

create trigger campaign_approvals_four_eyes
    before insert or update on campaign_approvals
    for each row execute function campaign_approvals_enforce_four_eyes();
