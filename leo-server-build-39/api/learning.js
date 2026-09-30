import { json, bodyOf } from "../lib/http.js";
import { verifiedUser } from "../lib/supabase.js";

const EVENTS = new Set(["suggestion_accepted", "correction_undone", "glide_top1", "voice_error", "handwriting_candidate"]);
const LANGUAGES = new Set(["en", "fr", "es", "other"]);

export default async function handler(req, res) {
  const send = (result) => { for (const [name, value] of Object.entries(result.headers)) res.setHeader(name, value); return res.status(result.statusCode).send(result.body); };
  if (req.method !== "POST") return send(json(405, { error: "method_not_allowed" }, { allow: "POST" }));
  if (process.env.LUMEN_AGGREGATE_LEARNING_ENABLED !== "true") return send(json(503, { error: "pilot_disabled" }));
  const user = await verifiedUser(req.headers?.authorization).catch(() => null);
  if (!user) return send(json(401, { error: "sign_in_required" }));
  let body; try { body = await bodyOf(req); } catch { return send(json(400, { error: "invalid_json" })); }
  if (body?.schema !== 1 || !Array.isArray(body?.events) || body.events.length > 20) return send(json(400, { error: "invalid_payload" }));
  const events = body.events.filter((item) => EVENTS.has(item?.event) && LANGUAGES.has(item?.language) && Number.isInteger(item?.count) && item.count > 0 && item.count <= 10000);
  if (events.length !== body.events.length) return send(json(400, { error: "invalid_event" }));
  const url = process.env.SUPABASE_URL?.replace(/\/$/, ""); const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!url || !key) return send(json(503, { error: "service_unavailable" }));
  const response = await fetch(`${url}/rest/v1/lumen_learning_aggregate`, {
    method: "POST", headers: { apikey: key, authorization: `Bearer ${key}`, "content-type": "application/json", prefer: "resolution=merge-duplicates" },
    body: JSON.stringify(events.map((item) => ({ bucket_date: new Date().toISOString().slice(0, 10), event_name: item.event, language: item.language, noisy_count: item.count }))),
  });
  if (!response.ok) return send(json(503, { error: "storage_unavailable" }));
  return send(json(200, { accepted: events.length }));
}
