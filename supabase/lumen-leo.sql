-- LEO usage records contain no request text, response text, clipboard data, or typing history.
create table if not exists public.lumen_leo_usage (
  request_id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  action text not null check (action in ('grammar', 'rewrite', 'translate', 'search')),
  is_search boolean not null default false,
  requested_at timestamptz not null default now(),
  reserved_usd numeric(12,6) not null default 0,
  actual_usd numeric(12,6),
  status text not null check (status in ('reserved', 'reconciled')) default 'reserved'
);
alter table public.lumen_leo_usage enable row level security;
create index if not exists lumen_leo_usage_user_day on public.lumen_leo_usage(user_id, requested_at desc);
create index if not exists lumen_leo_usage_month on public.lumen_leo_usage(requested_at desc);

create or replace function public.lumen_reserve_leo_action(
  p_request_id uuid, p_user_id uuid, p_action text, p_monthly_budget_usd numeric,
  p_daily_action_limit integer, p_daily_search_limit integer, p_estimated_usd numeric, p_is_search boolean
) returns table(allowed boolean, reason text)
language plpgsql security definer set search_path = public as $$
declare daily_count integer; month_cost numeric;
begin
  if p_estimated_usd < 0 or p_monthly_budget_usd <= 0 then return query select false, 'service_temporarily_unavailable'; return; end if;
  select count(*) into daily_count from lumen_leo_usage where user_id = p_user_id and requested_at >= date_trunc('day', now()) and (not p_is_search or is_search);
  if daily_count >= case when p_is_search then p_daily_search_limit else p_daily_action_limit end then return query select false, 'daily_limit_reached'; return; end if;
  select coalesce(sum(coalesce(actual_usd, reserved_usd)), 0) into month_cost from lumen_leo_usage where requested_at >= date_trunc('month', now());
  if month_cost + p_estimated_usd > p_monthly_budget_usd then return query select false, 'monthly_budget_reached'; return; end if;
  insert into lumen_leo_usage(request_id,user_id,action,is_search,reserved_usd) values (p_request_id,p_user_id,p_action,p_is_search,p_estimated_usd);
  return query select true, 'reserved';
end $$;

create or replace function public.lumen_reconcile_leo_action(p_request_id uuid, p_actual_usd numeric)
returns void language plpgsql security definer set search_path = public as $$
begin update lumen_leo_usage set actual_usd = greatest(p_actual_usd, 0), status = 'reconciled' where request_id = p_request_id; end $$;

revoke all on function public.lumen_reserve_leo_action(uuid,uuid,text,numeric,integer,integer,numeric,boolean) from public, anon, authenticated;
revoke all on function public.lumen_reconcile_leo_action(uuid,numeric) from public, anon, authenticated;
grant execute on function public.lumen_reserve_leo_action(uuid,uuid,text,numeric,integer,integer,numeric,boolean) to service_role;
grant execute on function public.lumen_reconcile_leo_action(uuid,numeric) to service_role;


