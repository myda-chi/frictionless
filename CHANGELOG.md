<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# frictionless Changelog

## [Unreleased]
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- A1 — working-tree change set: `WorkingTreeChangeSetProvider` reports the files that differ from the
  checkout's base revision (tracked changes plus unversioned files), each carrying its kind and the
  base `ContentRevision` that A2 parses. File level only — methods are A2's deliverable.
- A2 — method-level PSI delta: `MethodLevelDelta` parses the base revision and the working tree as
  `PsiFile`s and matches their methods by signature, reporting added, removed and modified methods as
  `ChangedMethod`s. Methods carry the empty verdict until A3 and A4 classify them. Adds the
  `com.intellij.java` dependency the Java PSI (`PsiMethod`, `PsiClass`) requires.
- A3 — impact graph: `ImpactGraph` walks the call graph breadth-first from each changed method via
  `ReferencesSearch`, dispatch-aware through super methods, stopping at test methods in the test source
  root, and fills in `reachingTests` and `callSites`. An empty list means nothing in the repo executes
  that method, which is the answer the ledger exists to show.
- A6 — blast radius: `BlastRadiusResolver` resolves what a changed method calls and the live entry
  points it is reachable under (`main`, an action handler, an HTTP handler, a test), so the detail
  panel can show what a mistake in it would cost. Entry points are heuristic and documented as such.
