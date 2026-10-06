# Canvas FX3 — Samsung report corrections

Evidence: duo-log-20261006-010038.txt and duo-log-20261006-010355.txt from SM-S947B / r8s / Android 16 SDK 36.
Observed main window: StatusBarWindowView 1080×89, density 2.8125. system_icons LinearLayout 89×55 at (952,34); Canvas previously 89×100 at (1033,3). A 314% saved size exceeded the full artboard budget.
Also observed separate com.android.systemui:edgelighting reporting renderer=none, overwriting the actual main process diagnosis.

Changes: main process gate; full artboard cap; bar coordinates include its origin; horizontal placement belongs to saved offset rather than camera cutout; clamp to measured bar; transient Continuum reset on completion, interruption and display changes. Split indicator box resizes with ring. Sliders use a local draft and save once on release; horizontal position gestures use the latest callback. Inline chips are static and live events UI is removed; manual logs remain available.

Verification: unit/Android host tests and local APK build. A new on-device dump and installation test have not been performed. This evidence does not mark every Samsung hook or API verified.
