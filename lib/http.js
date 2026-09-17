export function json(status, body, headers = {}) {
  return { statusCode: status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...headers }, body: JSON.stringify(body) };
}
export async function bodyOf(req) {
  if (typeof req.body === "object" && req.body !== null) return req.body;
  if (typeof req.body !== "string") return {};
  try { return JSON.parse(req.body); } catch { throw new Error("invalid_json"); }
}

