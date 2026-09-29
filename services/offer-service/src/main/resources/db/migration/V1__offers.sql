-- Offer catalogue (docs/data-model.md §5). Eligibility decisions and the budget ledger arrive
-- with the eligibility consumer in Phase 6.

create table offers (
    id                 uuid          primary key,
    tenant_id          uuid          not null,
    merchant_id        uuid          not null,
    title              varchar(120)  not null,
    description        varchar(1000) not null,
    category           varchar(16)   not null
        constraint offers_category_chk check (category in
            ('TRAVEL','DINING','GROCERY','FUEL','RETAIL','ENTERTAINMENT','ONLINE')),
    reward_type        varchar(20)   not null
        constraint offers_reward_type_chk check (reward_type in
            ('CASHBACK_PERCENT','CASHBACK_FIXED','DISCOUNT_PERCENT')),
    reward_percent     numeric(5,2),
    reward_fixed       numeric(19,4),
    max_reward         numeric(19,4) not null constraint offers_max_reward_chk check (max_reward > 0),
    min_spend          numeric(19,4) not null constraint offers_min_spend_chk check (min_spend >= 0),
    currency           char(3)       not null,
    per_customer_cap   integer       not null
        constraint offers_cap_chk check (per_customer_cap between 1 and 100),
    total_budget       numeric(19,4) not null constraint offers_budget_chk check (total_budget > 0),
    start_at           timestamptz   not null,
    end_at             timestamptz   not null,
    status             varchar(12)   not null
        constraint offers_status_chk check (status in ('DRAFT','ACTIVE','PAUSED','EXPIRED','EXHAUSTED')),
    terms_document_id  uuid,
    rule_set_version   integer       not null constraint offers_rule_set_chk check (rule_set_version >= 1),
    created_by         varchar(64)   not null,
    created_at         timestamptz   not null,
    updated_at         timestamptz   not null,
    version            bigint        not null,
    constraint offers_validity_chk check (end_at > start_at),
    constraint offers_reward_shape_chk check (
        (reward_type = 'CASHBACK_FIXED' and reward_fixed is not null and reward_percent is null)
        or (reward_type <> 'CASHBACK_FIXED' and reward_percent > 0 and reward_percent <= 50
            and reward_fixed is null)),
    constraint offers_tenant_title_uk unique (tenant_id, title)
);

-- Catalogue queries: active offers for a tenant and category (cached, see data-model.md §8).
create index offers_tenant_status_category_idx on offers (tenant_id, status, category);
create index offers_merchant_idx on offers (merchant_id);

create table offer_eligibility_rules (
    offer_id   uuid        not null references offers (id) on delete cascade,
    position   integer     not null,
    rule_type  varchar(32) not null
        constraint offer_rules_type_chk check (rule_type in
            ('SEGMENT_MEMBERSHIP','COUNTRY_IN','MIN_TRANSACTIONS_30D','CATEGORY_AFFINITY','CONSENT_REQUIRED')),
    params     jsonb       not null,
    primary key (offer_id, position)
);
