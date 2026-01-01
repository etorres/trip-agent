--! flyway:transactional=false
CREATE INDEX CONCURRENTLY state_tag_idx on public.durable_state (tag);
CREATE INDEX CONCURRENTLY state_global_offset_idx on public.durable_state (global_offset);