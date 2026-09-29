-- Keyset pagination for listings: newest first within a tenant (UUIDv7 ids are time-ordered).
create index offers_tenant_id_desc_idx on offers (tenant_id, id desc);
