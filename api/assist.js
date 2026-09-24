import crypto from "node:crypto";
import { validPayload } from "../lib/security.js";
import { adminRpc, verifiedUser } from "../lib/supabase.js";
import { transform } from "../lib/ai.js";
export default async function handler(req, res) {
  const send = (result) => {
    if (!res) return result;
    for (const [name, value] of Object.entries(result.headers)) res.setHeader(name, value);
    return res.status(result.statusCode).send(result.body);
  };
  if (req.method !== "POST") return send(json(405, { error: "method_not_allowed" }, { allow: "POST" }));
  if (process.env.LEO_CLOUD_ENABLED !== "true") return send(json(503, { error: "cloud_processing_unavailable" }));
  let raw; try { raw = await bodyOf(req); } catch { return send(json(400, { error: "invalid_json" })); }
  const [input, error] = validPayload(raw); if (error) return send(json(400, { error }));
  let user;
  try { user = await verifiedUser(req.headers?.authorization); }
  catch (cause) { console.error("leo_assist_auth_unavailable", cause instanceof Error ? cause.message : "unknown"); return send(json(503, { error: "service_temporarily_unavailable" })); }
  if (!user) return send(json(401, { error: "sign_in_required" }));
  const requestId = crypto.randomUUID();
  const monthlyBudget = Number(process.env.LEO_MONTHLY_BUDGET_USD);
  if (!Number.isFinite(monthlyBudget) || monthlyBudget <= 0) return send(json(503, { error: "service_temporarily_unavailable" }));
  const isSearch = input.action === "search";
  let reservation;
  try {
    reservation = await adminRpc("lumen_reserve_leo_action", {
      p_request_id: requestId, p_user_id: user.id, p_action: input.action,
      p_monthly_budget_usd: monthlyBudget,
      p_daily_action_limit: Number(process.env.LEO_DAILY_ACTION_LIMIT) || 20,
      p_daily_search_limit: Number(process.env.LEO_DAILY_SEARCH_LIMIT) || 3,
      p_estimated_usd: Number(process.env.LEO_ESTIMATED_ACTION_USD) || 0.002,
      p_is_search: isSearch,
    });
  } catch (cause) { console.error("leo_assist_metering_unavailable", cause instanceof Error ? cause.message : "unknown"); return send(json(503, { error: "service_temporarily_unavailable" })); }
  const outcome = Array.isArray(reservation) ? reservation[0] : reservation;
  if (!outcome?.allowed) return send(json(outcome?.reason === "daily_limit_reached" ? 429 : 503, { error: outcome?.reason || "service_temporarily_unavailable" }));
  try {
    const result = await transform(input);
    await adminRpc("lumen_reconcile_leo_action", { p_request_id: requestId, p_actual_usd: result.cost });
    return send(json(200, { text: result.text }));
  } catch (cause) {
    console.error("leo_assist_provider_or_reconciliation_unavailable", cause instanceof Error ? cause.message : "unknown");
    try { await adminRpc("lumen_reconcile_leo_action", { p_request_id: requestId, p_actual_usd: 0 }); } catch {}
    return send(json(503, { error: "service_temporarily_unavailable" }));
  }
}


