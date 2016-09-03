# Zen IDS Profile Tools
Profile building tools for Zen IDS.

### Build

1. Dependencies:
  * [cfi-common](https://github.com/uci-plrg/cfi-common)
2. Environment:
  * Export variable `$CFI_COMMON` pointing to `cfi-common` (above)
  * Export variable `$ZEN_IDS_PROFILE` pointing to this project
  * Add `$CFI_COMMON` to the `$PATH`
3. Build:
  * First build all dependencies
  * Build this project: `cfi-build -p $ZEN_IDS_PROFILE`
