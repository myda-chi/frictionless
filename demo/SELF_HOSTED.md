# Demoing Frictionless on Frictionless

The plugin reviews its own repository. Nothing is staged, nothing is synthetic, and the code on
screen is the code the judges just watched us talk about.

Three reasons this beats a purpose-built demo project:

1. **It is real.** Real history, real tests, real untested corners. Judges can open any file.
2. **It proves the Kotlin support.** Before the analyser understood `KtNamedFunction`, the plugin
   could not be run on itself at all. Running it on itself is the proof.
3. **It is fast.** Thirteen of nineteen test classes are plain JUnit and run in single-digit
   milliseconds. The demo change is chosen to land on those.

---

## Setup, once

**Set the test runner to the platform, not Gradle.** IntelliJ delegates test runs to Gradle by
default in a Gradle project. Frictionless builds a `JUnitConfiguration` and launches it directly, so
the platform runner is both the path we exercise and the fast one.

> Settings → Build, Execution, Deployment → Build Tools → Gradle → **Run tests using: IntelliJ IDEA**

`.idea/gradle.xml` in this repo carries that setting, so opening the project in the sandbox is enough.

**Open the sandbox on this repository.** `./gradlew runIde`, then open `frictionless` itself as the
project. The Frictionless tool window is on the right.

---

## The change set

Make these three edits in the working tree and leave them uncommitted. Each one is chosen to land in
a different bucket, and together they are the whole product in one screen.

| Edit | Bucket it lands in | Why |
|---|---|---|
| `BucketAssignment.afterTests` — reorder the `when`, no behaviour change | 🟢 **Proven** | `BucketAssignmentTest` reaches it and passes in ~0.01s |
| `Verdict.spoken()` — change "Nothing tests this." to "No test covers this." | 🟡 **Behaviour changed** | `VerdictCopyTest` pins that exact string and goes red immediately |
| `TourService.pickStops` — change `take(5)` to `take(6)` | 🔴 **Unverified** | `TourService` has no test at all. This is the red-to-green method |

The 🟡 beat is the one to point at: **the copy test we wrote to keep the narration and the screen in
step is what catches a wording change.** That is the product arguing for itself.

---

## The run

1. **Press Run.** The ledger fills in a few seconds: three changed methods, one per bucket.
2. **Say the line:** *"CI would call this green."*
3. **Press Play.** The tour opens each method, dims around it, speaks a short verdict, and the ledger
   row follows the editor.
4. **It stops on `pickStops`** — *"Nothing tests this."* Hold the pause. Two full seconds. Do not
   fill it.
5. **Click Pin behaviour.** The agent writes a characterisation test, runs it, reads the failure,
   fixes it, and the mark turns green.
6. **Close:** *"Review time should be proportional to risk, not to line count. That is the friction
   we removed."*

---

## Resetting between rehearsals

```bash
git checkout -- src/main/kotlin/com/github/mydachi/frictionless/analysis/BucketAssignment.kt \
                src/main/kotlin/com/github/mydachi/frictionless/model/Contracts.kt \
                src/main/kotlin/com/github/mydachi/frictionless/tour/TourService.kt
git clean -f src/test/kotlin   # drops whatever the agent pinned last time
```

---

## What to check before demo day

- [ ] The plugin loads — no "incompatible with Kotlin in K2 mode" in the sandbox log
- [ ] `OPENAI_API_KEY` is exported in the shell that launches `runIde`, or Pin behaviour falls back
      to the disabled template (issue #14)
- [ ] A text-to-speech binary is on `PATH`; the log line at startup says which one was chosen
- [ ] The three edits above produce exactly three rows, one per bucket
- [ ] Pin behaviour reaches green at least once, timed — if it is slow, the backup video covers it

## Known limits, if a judge asks

- Branch mode compares a base ref to the **working tree**, so the branch under review has to be
  checked out. That is how a reviewer works locally anyway.
- The endpoint detectors behind the blast radius (main, action handler, HTTP handler) are Java-only;
  a Kotlin `main` is not recognised yet.
