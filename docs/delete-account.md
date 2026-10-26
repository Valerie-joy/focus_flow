# Deleting your FocusFlow account

**App:** FocusFlow (`com.scc.focusflow`)

This page explains how to have your FocusFlow account and its data deleted, what
is removed, and what is kept.

> `delete-account.html` in this folder is the same content as a standalone page,
> and is what the Play Console "Delete account URL" field points at. Change both
> together.

---

## How to request deletion

### From inside the app

1. Open FocusFlow and sign in.
2. Go to **Profile**.
3. Scroll to the bottom and tap **Delete account**.
4. Confirm when asked.

Your account and the data listed below are deleted immediately. You are signed
out and returned to the welcome screen.

If anything could not be deleted — for example the app had no internet
connection at the time — the app says so before returning you to the welcome
screen, and tells you to use the email route below. It never reports success
for a deletion that did not finish.

### By email

If you cannot sign in, email **bethanyivalerie@gmail.com** from the address your
account uses, with the subject **"Delete my FocusFlow account"**.

We reply within 7 days and complete the deletion within 30 days. We may ask you
to confirm from the registered email address before deleting anything, so that
nobody else can delete your account.

---

## What is deleted

Everything below is erased permanently and cannot be recovered.

| Data | Where it was stored |
|---|---|
| Your sign-in account (email address and password credentials) | Supabase Auth |
| Your profile: full name, email address, age, sex | Supabase database and on your device |
| Attention assessment results: per-category scores, the measurements behind them, session dates | On your device |
| Self-report questionnaire answers | On your device |
| Any diagnosis document you chose to upload | Supabase Storage |
| App settings: theme choice, notification preferences | On your device |

Uninstalling the app also removes everything stored on your device, but it does
**not** delete your account. Use one of the two methods above for that.

---

## What is kept, and for how long

| Data | Retention | Why |
|---|---|---|
| Server backups containing your records | Up to 30 days, then overwritten | Backups run on a fixed cycle and cannot be edited individually |
| Security and error logs that may include your account identifier | Up to 90 days | Needed to investigate abuse and faults |

Neither is used to rebuild your profile or your results after deletion.

---

## What was never collected

FocusFlow measures attention using your device's front camera. Camera frames are
analysed **on your device** and discarded immediately. No photo, video, facial
image or facial landmark is ever recorded, uploaded or stored — there is nothing
of that kind to delete, because none of it ever left your phone.

Only the derived numbers (for example "eyes on screen 82% of the clip") are
saved.

---

## Questions

**bethanyivalerie@gmail.com**
