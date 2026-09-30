const configEndpoint = "/api/auth-config";
let client;

function accountMarkup() {
  const button = document.createElement("button");
  button.className = "account-trigger";
  button.type = "button";
  button.hidden = true;
  button.textContent = "Sign in";
  button.setAttribute("aria-haspopup", "dialog");

  const modal = document.createElement("dialog");
  modal.className = "account-dialog";
  modal.innerHTML = `<form method="dialog" class="account-card">
    <button class="account-close" value="close" aria-label="Close account window">×</button>
    <p class="mono">KAKAO ACCOUNT</p>
    <div data-account-signed-out>
      <h2>Your KAKAO workspace.</h2>
      <p>Sign in with Google to create and manage your account. We request your name, email and profile picture. KAKAO does not receive your Google password.</p>
      <button class="google-sign-in" type="button" data-google-sign-in><span>G</span> Continue with Google</button>
    </div>
    <div data-account-signed-in hidden>
      <div class="account-person"><img data-account-avatar alt=""><div><strong data-account-name></strong><span data-account-email></span></div></div>
      <label class="consent-check"><input type="checkbox" data-profile-consent> Save my name, email and profile picture to my KAKAO profile. I can delete this profile later.</label>
      <p class="account-status" data-account-status></p>
      <div class="account-actions"><button type="button" data-save-profile>Save profile</button><button type="button" data-sign-out>Sign out</button><button type="button" class="account-delete" data-delete-profile>Delete saved profile</button></div>
    </div>
  </form>`;
  document.body.append(modal);
  document.querySelector(".site-menu")?.append(button);
  button.addEventListener("click", () => modal.showModal());
  modal.addEventListener("click", event => { if (event.target === modal) modal.close(); });
  return { button, modal };
}

async function loadClient(url, key) {
  const { createClient } = await import("https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2/+esm");
  return createClient(url, key, { auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true } });
}

function renderSession(ui, session) {
  const user = session?.user;
  ui.button.hidden = false;
  ui.button.textContent = user ? (user.user_metadata?.full_name?.split(" ")[0] || "Account") : "Sign in";
  ui.modal.querySelector("[data-account-signed-out]").hidden = Boolean(user);
  ui.modal.querySelector("[data-account-signed-in]").hidden = !user;
  if (!user) return;
  ui.modal.querySelector("[data-account-name]").textContent = user.user_metadata?.full_name || "KAKAO member";
  ui.modal.querySelector("[data-account-email]").textContent = user.email || "";
  const avatar = ui.modal.querySelector("[data-account-avatar]");
  avatar.src = user.user_metadata?.avatar_url || "/favicon.svg";
  avatar.alt = `${user.user_metadata?.full_name || "Member"} profile picture`;
}

export async function initAuth() {
  const ui = accountMarkup();
  let config;
  try {
    const response = await fetch(configEndpoint, { headers: { Accept: "application/json" } });
    config = await response.json();
  } catch { return; }
  if (!config.enabled) return;
  client = await loadClient(config.url, config.publishableKey);
  const { data: { session } } = await client.auth.getSession();
  renderSession(ui, session);
  client.auth.onAuthStateChange((_event, nextSession) => renderSession(ui, nextSession));

  ui.modal.querySelector("[data-google-sign-in]").addEventListener("click", async () => {
    await client.auth.signInWithOAuth({ provider: "google", options: { redirectTo: `${location.origin}/?account=welcome` } });
  });
  ui.modal.querySelector("[data-sign-out]").addEventListener("click", async () => { await client.auth.signOut(); ui.modal.close(); });
  ui.modal.querySelector("[data-save-profile]").addEventListener("click", async () => {
    const consent = ui.modal.querySelector("[data-profile-consent]");
    const status = ui.modal.querySelector("[data-account-status]");
    if (!consent.checked) { status.textContent = "Please confirm consent before saving your KAKAO profile."; return; }
    const { data: { user } } = await client.auth.getUser();
    if (!user) return;
    const { error } = await client.from("profiles").upsert({ id: user.id, email: user.email, full_name: user.user_metadata?.full_name || null, avatar_url: user.user_metadata?.avatar_url || null, consented_at: new Date().toISOString() });
    status.textContent = error ? "We could not save the profile yet. Please try again." : "Your KAKAO profile is saved.";
  });
  ui.modal.querySelector("[data-delete-profile]").addEventListener("click", async () => {
    const status = ui.modal.querySelector("[data-account-status]");
    const { data: { user } } = await client.auth.getUser();
    if (!user) return;
    const { error } = await client.from("profiles").delete().eq("id", user.id);
    status.textContent = error ? "We could not delete the saved profile yet." : "Your saved KAKAO profile has been deleted. Your sign-in account remains active.";
  });
  if (new URLSearchParams(location.search).get("account") === "welcome" && session) ui.modal.showModal();
}
