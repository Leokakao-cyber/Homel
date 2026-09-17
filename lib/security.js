import crypto from "node:crypto";
export const hash = (value) => crypto.createHash("sha256").update(value).digest("hex");
export function authorized(header, hashes = process.env.LUMEN_ACCESS_TOKEN_HASHES || "") {
  const token = /^Bearer ([A-Za-z0-9_-]{32,256})$/.exec(header || "")?.[1];
  let allowed; try { allowed = JSON.parse(hashes); } catch { allowed = []; }
  if (!token || !Array.isArray(allowed)) return false;
  const supplied = Buffer.from(hash(token), "hex");
  return allowed.some((candidate) => {
    if (typeof candidate !== "string" || !/^[a-f0-9]{64}$/i.test(candidate)) return false;
    return crypto.timingSafeEqual(supplied, Buffer.from(candidate, "hex"));
  });
}
export function validPayload(input) {
  const clean = typeof input?.text === "string" ? input.text.trim() : "";
  const action = input?.action;
  if (!new Set(["grammar", "rewrite", "translate"]).has(action)) return [null, "unsupported_action"];
  if (!clean || clean.length > 1800) return [null, "text_must_be_1_to_1800_characters"];
  const tone = ["simple", "professional", "formal", "casual"].includes(input?.tone) ? input.tone : "professional";
  const targetLanguage = typeof input?.targetLanguage === "string" && /^[A-Za-z -]{2,32}$/.test(input.targetLanguage) ? input.targetLanguage : "English";
  return [{ action, text: clean, tone, targetLanguage }, null];
}

