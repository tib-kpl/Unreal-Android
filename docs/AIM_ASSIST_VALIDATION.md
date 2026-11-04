# Controller aim assistance validation

## Cold-start preference fix (2.4.11)

Activity.onResume may run before SDL initializes its event queue. Sending the
saved assistance level with SDL_PushEvent could silently discard it on a cold
launch, leaving Medium active regardless of the saved selection. Both v200 and
Gold 226 now publish the requested level with an atomic integer. The game thread
loads it before applying assistance and resets its target when the level changes.

The controller-only targeting policy, enemy filtering, visibility traces, response
curves and firm-stick override are unchanged.

## On-device engine integration test

Validated on an Odin2 Portal running the Gold 226 engine in Chizra. A temporary
game-thread fixture spawned a SkaarjWarrior in a visible, collision-free location,
placed the view 300 rotation units away from its upper torso, and called the
production assistance for 120 frames at 60 Hz with no stick deflection. Results:

| Level | Initial yaw error | Remaining yaw error |
| --- | ---: | ---: |
| Off | 300 | 300 |
| Light | 300 | 24 |
| Medium | 300 | 15 |
| Strong | 300 | 10 |

The view and assistance level were restored after measurement. The fixture,
spawned enemy code and direct Chizra startup were removed from the final APKs.
This checks real pawn selection, collision tracing and view rotation in the engine,
rather than only the menu value or an isolated mathematical model.

Both v200 and Gold native engines are built for ARMv7/ARM64 in normal and automotive
variants. Runtime targeting was measured on Gold, the data installed on the test
console; the original v200 runtime was not exercised with a separate data set.

## Smoothing update

Camera correction now uses a filtered angular velocity (18/s response) with an
acceleration limit of eight times the selected maximum speed per second. This
ramps acquisition and target-direction changes instead of instantly applying the
full correction. A smoothstep curve fades assistance between 20% and 65% stick
deflection; at 65% it stops immediately. Fractional rotation is accumulated with
symmetric rounding, preventing tiny corrections from being discarded every frame.

Target loss, occlusion, menus, input-mode changes and settings changes clear all
motion history immediately; there is no continued tracking behind an obstacle.
The existing capture cones and intensity speed limits are preserved.

`aim_smoothing.cpp` exercises the production header on the host in CI: acquisition,
acceleration-limited reversal, convergence without overshoot, fractional motion,
override, reset, and a 45°/s moving target at 30/60/120 Hz. Local measured errors
with UT99 Strong parameters (11/s response, 84 degrees/s limit) were 3.390° / 3.731° / 3.907° after three seconds.
Console visual/feel validation remains to be done; this pass used local tests.
