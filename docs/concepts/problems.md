# Problems it solves

## The problem on the floor

In a person-to-goods warehouse, pickers walk or push carts through aisles to collect items.
Many aisles are **narrow**: one person or one cart at a time. When two pickers want the same
narrow aisle at the same time, one of these happens:

| Situation | What it costs |
| --- | --- |
| **Head-on block**: two people enter from opposite ends | One must back out. With a cart this is slow and annoying. |
| **Waiting behind a picker**: someone stands in the aisle picking | The second person waits, or walks around the whole rack. |
| **Crowding** at popular aisles and at the depot | Small delays add up over a shift, and people bump into each other. |

Each event is small (a few seconds), but they happen **many times per hour per worker**. In
our simulation with 5 pickers, more than 100 blocking events per hour happened without
coordination (see [Results](results.md), simulation only).

## Why existing systems do not fix it

- A **Warehouse Management System (WMS)** plans *what* to pick and in which order, but it does
  not know *where people are right now*. It cannot see that two people are about to meet.
- **Pick path optimisation** makes each route short, but it plans every worker alone. Short
  routes for everyone can still meet head-on.
- **Robot fleet managers** solve exactly this for AMRs, but they need robots that obey
  orders and have precise sensors (lidar, cameras). People carry only a handheld.

## What this project adds

1. **Positioning from infrastructure that already exists.** Warehouses already have Wi-Fi and
   pickers already carry Android or Zebra handhelds. The app estimates position from Wi-Fi
   (RTT where supported, otherwise signal-strength fingerprints). No new tags or cameras are
   needed. Optional QR codes at aisle ends correct drift.
2. **Look-ahead planning.** The engine looks 30 seconds ahead and acts on clashes in the next
   10 seconds, before people meet.
3. **Low-effort cues.** One coloured banner, one vibration pattern, one chime per cue.
   Workers do not need to look at the screen. Cues are rate-limited so they do not nag.
4. **A built-in way to prove it.** The accuracy test measures position error on the real
   floor, and the dashboard counts cues and holds. Nothing is claimed that was not measured.

## Design goals

| Goal | How it shows in the design |
| --- | --- |
| Fewer jams, delay no worse | Cues are only sent when they save more time than they cost. A hold is never longer than 15 s. |
| Low cognitive load | Six cue states, each with its own colour, vibration and sound. Rate limit of one new warning per 15 s. |
| Works on cheap and rugged devices | Android 9+, no special hardware. RTT is used when available, not required. |
| Safe when things fail | If the server is silent for 5 s, the phone shows "No traffic info" and people use their own eyes. Cues expire by themselves. |
| Honest | Simulation and floor numbers are kept apart and labelled. |

!!! warning "Not a safety system"
    Cues help flow. They do not replace looking before entering an aisle. The system must
    never be used to protect people from forklifts or other vehicles.
