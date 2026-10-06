# Canvas FX4 — effects and active indicators

Evidence: duo-log-20261006-012318.txt, Samsung SM-S947B, Android 16, running FX3 code 20.
The main Canvas is attached. The log reports charge power unknown and no initial glow; proximity toggles pause rapidly; no unlock check starts in the captured period. Existing code gated unlock by 3.5 seconds after wake and by recent optional biometric hook activity.

Changes: USER_PRESENT itself confirms unlocking, including PIN, without the two old gates. Check remains exclusive for 3000 ms including its final 280 ms fade out, then the ordinary occupant enters over 160 ms. Charge/audio/camera/pocket cannot mask the check. Carousel is paused underneath and resumes afterwards.
Every charger gets a 3000 ms connection glow and an ongoing rotating highlight confined to the existing arcs. Fast-charge detection remains telemetry, not a prerequisite for animation. Charge bolt is one normal carousel member.
Active-list changes retain elapsed dwell time instead of restarting the current icon. Wi-Fi return does not reorder the list. The log now records active members and occupant changes.
Location means the enabled system toggle OR an active location operation; no coordinates are requested. Bluetooth broadcasts from its privileged sender use a separate receiver gated by BLUETOOTH_CONNECT. References: https://developer.android.com/develop/background-work/background-tasks/broadcasts and https://developer.android.com/reference/android/location/LocationManager.html
Proximity/face-down needs 500 ms stability before pausing; release is immediate. The exclusive unlock effect overrides the pause.

Validation: pure timing tests cover all 21 occupants over multiple rounds and a fluctuating GPS list; Android host tests cover unknown charger power, simultaneous Bluetooth/GPS/torch/airplane/charge, unlock after a long wake, priority over camera/audio/pocket, location toggle, and proximity noise. Existing geometry and UI regressions remain included.
Actual renderer frames were inspected on the computer; desktop font and gradient are approximations. No installation of FX4 on the Samsung has been performed here. The Samsung dump is evidence, not a claim of complete ROM validation.
