export async function transform(input, fetcher = fetch, env = process.env) {
  if (!env.OPENAI_API_KEY || !env.OPENAI_MODEL) throw new Error("service_unavailable");
  const instructions = input.action === "grammar"
    ? "Correct grammar, punctuation, and spelling. Preserve the meaning and language. Return only the corrected text."
    : input.action === "translate"
      ? `Translate to ${input.targetLanguage}. Preserve meaning and register. Return only the translation.`
      : input.action === "search"
        ? "Answer concisely using current information. State uncertainty rather than inventing facts."
      : `Rewrite in a ${input.tone} tone. Preserve meaning and factual claims. Return only the rewritten text.`;
  const response = await fetcher("https://api.openai.com/v1/responses", { method: "POST", headers: { Authorization: `Bearer ${env.OPENAI_API_KEY}`, "content-type": "application/json" }, body: JSON.stringify({ model: env.OPENAI_MODEL, store: false, instructions, input: input.text, max_output_tokens: 220 }) });
  if (!response.ok) throw new Error("provider_unavailable");
  const data = await response.json();
  const text = typeof data.output_text === "string" ? data.output_text.trim() : "";
  if (!text || text.length > 5000) throw new Error("invalid_provider_response");
  const usage = data.usage || {};
  const inputTokens = Number(usage.input_tokens) || 0;
  const outputTokens = Number(usage.output_tokens) || 0;
  const cost = inputTokens * (Number(env.LEO_INPUT_USD_PER_MILLION) || 0) / 1_000_000
    + outputTokens * (Number(env.LEO_OUTPUT_USD_PER_MILLION) || 0) / 1_000_000;
  return { text, cost: Number.isFinite(cost) ? cost : 0 };
}


