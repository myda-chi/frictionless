<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# frictionless Changelog

## [Unreleased]
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- A1 — working-tree change set: `WorkingTreeChangeSetProvider` reports the files that differ from the
  checkout's base revision (tracked changes plus unversioned files), each carrying its kind and the
  base `ContentRevision` that A2 parses. File level only — methods are A2's deliverable.
