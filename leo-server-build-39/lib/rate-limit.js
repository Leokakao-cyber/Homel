const script = "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n";
export async function allow(key, fetcher = fetch, env = process.env) {
  const url = env.UPSTASH_REDIS_REST_URL, token = env.UPSTASH_REDIS_REST_TOKEN;
  if (!url || !token) return false;
  try {
    const response = await fetcher(`${url.replace(/\/$/, "")}/eval/${encodeURIComponent(script)}/1/${encodeURIComponent(key)}/60`, { headers: { Authorization: `Bearer ${token}` } });
    if (!response.ok) return false;
    const data = await response.json(); return Number(data.result) <= 12;
  } catch { return false; }
}

