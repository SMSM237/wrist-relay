# Wrist Relay · Interface direction

## Content lock

The phone app exposes only measured Android state. Listener permission and the
listener's connection callback are separate facts; a rebind request is never
shown as a successful connection. Watch vibration is user-confirmed, not
automatically verified. A saved rule remains editable after its temporary
source notification expires.

## Design lock

The interface is a warm, quiet utility rather than a dark signal dashboard.
Wanted Sans provides one consistent Korean/Latin type voice. A bone canvas
(`#F7F5F0`), white working surfaces, graphite ink (`#262721`), and restrained
terracotta actions (`#97543C`) replace the old navy/mint palette. Green appears
only for verified positive state; burgundy marks blocked or disconnected state.

The compact header pairs **Wrist Relay** with the smaller line
**원하는 알림을 손목으로**. The main screen then names the task, **알림 전달**,
and gives one prominent one-hour capture action. Status rows state the observed
value and a recovery action. A rounded, raised three-destination navigation
surface floats above the system navigation area. Other screens use the same
canvas, type scale, rounded controls, and tonal surfaces.

Touch targets are at least 48 dp. State is conveyed with text as well as color.
System bars use dark icons on the light canvas. The layout must tolerate
larger font settings, system insets, and an empty rules/capture state. Motion
is intentionally minimal; it is not needed to interpret live diagnostics.

## Review evidence

`screenshots/` contains captures of the installed Android emulator build, not
composites or browser mockups. The Galaxy S25 Edge and Galaxy Watch Ultra
(2025) cannot be physically verified in this remote workspace; delivery and
vibration on those devices remain a separate on-device check.
