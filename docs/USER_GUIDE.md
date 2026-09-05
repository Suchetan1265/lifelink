# LifeLink — User Guide

Hospitals post what they need. Nearby donors with a compatible blood group hear
about it in seconds. Anything nobody confirms in time goes to the blood banks.

This guide covers what each role can do and the order things happen in. For
architecture and local setup, see the [README](../README.md).

---

## The four roles

Every account is one role, chosen at registration. What you see after signing in
is determined entirely by that role.

| Role | What it does |
|---|---|
| **Donor** | Manages availability, finds requests they can help with, responds, donates. Usable the moment you register. |
| **Hospital** | Raises requests, picks a donor from those who accept, records the donation. Needs approval first. |
| **Blood bank** | Keeps stock current and covers requests no donor confirmed in time. Needs approval first. |
| **Administrator** | Approves or rejects hospital and blood bank registrations, watches platform health. |

**Donors are trusted, organisations are not.** A donor can use LifeLink
immediately. A hospital or blood bank is held at `PENDING` until an administrator
has checked it, because anyone claiming to be a hospital could otherwise summon
donors to an address.

---

## Life of a request

Everything revolves around one object: a request for blood. It moves through a
fixed set of states, and the buttons you see depend on which state it is in.

```
RAISED ──donor accepts──> MATCHED ──hospital confirms──> CONFIRMED ──donation recorded──> FULFILLED
   │         │                                              ▲
   └─────────┴──deadline passes──> ESCALATED ──bank accepts──┘
   │         │                          │
   └─────────┴──needed-by passes────────┴──> EXPIRED

anything not yet fulfilled ──hospital cancels──> CANCELLED
```

Every move is recorded with who did it, when, and why. Hospitals see the full
history as a timeline on the request page.

The states are not decorative. A hospital cannot confirm a donor before that
donor has accepted, and cannot record a donation before confirming someone. **If
a button you expect is missing, the request is almost always in a state that does
not allow it yet.**

---

## Donors

You control when you are reachable, and you can either wait to be asked or go
looking for somewhere to give.

### Setting yourself up

1. **Register.** Give your blood group, city, location, and how far you are
   willing to travel. The travel radius matters — you will only ever be shown
   requests inside it.
2. **Turn availability on.** New accounts start unavailable, so nothing reaches
   you until you flip the switch on your dashboard. Turn it off any time you
   cannot donate and you drop out of matching until you turn it back on.

### Two ways to find a request

**You get asked.** When a hospital raises a request, LifeLink finds the nearest
available donors with a compatible group and notifies them. Those appear on your
dashboard as cards you can accept or decline.

**You go looking.** The *Where I can donate* screen lists every open request your
blood group can serve within your travel radius, most urgent first, whether or
not anyone picked you. Choosing one there counts as accepting it.

> **You hold one commitment at a time.** Once you accept, you stay committed
> until that request is fulfilled, cancelled, or you decline. You can give blood
> once per visit, and two hospitals must never both be counting on you.

### From accepting to donating

1. **Accept.** You are telling the hospital you are willing to come. It does not
   commit you to a time yet, and other donors may have accepted too.
2. **Wait to be confirmed.** The hospital picks who it needs. If they pick you,
   you get the address and the needed-by time. If they pick someone else, you are
   told the request is covered and you are free again.
3. **Donate.** Go and give blood. The hospital records it afterwards — you do not
   need to do anything in the app.
4. **Rest for 90 days.** Recording the donation starts your cooldown. Your
   dashboard counts down, and you are held out of matching until it ends.

### Your other screens

**Eligibility** shows whether you can donate today and, if not, the date you can.
**History** lists everything you have given. **Notifications** collects
everything LifeLink has told you.

---

## Hospitals

You describe what you need and how urgently. LifeLink finds donors; you decide
who actually comes.

### Before you can do anything

Registration asks for your name, licence number, address and location. The
account is created immediately but sits at `PENDING`, and any attempt to raise a
request is refused until an administrator approves you. You get a notification
either way; a rejection includes the reason.

### Running a request

1. **Raise it.** Choose the blood group, units, and when it is needed by. Urgency
   decides how long LifeLink waits for a donor before falling back to blood
   banks. Donors are notified the moment you submit.
2. **Watch the responses.** The request page lists every donor contacted:
   `NOTIFIED` (no reply yet), `ACCEPTED` (willing to come), `DECLINED`. The page
   refreshes itself, and a map shows where the donors are relative to you.
3. **Confirm a donor.** A **Confirm** button appears next to anyone who accepted.
   Pressing it commits that person, sends them your address and the needed-by
   time, and tells the other accepting donors that the request is covered.
4. **Record the donation.** Once someone is confirmed, a **Record the donation**
   panel appears. Entering the units closes the request as `FULFILLED`, writes it
   to the donor's history, and starts their 90-day cooldown.

> **The two steps cannot be skipped.** Confirming is what tells one specific
> person to travel; recording is what says blood actually changed hands. If the
> confirm button is missing, nobody has accepted yet. If the record panel is
> missing, you have not confirmed anyone.

### Cancelling, and what happens if nobody comes

You can cancel any time before the donation is recorded. A reason is required and
kept in the timeline; donors who accepted are told not to come.

If no donor is confirmed before the urgency deadline, LifeLink escalates to blood
banks near you automatically — you need do nothing, and you are told when a bank
takes it on. If the needed-by time passes with nothing fulfilled, the request
expires.

> **Ten requests per hour.** Beyond that, further requests are refused for the
> rest of the hour. It exists to stop a misconfigured system flooding every donor
> in the city.

---

## Blood banks

You are the safety net. Requests reach you only once the donor route has run out
of time.

### Keeping stock current

The inventory screen shows all eight blood groups with the units you hold.
Editing it is the only routine task, and it matters: when a request is offered to
you the screen shows whether you hold enough to cover it, and releasing stock you
do not have is refused.

### Taking on a request

1. **A request escalates to you.** When no donor confirms in time, it is offered
   to every verified blood bank within 50 km of the hospital, with the distance,
   the urgency, and how many units you hold of that group.
2. **Accept it.** This tells the hospital you are covering it and locks it to
   you. Only one bank can accept, and only that bank can release stock against it.
3. **Release the stock.** Entering the units closes the request as `FULFILLED`
   and decrements your inventory by exactly that amount. If you do not hold
   enough, nothing is deducted and the request stays open to you.

---

## Administrators

You are the gate. Until you approve a hospital, it cannot ask anyone for blood.

### The verification queue

Pending hospitals and blood banks are listed separately, each showing contact
details, address, and — for hospitals — the licence number to check. Approving
activates the account immediately. Rejecting disables it, ends any signed-in
session, and sends the applicant your reason, so write something they can act on.

A rejection is not permanent: approving a rejected account later reactivates it.

### Platform statistics

Requests broken down by state, the share that ended in a donation, average time
from raising to fulfilling, and donor counts by blood group and city — useful for
spotting a group you are thin on. Figures are cached for five minutes, so they
can lag slightly behind live activity.

### Suspending an account

Any account can be disabled, which blocks sign-in and invalidates existing
sessions. You cannot change your own status, so there is no way to lock yourself
out.

---

## What happens on its own

Three jobs run in the background. Nobody triggers them, and they are the reason a
request never sits forgotten.

| Job | When | What it does |
|---|---|---|
| **Escalation** | Every 15 min | Moves requests past their urgency deadline to `ESCALATED` and alerts blood banks within 50 km. |
| **Expiry** | Every 15 min | Closes requests whose needed-by time has passed as `EXPIRED` and tells the hospital. |
| **Eligibility** | Daily, 00:05 | Tells donors whose cooldown ended that they can give again, and returns them to matching. |

### How long before a request escalates

| Urgency | Waits for donors | Use it when |
|---|---|---|
| `CRITICAL` | 2 hours | Someone is bleeding now. |
| `HIGH` | 6 hours | Needed today, but not this minute. |
| `NORMAL` | 24 hours | Planned surgery, restocking. |

---

## Who can give to whom

LifeLink applies this automatically — a donor is never shown a request they
cannot safely supply — but it explains a lot of what you see.

| Patient needs | Can receive from |
|---|---|
| O− | O− |
| O+ | O−, O+ |
| A− | O−, A− |
| A+ | O−, O+, A−, A+ |
| B− | O−, B− |
| B+ | O−, O+, B−, B+ |
| AB− | O−, A−, B−, AB− |
| AB+ | every group |

Read the other way round: **O− donors can help anyone**, which is why they are
asked most often, and **AB+ donors can only help AB+ patients**, so they see the
fewest requests. An O+ donor seeing a B+ request is not a bug — O+ can supply O+,
A+, B+ and AB+.

---

## When something looks wrong

Most surprises come from one of a few rules working as designed.

**I am a donor and I see no requests at all.** Check availability is on — new
accounts start off. If it is on, open *Where I can donate*: donors are notified
when a request is raised, so anything raised before you registered or became
available never reached you. That screen shows everything open regardless.

**I can see one request but I know there are others.** You are already committed
to one. Decline it, or see it through, and the rest become available again.

**A request is open near me but is not offered to me.** Three things filter it
out: your blood group cannot supply that patient, the hospital is outside your
travel radius, or you are inside your 90-day cooldown.

**My hospital cannot raise a request.** The account is still awaiting approval.
Check your notifications — if you were rejected, the reason is there.

**There is no Confirm button next to a donor.** It only appears for donors who
have `ACCEPTED`. Anyone still showing `NOTIFIED` has not replied, and you cannot
commit someone who has not agreed to come.

**I confirmed a donor but cannot record the donation.** The panel appears once
the request reaches `CONFIRMED`. If it escalated to a blood bank instead, the
bank records it by releasing stock, not you.

**My blood bank has no escalations.** That is the normal state — it means donors
are covering requests. A request reaches you only after its urgency deadline
passes with nobody confirmed, and only if the hospital is within 50 km.

**I was told I have raised too many requests.** Ten per hour per hospital,
resetting an hour after your first request in the window. Raising duplicates will
not find extra donors anyway, because a donor already tied to one of your
requests is not offered another.
