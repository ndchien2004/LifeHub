/**
 * electron-builder configuration.
 *
 * Phase 0 only records the identity and output layout so the paths in main.ts have something
 * real to point at. Phase 6 fills in the jlink JRE, asarUnpack rules, NSIS and DMG targets
 * (07-PHASE-PLAN.md, Phase 6).
 */
module.exports = {
  appId: 'com.lifehub.app',
  productName: 'LifeHub',
  directories: {
    output: 'release',
    buildResources: 'build',
  },
  files: ['electron/dist/**/*', 'electron/splash.html', 'frontend/dist/**/*'],
  extraResources: [
    {
      from: 'backend/target/lifehub-backend.jar',
      to: 'backend/lifehub-backend.jar',
    },
  ],
}
