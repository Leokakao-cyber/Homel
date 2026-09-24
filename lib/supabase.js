const required = (env, name) => {
  const value = env[name];
  if (typeof value !== "string" || !value.trim()) throw new Error(`missing_${name.toLowerCase()}`);
  return value;
};

export async function verifiedUser(authorization, fetcher = fetch, env = process.env) {
  const token = /^Bearer\s+(.+)$/.exec(authorization || "")?.[1];
  if (!token) return null;
  const url = required(env, "SUPABASE_URL").replace(/\/$/, "");
  const key = required(env, "SUPABASE_PUBLISHABLE_KEY");
  const response = await fetcher(`${url}/auth/v1/user`, {
    headers: { apikey: key, authorization: `Bearer ${token}` },
  });
  if (!response.ok) return null;
  const user = await response.json();
  return typeof user?.id === "string" ? { id: user.id } : null;
}

export async function adminRpc(name, body, fetcher = fetch, env = process.env) {
  const url = required(env, "SUPABASE_URL").replace(/\/$/, "");
  const key = required(env, "SUPABASE_SERVICE_ROLE_KEY");
  const response = await fetcher(`${url}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: { apikey: key, authorization: `Bearer ${key}`, "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) throw new Error("supabase_rpc_unavailable");
  if (response.status === 204) return null;
  const payload = await response.text();
  return payload ? JSON.parse(payload) : null;
}

