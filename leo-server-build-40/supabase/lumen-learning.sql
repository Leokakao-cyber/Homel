-- Aggregates contain no user id, typed text, transcript, clipboard data, strokes, or app identity.
create table if not exists public.lumen_learning_aggregate (
  bucket_date date not null,
  event_name text not null check (event_name in ('suggestion_accepted','correction_undone','glide_top1','voice_error','handwriting_candidate')),
  language text not null check (language in ('en','fr','es','other')),
  noisy_count bigint not null check (noisy_count > 0),
  received_at timestamptz not null default now()
);
alter table public.lumen_learning_aggregate enable row level security;
revoke all on public.lumen_learning_aggregate from anon, authenticated;
create index if not exists lumen_learning_bucket on public.lumen_learning_aggregate(bucket_date desc, event_name, language);
