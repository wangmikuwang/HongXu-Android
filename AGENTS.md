# Alpha / Beta synchronization

Alpha and Beta are independent projects and repositories. Every shared feature, bug fix, layout or architecture change must be applied to both projects. Counterpart: `../WenYouTextQuest-Beta`. Inspect both working trees first; preserve project-specific application IDs, flavors, content settings and preset assets. Validate and build each affected project separately, then synchronize both GitHub repositories. Never copy API keys or private app data into either repository.

Keep `versionMajor`, `versionMinor`, `versionPatch` and `versionCode` aligned across Alpha and Beta releases. Retain each flavor suffix and package ID. For shared changes, increment both versions together before configuring the build; documentation-only changes do not require an APK rebuild.
