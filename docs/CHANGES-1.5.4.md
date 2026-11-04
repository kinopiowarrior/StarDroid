# StarDroid 1.5.4

Mission countdowns now disappear when they reach zero. This applies to the original StarCraft and Brood War campaigns, including the defense timer in **Desperate Alliance** and the Chrysalis timer in **Agent of the Swarm**. The last second stays visible. If a mission sets a new positive countdown, the timer appears again.

The fix is in the shared Android mission display and the web campaign display. Mission scripts still receive the expiration event and can advance objectives or start another timer.

Install the APK over 1.5.3. The release uses the same simulation rules, replay format and multiplayer protocols as 1.5.3. The attached **StarDroid-1.5.4-source.zip** contains the source matching the APK and its validation records.
