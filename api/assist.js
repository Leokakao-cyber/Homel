import { json, bodyOf } from "../lib/http.js";
import { authorized, hash, validPayload } from "../lib/security.js";
import { allow } from "../lib/rate-limit.js";
import { transform } from "../lib/ai.js";
export default async function handler(req, res) {
  const send = (result) => res ? res.status(result.statusCode).set(result.headers).send(result.body) : result;
  if (req.method !== "POST") return send(json(405, { error: "method_not_allowed" }, { allow: "POST" }));
  if (!authorized(req.headers?.authorization)) return send(json(401, { error: "unauthorized" }));
  let raw; try { raw = await bodyOf(req); } catch { return send(json(400, { error: "invalid_json" })); }
  const [input, error] = validPayload(raw); if (error) return send(json(400, { error }));
  const token = /^Bearer (.+)$/.exec(req.headers.authorization)?.[1] || "";
  if (!(await allow(`lumen:assist:${hash(token).slice(0, 24)}`))) return send(json(503, { error: "service_temporarily_unavailable" }));
  try { return send(json(200, { text: await transform(input) })); }
  catch { return send(json(503, { error: "service_temporarily_unavailable" })); }
}

