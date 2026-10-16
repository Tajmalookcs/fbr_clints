# CLAUDE.md — FBR Clients (Android)

## Standing instructions
- Read `docs/DESIGN.md` first. It is the **approved** design (2026-10-16). Follow it; ask before changing it.
- The owner works from the **mobile app** and has no local project folder. Do all work in this repo.
- There is no local Android SDK. **Build the APK with GitHub Actions** and give the owner a link they can download on their phone.
- Work in the build-plan steps in `docs/DESIGN.md` §7. After each step: commit, push, confirm CI is green, and report back.
- **Public repo:** never commit client data, sample returns or screenshots, release keystores, or secrets. Use GitHub Secrets for signing.
- Don't invent FBR/IRIS codes. Unknown codes and sub-types are left for the owner to fill in Settings.

## Owner preferences
- Keep explanations short and plain.
- Amounts shown as `1,234,567` (whole rupees).
