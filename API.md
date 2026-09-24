# Lumen Assist API

`POST /api/assist` accepts `{ "action": "grammar"|"rewrite"|"translate"|"search", "text": "...", "tone"?: "simple"|"professional"|"formal"|"casual", "targetLanguage"?: "French" }` and returns `{ "text": "..." }`.

It requires a Supabase access token in `Authorization: Bearer TOKEN`. The API verifies the session, stores only metering metadata (never submitted or generated text), and refuses requests when Cloud Processing is disabled, the daily allowance is exhausted, or the Alpha monthly allowance is exhausted.

Apply `supabase/lumen-leo.sql` in the Supabase SQL editor. Then configure these Vercel variables server-side only: `OPENAI_API_KEY`, `OPENAI_MODEL`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, `LEO_CLOUD_ENABLED`, `LEO_MONTHLY_BUDGET_USD`, `LEO_DAILY_ACTION_LIMIT`, `LEO_DAILY_SEARCH_LIMIT`, `LEO_ESTIMATED_ACTION_USD`, `LEO_INPUT_USD_PER_MILLION`, and `LEO_OUTPUT_USD_PER_MILLION`.

Keep `LEO_CLOUD_ENABLED=false` until the SQL migration and an authenticated staging request have succeeded. The endpoint fails closed when required configuration is missing. Do not place the OpenAI or Supabase service-role key in the Android APK, public JavaScript, or repository.


