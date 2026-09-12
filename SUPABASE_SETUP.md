# Supabase / Google sign-in setup

## Already done for you

`local.properties` has `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` filled in
and wired into `BuildConfig` (see `app/build.gradle.kts`). Email/password
sign-up, sign-in, password reset, and sign-out all work as soon as the
project syncs — nothing else to configure for those.

**Never put the Supabase *secret* key (`sb_secret_...`) anywhere in this
app.** It has full service-role access and bypasses Row Level Security; if
it ends up in a built APK, anyone can decompile the app and extract it. Only
`SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` (the anon/public key) belong
in `local.properties`/`BuildConfig`. Since the secret key was shared in a
chat session during development, rotate it in the Supabase Dashboard
(Settings → API → reset `service_role` key) once setup is complete.

## Google sign-in — requires your own Google Cloud setup

This app uses Android's Credential Manager for *native* Google sign-in (no
browser redirect), which needs two OAuth client IDs from Google Cloud
Console. Both are one-time console setup only you can do:

1. **Google Cloud Console** → [APIs & Services → Credentials](https://console.cloud.google.com/apis/credentials)
   → *Create Credentials* → *OAuth client ID* → type **Web application**.
   - No redirect URI is needed for this flow.
   - Copy the generated **Client ID** — this is your "Web Client ID".

2. Create a **second** OAuth client ID → type **Android**.
   - Package name: `com.focusflow`
   - SHA-1 certificate fingerprint: run `./gradlew signingReport` from the
     project root and copy the SHA-1 for the `debug` variant (for release,
     redo this step with your release keystore's SHA-1 before publishing).
   - This one doesn't need to be copied anywhere in the app — Google Cloud
     just needs it registered so your app is allowed to use the flow.

3. **Supabase Dashboard** → Authentication → Providers → **Google** →
   enable it → paste the **Web** Client ID (from step 1) into "Authorized
   Client IDs".

4. In `local.properties`, replace:
   ```
   GOOGLE_WEB_CLIENT_ID=REPLACE_ME_GOOGLE_WEB_CLIENT_ID
   ```
   with the real Web Client ID from step 1, then rebuild.

Until step 4 is done, tapping "Continue with Google" shows a clear
"Google sign-in isn't configured yet" message instead of failing silently
or crashing.

## ADHD document storage

The `adhd-documents` Storage bucket was created directly against your
project during implementation (private, not public — confirmed via the
Storage API). Files are uploaded to `{user_id}/{filename}`, one folder per
account.

**One manual step left:** Row Level Security policies can only be created
via SQL, which needs your database password (not something the app's
anon/service keys grant access to) — so this part wasn't automated. Run
this once in **Supabase Dashboard → SQL Editor**:

```sql
create policy "Users can upload their own ADHD documents"
on storage.objects for insert
to authenticated
with check (
  bucket_id = 'adhd-documents'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "Users can read their own ADHD documents"
on storage.objects for select
to authenticated
using (
  bucket_id = 'adhd-documents'
  and (storage.foldername(name))[1] = auth.uid()::text
);
```

Until this runs, uploads will fail with a permissions error (RLS denies by
default) — `AdhdDocumentRepository`'s `UploadState.Invalid` will surface
that as "Couldn't verify document" rather than crashing.
