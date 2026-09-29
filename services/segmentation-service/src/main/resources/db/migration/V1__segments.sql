-- Segment definitions (docs/data-model.md §4). Membership and the feature projection are added
-- with the profile consumer in Phase 6.

create table segments (
    id                 uuid         primary key,
    tenant_id          uuid         not null,
    name               varchar(100) not null,
    description        varchar(500),
    definition         jsonb        not null,
    definition_version integer      not null constraint segments_definition_version_chk check (definition_version >= 1),
    status             varchar(8)   not null
        constraint segments_status_chk check (status in ('DRAFT','ACTIVE','RETIRED')),
    origin             varchar(12)  not null
        constraint segments_origin_chk check (origin in ('MANUAL','AI_PROPOSED')),
    created_by         varchar(64)  not null,
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null,
    version            bigint       not null,
    constraint segments_tenant_name_uk unique (tenant_id, name)
);

-- The profile consumer evaluates every ACTIVE segment of a tenant for each profile update.
create index segments_tenant_active_idx on segments (tenant_id) where status = 'ACTIVE';
