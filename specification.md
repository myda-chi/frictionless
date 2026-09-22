# Receipts — Technical Specification

**42 Abu Dhabi × JetBrains Hackathon · "Help the Developer"**
**Date:** 22–23 September 2026
**Author:** Hackeem Mensah Bosu
**Repository:** `frictionless` — the product ships as **Receipts**; plugin ID, README and slides all use *Receipts*.

---

## 1. Problem Statement

AI now writes roughly **42%** of committed code. **96%** of developers say they do not fully trust AI-generated code, yet only **48%** actually verify it before committing ([Sonar, 2026](https://www.sonarsource.com/company/press-releases/sonar-data-reveals-critical-verification-gap-in-ai-coding/); PR review time is up **91%** — [review-bottleneck data, 2026](https://www.flowverify.co/blog/ai-code-review-bottleneck-2026-data)). The result: CI stays green not because the code is correct, but because nothing tests the new code at all.

**The bottleneck has moved from writing code to verifying it — and no existing tool measures verification.** Every competing approach reviews AI-generated code by asking a second AI for its opinion on a text diff. That is an opinion, not evidence.

**Receipts verifies by evidence.** It walks the call graph from every changed method, runs only the tests that genuinely reach that method, and reports what is proven, what changed, and what nothing tests.

---

## 2. Product Summary

| | |
|---|---|
| **Name** | Receipts |
| **Type** | IntelliJ IDEA plugin |
| **Languages supported** | Java, Kotlin |
| **Core question answered** | "Of these changes, which of them is anything actually checking?" |
| **Review modes** | Uncommitted working tree · any branch compared against `main` (§3.0) |
| **Entry point** | A **Receipts** tool window in the sidebar; its toolbar holds the source picker and the Run and Play buttons (§3.0) |
| **Collaboration** | A review can be walked together over Code With Me (§3.5.1) |
| **Differentiator** | Verifies via real test execution over a real call graph — not an LLM's opinion on a diff |

---

## 3. Core Feature Set

### 3.0 Change Source (mandatory)

Receipts reviews a **change set**, not specifically uncommitted work. The toolbar's source picker offers two modes:

| Mode | Compares | Use |
|---|---|---|
| **Working tree** (default) | `HEAD` → uncommitted changes | What did the agent just do to my checkout? |
| **Branch** | `main` (or the chosen base) → selected branch | Reviewing someone's PR before approving it |

Branch mode prompts for the branch with a searchable popup over `GitBranchesCollection`, remembering the last base used. Everything downstream is identical: both modes emit the same `ChangeSet`, so the analyser, the ledger, the agent and the tour are unaware of which mode produced it.

**Entry point.** No default keybinding — the action is registered so users can bind their own, and the sidebar tool window's toolbar is the primary trigger. This avoids fighting IntelliJ's existing shortcut map, which is dense and personally customised.

### 3.1 The Ledger (mandatory — the product)

Every changed method in the selected change set (§3.0) is sorted into exactly one of three buckets:

| Bucket | Meaning | Action surfaced to user |
|---|---|---|
| 🟢 **Proven** | Covered by existing tests; tests re-run and pass | None — move on |
| 🟡 **Behaviour changed** | Covered, and a reaching test **fails now** | Jump to the failing assertion |
| 🔴 **Unverified** | Changed, and nothing in the repo executes it | **Pin behaviour** (§3.4) |

**Definition, to settle it before hour 9:** we do **not** run a baseline against the pre-change code — a second build is too slow to be worth it. 🟡 therefore means *a test that reaches this method is red right now*. That is honest, cheap, and demonstrably useful; a true before-and-after comparison is roadmap, not scope.

The **Unverified** bucket is the actual product. This is where AI-introduced bugs live, and no existing tool reports it.

### 3.2 Real Impact Analysis (mandatory)

- Does **not** diff text.
- Walks the **call graph** outward from each changed method (via `ReferencesSearch` / `OverridingMethodsSearch`, breadth-first, stopping at the test source root) to find every test that **genuinely reaches** that method.
- Runs **only** those tests — a targeted subset, not the full suite. Result: seconds, not minutes.
- This is the direct answer to "use real codebase context, not just the selected snippet."

### 3.3 Blast Radius (mandatory)

- Click any changed method to see:
  - Its call sites (who calls it)
  - What it calls (what it depends on)
  - The endpoints it is reachable/"live" under
- Purpose: let the developer see what a mistake in this method would actually cost before deciding how much scrutiny it deserves.

### 3.4 Pin Behaviour — the AI Agent (mandatory)

On any **Unverified** method, one click starts a Koog-based agent that:

1. Reads the method, its callers' real arguments, and the repo's existing test conventions
2. Writes a **characterisation test**
3. Runs it
4. Reads the compiler/test failure output
5. Fixes and repeats
6. **Hard cap: 3 attempts**, then reports honestly and stops

**Trust guarantee:** the test runner is ground truth. The AI *proposes* a test; the runner *disposes* (passes or fails it). A hallucinated pass is structurally impossible, because passing is defined by actual execution, not by the model's claim.

### 3.5 Autopilot Review (wow feature — the demo moment)

A **Play** button that drives the IDE hands-free:

- Auto-scrolls through the five most interesting stops (not all changed methods)
- A moving spotlight dims everything else on screen
- One short narrated verdict line per stop
- Gutter marks flip red → green live as results stream in

This is what makes the product legible from the back of a room — the audience watches the IDE move by itself, not a person narrating a slide.

#### 3.5.1 Reviewing together over Code With Me (stretch)

In branch mode the review is a PR review, so it should be shareable. **Share Receipts session** starts a [Code With Me](https://www.jetbrains.com/help/idea/faq-about-code-with-me.html) session and copies the invite link, so the author and reviewer walk the same ledger together.

**What actually works, and what does not.** The Autopilot tour is editor navigation — scrolling, caret movement, highlighters — which is exactly what Code With Me synchronises, so a guest following the host sees the tour. But [not all tool windows are available to guests](https://www.jetbrains.com/help/idea/faq-about-code-with-me.html), and a third-party tool window is very unlikely to render on the guest side. So: **the ledger stays host-side, the tour is the shared surface.** Verify this early with two IDEs — do not discover it on stage.

### 3.6 Narration (polish)

- Live text-to-speech via whatever binary is present on the machine, detected at startup, in this priority order: `piper` → `espeak-ng` → `spd-say` → `say` (macOS)
- On-screen-only fallback if no TTS binary is found
- Spoken lines are capped at **under six words**; full detail stays on screen
- **Identifier humanisation is mandatory**: camelCase and initialisms must be split/expanded before synthesis (e.g. `applyFxMargin` → "apply F-X margin") — described in the source plan as "fifteen lines of code that decide whether the demo sounds deliberate or broken"
- **Security note:** narration text includes raw identifiers from source code — these must **never** be shell-interpolated. Pass as an argument list to `GeneralCommandLine`, never as a concatenated string
- Verdict strings live in exactly one place (`Verdict.display()` and `Verdict.spoken()`) so the on-screen text and the spoken text can never drift apart mid-demo
- **Justification, for the inevitable "why does it talk?" question:** a hands-free, eyes-free review pass, and a screen-reader-friendly verdict surface

**The copy (implement exactly this).** Spoken lines stay under six words; the screen carries detail.

| Moment | Spoken | On screen |
|---|---|---|
| Tour starts | *"Seventeen methods changed. Reviewing."* | `17 changed · 34 tests selected` |
| Entering a stop | *"Checking apply F-X margin."* | `TransferService.applyFxMargin()` |
| 🟢 Proven | *"Proven. Four tests cover this."* | `✓ 4 tests reached it · passed in 0.3s` |
| 🟡 Behaviour changed | *"Behaviour changed. Two tests failing."* | `⚠ 2 reaching tests red — click to open` |
| 🔴 Unverified | *"Nothing tests this."* | `✗ no test reaches this code · 3 call sites` |
| Agent starts | *"Writing a test."* | `generating characterisation test…` |
| Agent retries | *"Didn't compile. Fixing."* | `attempt 2 of 3 — missing import` |
| Agent succeeds | *"Pinned. Verified."* | `✓ FxMarginTest passed` |
| Tour ends | *"Twelve proven. One pinned."* | `12 proven · 2 changed · 0 unverified` |

Present tense, no pronouns, no "I" — the plugin reports facts about the repo, it does not apologise for itself. Keep *"Didn't compile. Fixing."* even though it sounds like a stumble: it is the clearest live proof the agent is really running.

### 3.7 Graceful Degradation (mandatory)

| Condition | Behaviour |
|---|---|
| No tests exist in the repo | Everything is reported as Unverified — this is the correct, useful answer, not an error state |
| No network available | The ledger still works fully; only the Pin Behaviour agent is unavailable |
| Agent cannot produce a passing test in 3 attempts | Reports honestly, stops, does not spin indefinitely |

---

## 4. Architecture

### 4.1 Data flow

```
Toolbar button (working tree | branch → main)
      │
      ▼
ChangeSet — changed files
  working tree: ChangeListManager
  branch:       git4idea diff, base ref → head ref
      │
      ▼
Changed methods (PSI compare: base blob vs head, at method level)
      │
      ▼
Impact graph (ReferencesSearch BFS, stop at test source root)
      │
      ├──────────────► Impacted tests (JUnit run config) ───► Ledger
      │
      └──────────────► Unverified methods ───► Koog agent (write → run → fix) ───► Ledger
                                                                                        │
                                                                                        ▼
                                                                         Autopilot tour + narration
```

### 4.2 Component-to-API mapping

| Piece | Built on |
|---|---|
| Entry point | Tool window + toolbar actions; no default keybinding |
| Branch picker | `GitBranchesCollection`, searchable popup, last base remembered |
| Changed files | `ChangeListManager` (working tree) or a git4idea ref-to-ref diff (branch) |
| Changed methods | Two `PsiFile` trees (base blob vs. head blob or working tree), diffed at method level |
| Impact graph | `ReferencesSearch`, `OverridingMethodsSearch`, BFS up the call graph, stopped at the test source root |
| Test execution | `JUnitConfiguration` built programmatically, `ExecutionEnvironmentBuilder`, results collected via `SMTRunnerEventsListener` |
| Ledger UI | Tool window, Kotlin UI DSL |
| Editor marks | `LineMarkerProvider` (gutter), `InlayHintsProvider` (inline badges) |
| Autopilot tour | `CaretModel` + `ScrollingModel` + a dimming `RangeHighlighter`, stepped by a coroutine |
| Narration | `GeneralCommandLine` to the detected TTS binary, run off the EDT, utterances queued so none overlap |
| Agent | Koog, with "run the test" wired in as a callable tool |
| Collaboration | Code With Me session started from the toolbar; tour syncs, tool window does not (§3.5.1) |

### 4.3 Frozen shared contracts

Three types must be agreed and frozen in **hour one**. Every track builds against these; nothing else is a shared coupling point.

```kotlin
// ChangeSet — what we are reviewing; the only thing that differs between modes
data class ChangeSet(
    val source: Source,        // WorkingTree | Branch(base, head)
    val methods: List<ChangedMethod>
)

// ChangedMethod — the PSI element, its file, its call sites, the tests that reach it
data class ChangedMethod(
    val methodPointer: SmartPsiElementPointer<PsiElement>,
    val file: VirtualFile,
    val callSites: List<SmartPsiElementPointer<PsiElement>>,
    val reachingTests: List<SmartPsiElementPointer<PsiElement>>
)

// Verdict — the bucket, the counts, and the two rendering surfaces
data class Verdict(
    val bucket: Bucket, // PROVEN, BEHAVIOUR_CHANGED, UNVERIFIED
    val counts: VerdictCounts
) {
    fun display(): String   // on-screen text
    fun spoken(): String    // TTS text — must never drift from display()
}
```

Until the Analysis track produces real data, every other track works against a **hardcoded fake list of three methods** — deliberately, so UI and Agent tracks are demoable before the analyser is finished, and a slip in one track cannot take down the whole demo.

---

## 5. Team Split

Task-level breakdown, with acceptance criteria per item: [`deliverables.md`](./deliverables.md).

| Track | Owns | Depends on | Can start |
|---|---|---|---|
| **Analysis** | Changed-method detection, impact graph, the `ChangedMethod` and `Verdict` types | Nothing | Immediately |
| **Execution** | Building/running the JUnit config, collecting results into verdicts | `ChangedMethod` type only | After hour 1 |
| **UI** | Tool window, ledger, gutter marks, inlay hints, the tour, narration | `Verdict` type only | After hour 1 |
| **Agent** | Koog loop, prompt construction, repo-convention context, the run-test tool | `ChangedMethod` type; a **stub runner** until Execution lands | Immediately |

**The Agent track does not wait for Execution.** It starts hour 2 against a stub runner that returns a canned pass or fail, and swaps to the real runner when Execution lands. Four hours is not enough for a must-ship component, and waiting contradicts the fake-data principle below.

---

## 6. Build Plan — 24 Hours

| Hours | Milestone | Tier |
|---|---|---|
| 0–2 | Plugin template running, tool window + toolbar actions render. **Pull the OpenAI key from the vault and smoke-test it immediately** — not at 2am | Setup |
| 2–5 | `ChangeSet` → changed methods, rendered in the ledger. Working-tree mode first | **Must ship** |
| 2–17 | *(Agent track, in parallel from hour 2 against the stub runner)* | **Must ship** |
| 5–9 | Impact graph: covered vs. unverified. **This is the product** — nothing after this matters if it isn't done | **Must ship** |
| 9–13 | Run impacted tests; stream proven / behaviour-changed results | **Must ship** |
| 13–17 | Agent swapped onto the real runner; generate → run → read failure → fix → green | **Must ship** |
| 15–17 | Branch mode: picker + ref-to-ref diff (the analyser is already source-agnostic) | **Must ship** |
| 17–19 | Autopilot tour: auto-scroll + spotlight (skip fancy dimming if tight on time) | Wow |
| 19–20 | Narration: provider detection, identifier humaniser, verdict lines | Polish |
| 20–21 | Code With Me: share action, verified host-and-guest with two IDEs | Stretch |
| 20–22 | Icons, empty states, progress indicators, dark theme, keyboard navigation | Polish |
| 22–23 | Rehearse **five times** on a frozen, offline repo. Record a backup video | **Must ship** |
| 23–24 | Slides: trust statistic, ledger screenshot, red-to-green clip, roadmap | **Must ship** |

**Checkpoint: Hour 13.** Something demoable must exist by this point. Everything after hour 13 is upside, not requirement.

### 6.1 Cut list (in cutting order — cut top to bottom as time runs short)

1. Code With Me sharing
2. Narration
3. Tour dimming (fall back to plain auto-scroll)
4. Live test execution (fall back to the static covered-vs-unverified ledger — still a complete, honest product on its own)

Branch mode is **not** on the cut list: it is what makes this a PR-review tool rather than a personal one, and it costs two hours because the analyser is source-agnostic by design.

**Never cut:**
- The impact graph — it *is* the idea
- The agent — it is the brief's explicit requirement that this be an AI solution

---

## 7. The Demo (target: ~90 seconds)

Built backwards: if a feature does not appear in this sequence, it is not a priority.

| Step | Action | Line / Note |
|---|---|---|
| 1 | Open a real Java/Kotlin repo where an AI agent has already made a plausible multi-file change. **Pre-baked — never generated live.** | — |
| 2 | Press the shortcut. Ledger appears in ~4 seconds. | *"17 methods changed · 11 proven · 2 behaviour changed · 4 unverified."* |
| 3 | State the stakes in one line. | *"CI would call this green."* |
| 4 | Press **Play**. The IDE tours itself hands-free: opens each interesting method, spotlights it, speaks a short verdict, flips gutter marks as results stream in. | Let it run 8 seconds in silence — do not talk over it |
| 5 | It stops on a red (unverified) method. | *"Nothing tests this."* — then **hold two full seconds of silence. Do not fill it.** |
| 6 | Click **Pin behaviour**. The agent writes a characterisation test, runs it, reads the failure, fixes it, runs again — green. | **This red-to-green moment is the whole demo. Everything else exists to set it up.** |
| 7 | Switch the source picker to **Branch**, pick a teammate's branch, run it again. | *"Same thing on someone else's PR — and I can hand them the link."* One beat only; do not re-run the whole tour |
| 8 | Close. | *"Review time should be proportional to risk, not to line count. Here are the receipts."* |

---

## 8. Demo Day Checklist

Run this **at setup**, not five minutes before going on stage:

- [ ] Demo repo frozen on disk; the AI-authored change already staged; git state reset to a known point
- [ ] Presentation mode on; font large enough to read from the back row
- [ ] TTS binary present on the demo machine; volume up; **audio actually routed to the projector**, not just the laptop speakers
- [ ] One full dry run on the real repo, end to end
- [ ] Backup video recorded and already open in a browser tab
- [ ] OpenAI key confirmed working from the demo machine, with a deterministic template-test fallback if the live call fails

### 8.1 Risks and mitigations

| Risk | Mitigation |
|---|---|
| API key requires network, and venue Wi-Fi fails | Cache aggressively; fall back to a template-generated test; ensure the ledger works fully with zero network |
| The agent spins on a method it cannot test | Hard cap of 3 attempts, then report honestly and move on |
| The tour's narration talks over the presenter during Q&A | Kill the TTS process immediately when the tour is cancelled |
| A judge opens their own project and it looks "wrong" | It will correctly report everything as unverified if no tests exist — **this is correct behaviour, not a bug.** Rehearse this exact scenario; it is the most likely live question |
| Code With Me does not render our tool window for the guest | Expected — the tour is the shared surface. Test with two IDEs before demo day; if it fails entirely, cut it (it is first on the cut list) |
| Branch mode hits a repo with no `main` | Default the base to the current checkout's tracking branch, and let the picker choose both sides |
| The ledger is unreadable on the projector | Large font, high contrast, large counters. Verify by literally checking from across a room |

### 8.2 Two non-negotiable presentation rules

1. **Do not talk over the Autopilot tour.** Let the IDE move in silence for the full duration — that silence *is* the moment.
2. **Hold the pause after "Nothing tests this."** Do not fill it with more words. Let it land.

---

## 9. Engineering Notes — Easy to Get Wrong

1. **Never shell-interpolate identifiers.** Narration text contains raw names pulled directly from source code. Always pass arguments to `GeneralCommandLine` as a list, never as a concatenated/interpolated string — this is a real injection risk, not just a style preference.
2. **Speak identifiers, don't read them literally.** Split camelCase and expand initialisms before sending text to the TTS engine (`applyFxMargin` → "apply F-X margin"). This single detail determines whether the demo sounds polished or broken.
3. **Single source of truth for verdict text.** `Verdict.display()` and `Verdict.spoken()` must live in one place so the on-screen ledger and the narrated voice line can never contradict each other mid-demo.

---

## 10. Why This Wins

**The opener.** *"Last year someone built a robot that uses the IDE like a human. Since then, robots started writing the code — 42% of it. We built the thing that checks their work."*


Every competing approach to "review AI-generated code" ultimately reduces to: *ask a second AI what it thinks of a text diff.* That is an opinion — informed, perhaps, but unverifiable and occasionally confidently wrong, exactly like the code it's reviewing.

**Receipts verifies by evidence.** It runs the actual codebase. A method is "proven" because a real test actually executed it and passed — not because a language model said it looked fine.

This directly satisfies the brief's explicit warning against "wrapping a prompt around a common IDE action": Receipts' core mechanism (call-graph walking + targeted real test execution) produces evidence with or without any AI involved at all. The AI (Koog agent) is used only where it belongs — to *close* an identified gap (writing a missing test) — and even then, it is checked by the same ground truth (the test runner) as everything else, so it cannot fabricate a result.
