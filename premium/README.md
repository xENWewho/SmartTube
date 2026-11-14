# SmartTube Premium

This fork of [SmartTube](https://github.com/yuliskov/SmartTube) has a YouTube-style UI,
the SmartTube Premium logo and a YouTube-style player.

## Install
Download from **Releases** → *SmartTube Premium (latest)*. If unsure which file, use
`smarttube_premium_universal.apk`. It installs as its own app, next to official SmartTube.

## Updates
- **In the app:** the built-in updater checks the `premium-latest` release of this fork.
- **Weekly (Sundays):** *Weekly update from SmartTube* merges the newest official SmartTube
  release into `master`, keeping the Premium changes, then builds and publishes a new APK.
  If SmartTube changed the same lines, nothing is changed and the run fails; the current
  version keeps working.
- **On every change to `master`:** *Build SmartTube Premium* builds and publishes.

Don't use **Sync fork → Discard commits** on GitHub: it deletes the Premium changes.

## Signing
Builds are signed with `smarttube-premium.p12`. Its password is the `KEYSTORE_PASSWORD`
repository secret (Settings → Secrets and variables → Actions). Keep the password private
and don't change the key, or new versions won't install over old ones.
