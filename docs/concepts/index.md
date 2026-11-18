# Concepts

This section explains the engineering idea behind the project, before any code.

## The idea in one paragraph

Fleets of autonomous mobile robots (AMRs) in warehouses do not crash into each other in
narrow aisles. They do not need to see each other. Each robot shares where it is and where it
is going, and a planner gives out **time windows**: "you use aisle 3 from 12:00:05 to 12:00:14,
you wait". This project applies the same idea to **people**. A picker's handheld device
already has Wi-Fi, so it can estimate its own position, share it, and receive a short cue.
People are not robots, so the system only **advises**: cues are small, rare and easy to
ignore, and the plan adapts when people do something else.

## Why "swarm"?

In robotics, "swarm" coordination means many agents follow simple local rules and the
group behaves well. Here each device only does three simple things: measure, report, and
obey a cue. The coordination comes from the shared reservation table on the server. The
design keeps the per-device logic small so it can run on a rugged handheld all shift.

## Read next

<div class="grid cards" markdown>

- [**Problems it solves**](problems.md): aisle jams, head-on blocks, and why existing
  warehouse software does not see them.
- [**System architecture**](architecture.md): devices, broker, engine and dashboard, and how
  data flows between them.
- [**Indoor positioning**](positioning.md): Wi-Fi RTT, fingerprints, Kalman filter, map
  matching and QR checkpoints.
- [**Traffic engine**](traffic-engine.md): time-window reservations, clash rules, the cue
  ladder and priority.
- [**Results and limits**](results.md): what has been measured, what is simulation, and what
  is not solved.

</div>
