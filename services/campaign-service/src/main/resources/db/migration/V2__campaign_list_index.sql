-- Keyset pagination for listings: newest first within a tenant (UUIDv7 ids are time-ordered).
create index campaigns_tenant_id_desc_idx on campaigns (tenant_id, id desc);
