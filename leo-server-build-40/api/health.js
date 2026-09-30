import { json } from "../lib/http.js";
export default function handler(req, res) {
  const required = ["OPENAI_API_KEY", "OPENAI_MODEL", "SUPABASE_URL", "SUPABASE_PUBLISHABLE_KEY", "SUPABASE_SERVICE_ROLE_KEY", "LEO_MONTHLY_BUDGET_USD"];
  const response = json(200, { status: required.every((name) => process.env[name]) ? "configured" : "setup_required" });
  if (!res) return response;
  for (const [name, value] of Object.entries(response.headers)) res.setHeader(name, value);
  return res.status(response.statusCode).send(response.body);
}

