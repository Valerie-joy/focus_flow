-- FocusFlow — account deletion backend
--
-- Run this ONCE in the Supabase SQL editor (Dashboard → SQL Editor → New
-- query → paste → Run). Until it exists, the in-app "Delete account" button
-- clears the device and signs the user out, but reports that the sign-in
-- account itself could not be removed.
--
-- WHY THIS IS A DATABASE FUNCTION
--
-- The app ships with only the publishable (anon) key. Deleting a row from
-- `auth.users` needs the service-role key, which can never be put inside an
-- APK — anyone could extract it and delete any account. So the privilege
-- stays on the server: this function runs as its owner (SECURITY DEFINER) but
-- deletes `auth.uid()` and nothing else, so the most a caller can ever do is
-- delete their own account. There is no parameter to tamper with.

create or replace function public.delete_current_user()
returns void
language plpgsql
security definer
-- Pinned search_path. Without it, a SECURITY DEFINER function resolves
-- unqualified names against the caller's search_path, which is the classic
-- way these functions get hijacked.
set search_path = public, auth, pg_temp
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'Not signed in';
  end if;

  -- Explicit, even though `profiles.id` should cascade from auth.users: if the
  -- foreign key was created without ON DELETE CASCADE the profile row would
  -- silently outlive the account, and a row with no owner can never be
  -- reached again by anyone.
  delete from public.profiles where id = uid;

  delete from auth.users where id = uid;
end;
$$;

-- Only a signed-in caller may invoke it; `anon` is deliberately not granted.
revoke all on function public.delete_current_user() from public, anon;
grant execute on function public.delete_current_user() to authenticated;


-- STORAGE POLICY
--
-- The app deletes the user's uploaded diagnosis document itself, before
-- calling the function above (once the auth user is gone the client can no
-- longer prove ownership). That delete needs a policy allowing a user to
-- remove their own objects — the upload path is "<user id>/<file name>", so
-- the first path segment is the owner's UUID.
--
-- Skip this block if an equivalent policy already exists.

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'storage'
      and tablename = 'objects'
      and policyname = 'Users can delete their own adhd documents'
  ) then
    create policy "Users can delete their own adhd documents"
      on storage.objects for delete
      to authenticated
      using (
        bucket_id = 'adhd-documents'
        and (storage.foldername(name))[1] = auth.uid()::text
      );
  end if;
end $$;


-- VERIFYING
--
-- As a signed-in user (not the SQL editor, which runs as the service role and
-- so has no auth.uid()):
--
--   select public.delete_current_user();
--
-- In the SQL editor it will raise 'Not signed in', which is the correct
-- response and confirms the function is installed and reachable. To test it
-- end to end, use the app's Profile → Delete account with a throwaway
-- account, then confirm the account is gone from Dashboard → Authentication →
-- Users and that no row remains in `profiles`.
