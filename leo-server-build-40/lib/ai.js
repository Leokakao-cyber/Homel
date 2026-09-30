const instructionFor = (input) => {
  switch (input.action) {
    case "grammar": return "Correct grammar, punctuation, spelling, and clear agreement errors. Preserve the writer's meaning, language, names, and voice. Return only the corrected text.";
    case "shorten": return "Make the text shorter and clearer without losing important meaning or factual claims. Return only the revised text.";
    case "expand": return "Expand the text with useful clarity and natural detail without inventing facts. Return only the revised text.";
    case "summarize": return "Summarize the supplied text concisely and faithfully. Return only the summary.";
    case "explain": return "Explain the supplied text clearly in plain language. Do not invent missing context. Return only the explanation.";
    case "reply": return "Draft a concise, natural reply to the supplied message. Do not claim actions or facts the user did not provide. Return only the proposed reply.";
    case "translate": return `Translate to ${input.targetLanguage}. Preserve meaning, names, formatting, and register. Return only the translation.`;
    case "search": return input.text.startsWith("[DICTIONARY LOOKUP]")
      ? "Act as a concise multilingual dictionary. Auto-detect the word's language. Give the headword, language, pronunciation when reliable, part of speech, numbered definitions, one short usage example, and a Sources section with 2 or 3 authoritative source URLs. Never assume Nigerian English unless the word specifically has Nigerian usage. State uncertainty rather than inventing an entry."
      : "Answer concisely using current information. State uncertainty rather than inventing facts and include source URLs.";
    default: return `Rewrite in a ${input.tone} tone. Preserve meaning, names, and factual claims. Return only the rewritten text.`;
  }
};

export async function transform(input, fetcher = fetch, env = process.env) {
  if (!env.OPENAI_API_KEY || !env.OPENAI_MODEL) throw new Error("service_unavailable");
  const request = { model: env.OPENAI_MODEL, store: false, instructions: instructionFor(input), input: input.text, max_output_tokens: input.action === "search" ? 500 : input.action === "expand" ? 400 : 260 };
  if (input.action === "search") request.tools = [{ type: "web_search" }];
  const response = await fetcher("https://api.openai.com/v1/responses", { method: "POST", headers: { Authorization: `Bearer ${env.OPENAI_API_KEY}`, "content-type": "application/json" }, body: JSON.stringify(request) });
  if (!response.ok) throw new Error("provider_unavailable");
  const data = await response.json();
  const outputText = Array.isArray(data.output)
    ? data.output.flatMap((item) => Array.isArray(item?.content) ? item.content : [])
      .filter((part) => part?.type === "output_text")
      .map((part) => typeof part.text === "string" ? part.text : "")
      .join("")
      .trim()
    : "";
  const text = outputText || (typeof data.output_text === "string" ? data.output_text.trim() : "");
  if (!text || text.length > 5000) throw new Error("invalid_provider_response");
  const usage = data.usage || {};
  const inputTokens = Number(usage.input_tokens) || 0;
  const outputTokens = Number(usage.output_tokens) || 0;
  const cost = inputTokens * (Number(env.LEO_INPUT_USD_PER_MILLION) || 0) / 1_000_000
    + outputTokens * (Number(env.LEO_OUTPUT_USD_PER_MILLION) || 0) / 1_000_000;
  return { text, cost: Number.isFinite(cost) ? cost : 0 };
}

