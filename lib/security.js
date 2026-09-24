export function validPayload(input) {
  const clean = typeof input?.text === "string" ? input.text.trim() : "";
  const action = input?.action;
  if (!new Set(["grammar", "rewrite", "translate", "search"]).has(action)) return [null, "unsupported_action"];
  if (!clean || clean.length > 5000) return [null, "text_must_be_1_to_5000_characters"];
  const tone = ["simple", "professional", "formal", "casual"].includes(input?.tone) ? input.tone : "professional";
  const targetLanguage = typeof input?.targetLanguage === "string" && /^[A-Za-z -]{2,32}$/.test(input.targetLanguage) ? input.targetLanguage : "English";
  return [{ action, text: clean, tone, targetLanguage }, null];
}


