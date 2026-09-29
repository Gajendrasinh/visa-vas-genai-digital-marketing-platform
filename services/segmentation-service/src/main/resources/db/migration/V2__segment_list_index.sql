-- Keyset pagination for listings: newest first within a tenant (UUIDv7 ids are time-ordered).
create index segments_tenant_id_desc_idx on segments (tenant_id, id desc);
