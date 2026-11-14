# Application Baseline Profile

Run only on a dedicated API 34+ emulator with a fresh test installation. The producer uses the
nonMinifiedRelease variant, creates two synthetic cards through real onboarding/editor UI, then
records cold startup, keypad input, card selection and the card editor. It never opens country
information or camera, and takes no screenshots. A real personal wallet must not be used.

Generate with `./gradlew :app:generateReleaseBaselineProfile -Pdirhamrub.x86=true`.
The plugin does not regenerate profiles during normal APK builds. The `Baseline Profile` workflow
runs on agent-owned `profiles/**` branches or manual dispatch, exports the actually recorded
baseline/startup text and source SHA to the rolling `baseline-profiles` prerelease. It creates no
Actions artifacts and does not publish an application APK.

After inspecting the matching source SHA, commit the generated text to
`app/src/release/generated/baselineProfiles/`. Release builds compile/merge it with dependency profiles and R8 rewrites method references.
The profile contains code rules, not phone-specific compiled machine code. GitHub APK installation
uses ProfileInstaller/Android background dex optimization; no speed percentage is implied.

Recorded profile provenance and checksums: [profile-source.json](profile-source.json).
The profile was refreshed for 1.51.0 after navigation, cache and background calculation changes;
the two real UI producer scenarios passed on API 34. Counts and exact checksums are in the JSON.
