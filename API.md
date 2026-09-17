# Lumen Assist API

`POST /api/assist` accepts `{ "action": "grammar"|"rewrite"|"translate", "text": "...", "tone"?: "simple"|"professional"|"formal"|"casual", "targetLanguage"?: "French" }` and returns `{ "text": "..." }`.

It accepts a private-beta device token as `Authorization: Bearer TOKEN`. Run `node scripts/create-token.mjs` locally, share its token through a secure channel, and put its hash in the Vercel `LUMEN_ACCESS_TOKEN_HASHES` JSON array. Never place an OpenAI key or this token in the Android app source or website.

Set these Vercel environment variables before enabling Smart, Rewrite, or Translate:

- `OPENAI_API_KEY` and explicit `OPENAI_MODEL`
- `UPSTASH_REDIS_REST_URL` and `UPSTASH_REDIS_REST_TOKEN`
- `LUMEN_ACCESS_TOKEN_HASHES`, for example `["64-hex-character-sha256-hash"]`

The endpoint fails closed when any service is not configured. It allows 12 requests per minute for each beta token and does not write submitted text to application logs. This is a private-beta enrollment mechanism, not a complete public account system. Before a public launch, add real user authentication, an approval flow, monitoring, a complete privacy policy, terms, abuse handling, and a data-retention review.

