# Project maintenance

Follow the user's session instructions for coordinating changes. Inspect the working tree before editing; preserve this project's application ID, flavor, content settings and preset assets. Validate affected code and build this project before publishing. Never copy API keys or private app data into the repository.

Retain the existing package ID. Do not add an edition suffix to the public version name. Follow the version policy supplied in the session; increment the version before configuring a changed application build. Documentation-only changes do not require an APK rebuild.

Public UI, documentation and release notes must describe this application only. Do not compare editions or promote other applications.

## Shared project knowledge (required)

Before starting work, read `F:/OneDrive/Documents/projects/PROJECT_KNOWLEDGE_BASE.md` when available, or follow `.codex/KNOWLEDGE_BASE.md`. Treat it as the current internal source of project requirements and status, not a one-time handoff.

After every fix, feature, theme, configuration, version, release, or documentation change, update the corresponding current state and append the request, changed files, validation results, release state, and remaining work to that knowledge base before finishing. Re-read before writing so concurrent agent records are preserved. Never commit or publish the private knowledge base, its pointers, migration archives, user attachments, or credentials.

Use the workspace version authority `../PROJECT_VERSION.properties` and the local coordinator `../tools/sync_app_versions.py` when present. Do not increment a workspace-managed version with a standalone bumpVersion task. Check synchronisation before builds and publication. User-visible UI, documentation, update messages and release notes must present this application as a standalone product without edition labels or references to another application.
