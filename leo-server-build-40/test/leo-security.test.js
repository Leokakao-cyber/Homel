import test from "node:test";
import assert from "node:assert/strict";
import { validPayload } from "../lib/security.js";
import { verifiedUser } from "../lib/supabase.js";
import { transform } from "../lib/ai.js";

test("all LEO 1.2.2.0 writing actions are accepted", () => {
  for (const action of ["grammar", "rewrite", "shorten", "expand", "summarize", "explain", "reply", "translate", "search"]) {
    const [payload, error] = validPayload({ action, text: "Test text" });
    assert.equal(error, null);
    assert.equal(payload.action, action);
  }
});

test("unsupported actions and oversized input fail closed", () => {
  assert.equal(validPayload({ action: "auto_upload", text: "private" })[1], "unsupported_action");
  assert.equal(validPayload({ action: "grammar", text: "x".repeat(5001) })[1], "text_must_be_1_to_5000_characters");
});

test("Supabase verification forwards the bearer token and project key", async () => {
  let request;
  const fetcher = async (url, options) => {
    request = { url, options };
    return { ok: true, json: async () => ({ id: "user-123" }) };
  };
  const user = await verifiedUser("Bearer access-token", fetcher, { SUPABASE_URL: "https://example.supabase.co", SUPABASE_PUBLISHABLE_KEY: "publishable" });
  assert.deepEqual(user, { id: "user-123" });
  assert.equal(request.options.headers.authorization, "Bearer access-token");
  assert.equal(request.options.headers.apikey, "publishable");
});

test("invalid and missing bearer tokens are rejected", async () => {
  assert.equal(await verifiedUser(undefined, async () => { throw new Error("must not fetch"); }, {}), null);
  const rejected = await verifiedUser("Bearer bad", async () => ({ ok: false }), { SUPABASE_URL: "https://example.supabase.co", SUPABASE_PUBLISHABLE_KEY: "publishable" });
  assert.equal(rejected, null);
});

test("raw Responses API output text is extracted", async () => {
  const fetcher = async () => ({
    ok: true,
    json: async () => ({
      output: [{ type: "message", content: [{ type: "output_text", text: "Corrected text" }] }],
      usage: { input_tokens: 10, output_tokens: 3 },
    }),
  });
  const result = await transform(
    { action: "grammar", text: "bad text", tone: "professional", targetLanguage: "English" },
    fetcher,
    { OPENAI_API_KEY: "test", OPENAI_MODEL: "gpt-5.6-sol" },
  );
  assert.equal(result.text, "Corrected text");
});

test("empty provider output fails closed", async () => {
  const fetcher = async () => ({ ok: true, json: async () => ({ output: [] }) });
  await assert.rejects(
    transform({ action: "grammar", text: "bad text" }, fetcher, { OPENAI_API_KEY: "test", OPENAI_MODEL: "gpt-5.6-sol" }),
    /invalid_provider_response/,
  );
});
