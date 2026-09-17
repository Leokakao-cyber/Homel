import { json } from "../lib/http.js";
export default function handler(req, res) { const response = json(200, { status: process.env.OPENAI_API_KEY && process.env.OPENAI_MODEL && process.env.UPSTASH_REDIS_REST_URL ? "configured" : "setup_required" }); return res ? res.status(response.statusCode).set(response.headers).send(response.body) : response; }

