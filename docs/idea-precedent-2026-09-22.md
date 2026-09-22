# Precedent — leaning on the idea

> **"Your tests proved 8,000 things about your program. You only wrote down 400 of them."**
>
> An IntelliJ plugin that runs the test suite you already have, mines the **behaviours your code actually
> exhibited** (thousands of facts nobody asserted), turns the durable ones into real IDE inspections, and
> then catches a change that passes every test but breaks a rule **no human ever wrote**.

Written 2026-09-22 for the 42 Abu Dhabi × JetBrains "Help the Developer" challenge.
Everything marked ✅ was checked today against a named source; ❓ is unverified and flagged as such.

---

## 1. The pain (symptom → cause → analogy)

**Symptom.** The suite is green and the product is broken. A refactor, or an AI agent's "fix", passes all
812 tests — and something that used to be true about the system isn't true any more. Nobody notices until
a customer does.

**Cause.** A test suite is not a description of your program. It's a *sample of runs*, of which it checks a
tiny fraction of what happened. Every test execution pushes thousands of observable facts through the JVM —
`esc()` returned non-null, `render()` was never called with an empty list, `discount` landed in [0, 100],
`items` came back sorted, `status` was never null after `pay()`. Tests assert maybe a few percent of those.
**The rest are discarded the moment the JVM exits.** So "all tests pass" means "the things someone thought
to write down are still true", which is not the same claim as "the behaviour is unchanged".

**Analogy.** Tests are a lighthouse, not a perimeter fence. It's lit, it's bright, it's the thing everybody
looks at — and it only illuminates one cone of the coastline. The rest of the perimeter has been walkable
the whole time, and nothing has ever told you where the fence should have been.

**Why now.** The people writing changes are increasingly agents. They run the tests, they see green, they
say "done" — and they cannot know that they altered a behaviour no test pins down. Your own challenge brief
already cites the numbers: 96% of developers don't fully trust AI code, "almost right, but not quite" is
the top complaint about it, agents' most frequent real defects are the *invisible* ones (missing
authorization on a new endpoint, silently duplicated logic).

---

## 2. The idea in one screen

```
        Run tests (one toggle: "Learn")                ← the run you were going to do anyway
                          │
        instrumented run: every project method entry/exit/field-write observed
                          │
        ┌─────────────────┴─────────────────┐
   FACTS  ·  8,412 observations            STATISTICS
   esc(String p1) never returned null       support 1,204 / counterexamples 0
   render(List) never got an empty list     support 2,918 / counterexamples 0
   discount validated in [0,100]            support 8,412 / counterexamples 0
                          │
                    AI TRIAGE (the part Daikon never solved)
                    · name it in domain language
                    · drop accidents of the test data
                    · rank by "would a reviewer care if this broke?"
                    · choose the enforcement form
                          │
        RULES INBOX   ✓ approve   ✗ dismiss   ⌘ edit   →  permanent checks
                          │
     IDE inspection (squiggle as you type)  +  generated assertions  +  MCP tool for agents
```

**Naming the parts.** Facts come from real execution. Rules come from AI triage over facts. Enforcement
comes from the platform: a learned rule becomes an `inspection.kts`, which means it behaves exactly like a
native inspection — editor squiggle, quick-fix, batch runs, Qodana/CI. The plugin validates each generated
inspection against the recorded corpus **before** registering it, so a rule that would have produced a
false positive on the runs you already have is rejected automatically. *(This is the trick that makes the
whole thing trustworthy: the evidence that generates a rule also polices it.)*

---

## 3. The wow moment (this is the demo)

> **An AI agent refactors `applyDiscount()`. All 812 tests pass. The IDE catches the change anyway.**

Sequence, exactly what's on screen:

1. **Learn.** Run the project's test suite with the plugin on. Progress appears in the test tree through the
   platform's own test-runner events ✅. Header at the end:
   `412 tests · 8,412 observations · 61 rules learned · 57 of them nobody wrote`.
2. **Show a rule with teeth.** Click `discount stays in [0,100]` → the panel shows the observation count, a
   histogram of the actual values seen, and a zero-counterexample badge, all built from real traces.
3. **Break it, greenly.** Ask the agent (or just apply a diff) to "simplify" `applyDiscount()` — a
   plausible, idiomatic refactor. Run the suite. **All 812 tests pass.** Green.
4. **Catch it.** The IDE squiggles the new line:
   `Precedent: violates "discount ∈ [0,100]" — observed -5.0 at Order.java:118 (never seen in 8,412 prior
   observations)`. Clicking the squiggle shows the two traces side by side: 8,412 runs in-range, this one
   out. One keystroke applies a suggested guard/test.
5. **Then the closer, for the agent era.** The same rules are exposed over MCP, so the agent can ask the
   codebase *"what rules does this code obey?"* before editing and *"did I break one?"* after. The last line
   of the pitch: **"The tests you already have just became rules no future change can quietly break."**

Second wow, cheap to add: **"the rule we did *not* write"** — show a learned rule the developer disagrees
with, dismiss it, and show that the dismissal is remembered. That proves there's a human in the loop, not a
noise generator. Judges probe exactly this.

---

## 4. Why this isn't "a prompt around an IDE action"

The brief's innovation criterion explicitly targets prompt-wrappers. Three structural answers:

1. **The facts are machine-verified, not model-guessed.** No prompt can know that `discount` was in range
   across 8,412 calls. The AI's job is *triage, naming and encoding* of execution evidence — the LLM is the
   librarian, the tracer is the source of truth. The pitch inverts the usual arrangement, which is exactly
   what "agentic logic where warranted" rewards.
2. **It rides on the workflow the developer already has.** No new tool, no CI change, no annotations to add:
   you run your tests, and the IDE gets smarter as a side effect. That is a UX argument (criterion 2), and it
   is also why this could ship as a product rather than a paper.
3. **The enforcement is native.** Learned rules become inspections, so they appear in the same gutter, the
   same inspection results view, and the same Qodana/CI path as hand-written rules. Nothing is bolted on.

---

## 5. Honest prior-art positioning ✅

| Prior art | What it is | Why this is different | Evidence |
|---|---|---|---|
| **Daikon** (UW, Ernst et al.) | *The* dynamic invariant detector. "runs a program, observes the values that the program computes", reports likely invariants; C/C++/C#/Eiffel/F#/Java/Perl. Research tool, since ~1999. | It emits a **report file**; the famous failure is triage noise, and it never became part of a developer's loop. We close the loop: triage by LLM, then *enforcement inside the editor*, plus a validation pass that suppresses false positives by construction. | ✅ `plse.cs.washington.edu/daikon/` (fetched today) |
| Marketplace search `daikon` | **0 results** | Nothing in the IDE does invariant mining. | ✅ `/api/searchPlugins?search=daikon` |
| Marketplace search `invariant` | 7 results, all unrelated (a colour scheme; "Code Contract editing extensions", 134 dl, which is **manual** pre/post/invariant *editing*; Jimmer support; a perf static analyser) | Nobody has ever *learned* one from a run. | ✅ |
| **AppMap** (74,130 dl, MIT) | Runtime tracing: sequence diagrams, flame graphs, dependency maps, plus an AI chat (Navie) over that runtime data. | Different question. AppMap shows you *what the run did*; Precedent extracts *rules that must keep holding* and enforces them as IDE checks. No invariant mining in its description. | ✅ full description read; `get_local_history`-class features checked |
| **CodeRef** (520 dl) | Adaptive static analysis: ML "learns" which *findings* you dismiss. | Learns about *you*, not about *your program's runtime behaviour*. Static-only. | ✅ description read |
| **Code coverage** (`CoverageQuickLink` 5,799, `Live Coverage` 825) | Per-line execution traces, human-facing. | Coverage is line-level and report-style: it tells you *what ran*, never *what was true when it ran*. Coverage is an **input** to this idea, not a competitor. | ✅ |
| Bundled MCP toolsets | Agent toolsets incl. Inspection Generator / Inspection KTS, Debugger (14 tools), Coverage: **absent** | Precedent supplies the missing read-model an agent needs before editing. | ✅ `jetbrains.com/help/idea/mcp-server.html` |
| IntelliJ's own features | No "invariant" anywhere in the testing or coverage help pages (0 hits, both pages). | Greenfield inside the platform. | ✅ |

**The one attack to expect, and the answer:** *"Daikon did this in 2001."* — Yes, and it never shipped,
because a text report is a research artifact and noise is the enemy of trust. Precedent's contribution is
the part that was always missing: turn observations into *few, named, reviewable, executable* rules —
and validate them against the corpus so the developer never sees a false alarm twice.

---

## 6. Rubric mapping (the brief's own priority order)

| Criterion | How this scores |
|---|---|
| **1. Implementation & PoC, end-to-end** | The demo is deterministic: run tests → rules → break it → caught. Nothing depends on an LLM answering nicely. Fallback path (if the tracer is late): learn from a curated set of methods only — same story, smaller numbers. |
| **2. UX & workflow fit** | One toggle on the run you already do. Rules arrive in a review inbox. Violations surface where you're already looking (gutter, test tree). Zero new ceremony. |
| **3. Innovation & creativity** | Unclaimed space (see §5), and the *angle* is the inversion: the AI is not the oracle, the execution trace is; AI does triage on machine-verified facts. Not a prompt around a common action — a new primitive (execution-derived rules) surfaced in the IDE. |
| **4. Technical features & depth** | Real codebase context (whole-program traces, not a selection), agentic multi-step logic (hypothesise rule → validate against corpus → encode → verify it fires), platform integration (test-runner events ✅, coverage API ✅, inspections ✅, MCP toolset ✅). |
| **5. Presentation** | One memorable moment ("all tests pass — and it's still caught") plus a number that lands: observations vs assertions. |

---

## 7. Build plan (4 people, one weekend) — each track independently demoable

**Track A — Trail (platform & plumbing).** Toggle in the run config; inject the learning agent into test
JVM runs; receive trace events; persist a compact fact store; run everything in a
`Task.Backgroundable` with progress. Owns the fallback (curated method list) if the agent misbehaves.
*Riskiest, start here, first hour.*

**Track B — Miner (facts → candidate rules).** Instrumentation (method entry/exit + field writes) for
project classes; fact templates: non-null, range, equality to constant, membership in an observed set,
sortedness, uniqueness, no-drop/size relations between args and return, ordering between params,
non-empty collections, "field unchanged after construction", string shape; support/counterexample counting.

**Track C — Triage (AI).** Rank, dedupe, name in domain language, and choose the enforcement form. Reject
any rule with a counterexample in the corpus. Emit `inspection.kts` and validate it with the platform's own
validation path before registering (`validate_inspection_kts` ships in the bundled Inspection Generator
toolset ✅). This track is where Koog earns its place ✅ (agent: propose → validate → repair → register).

**Track D — Stage (UX, demo, deck).** Rules inbox (approve / dismiss / edit), gutter squiggle with evidence
popover, the "break it greenly" demo diff, the deck. Also owns the honest-framing slides from §5.

**Cross-cutting rule the four of you must keep:** the *evidence* is the product. Every rule and every
violation must be one click from the raw observations that produced it. Any screen that shows a claim
without a count behind it is a bug.

---

## 8. Risks, and what to do about each

| Risk | Severity | Mitigation |
|---|---|---|
| **Noise / false positives destroy trust** (the reason Daikon stayed in academia) | High | Hard rule: zero counterexamples in the corpus, or the rule doesn't ship to the inbox. Rules are *reviewable and dismissible*, and dismissals persist. Show a dismissal in the demo on purpose. |
| **Instrumentation overhead makes the run unusable** | Medium–High | Scope to project classes only; sample large arguments (cap collection sizes, hash long strings); batch- flush traces off-heap to a file; logpoints as an alternative probe mechanism ✅ (`intellij.debugger.logpoints.*`, 6 modules, incl. `JavaLogpointEditorsProvider` / `JavaLogpointParser`). Prove overhead once, on paper, with numbers. |
| **Agent injection is fiddly** | Medium | Precedent: JetBrains already does exactly this for coverage — `intellij.platform.coverage.agent.jar` (1.1 MB) and `intellij-test-discovery-agent.jar` (635 KB) ship in the distribution ✅; `JavaParameters` is public API ✅. Our agent rides a proven mechanism. |
| **"Your rules are just assertions dressed up"** | Medium | Answer with enforcement: they're IDE inspections (as-you-type squiggles, batch runs, CI), plus a corpus-validation gate assertions can't have. And a rule is *learned*, not written — the developer's time cost is zero. |
| **"What if the codebase is already buggy? You'll learn a bug as a rule."** | Medium — and the sharpest question a judge can ask | Correct, and it deserves a straight answer on stage: invariants are only as good as the runs. The corpus should be a *trusted* baseline (e.g. learn on the last green commit / release tag), rules are per-baseline, and a rule violation is a *question*, not a verdict — hence the evidence popover. Also: learning on a *buggy* baseline mostly produces rules that fire immediately, which shows up as noise and gets dismissed. Say this before they ask it. |
| **Scope creep across four tracks** | Medium | §7's frozen contracts, same discipline as your existing Unroll spec: fact schema and rule schema frozen before anyone writes UI. |

**Two 60-minute spikes before anything else:** (1) instrument one class, run 10 tests, confirm you get
usable facts and acceptable overhead; (2) generate one `inspection.kts` from a hand-written rule and get a
real squiggle in the editor. Both green = the idea is de-risked.

---

## 9. Names (pick one, it costs nothing)

- **Precedent** — the past that binds the future. Accurate to what the tool does: your prior runs establish
  rules that new changes must respect. *(my pick)*
- **Bedrock** — the solid layer under everything you build on.
- **Case Law** — same legal metaphor, warmer, slightly cheekier.
- **Sediment** — traces settling into a record (nice, less self-explanatory).

---

## 10. Sources — all fetched 2026-09-22

- Brief: `~/Downloads/Help the Developer.docx` (extracted text).
- Daikon: `https://plse.cs.washington.edu/daikon/` — "dynamic detection of likely invariants … runs a
  program, observes the values that the program computes"; languages incl. Java.
- Marketplace API: `/api/searchPlugins?search=…` for `daikon` (0), `invariant` (7, none relevant),
  `record runtime behavior learn` (AppMap 74,130), `logpoint tracepoint` (0); `/api/plugins/{id}` full
  descriptions for AppMap (16701), CodeRef, Code Contract editing extensions.
- Platform jar inspection (`jar tf` / `unzip -l` / `javap`, JDK 21):
  `~/.gradle/caches/9.7.1/transforms/f44560136cf9b9cf511ccab7bf51d8d2/transformed/idea-2026.2.3`
  (IU 262.10968.63):
  - `plugins/platform-testRunner-plugin/lib/modules/intellij.platform.smRunner.jar` →
    `com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener` (`Topic TEST_STATUS`,
    `onTestStarted/Finished/Failed`, `SMTestProxy` payloads) — live hook into test runs.
  - `plugins/java/lib/modules/intellij.java.execution.jar` → `com.intellij.execution.configurations.JavaParameters`
    (public) ; `intellij.java.execution.impl.jar` → `JavaRunConfigurationExtensionManager`.
  - `lib/intellij.platform.coverage.agent.jar` (1,120,442 B), `plugins/java-coverage/lib/intellij-test-discovery-agent.jar`
    (634,912 B) — the platform already injects a Java agent into test runs.
  - `lib/intellij.platform.coverage.jar` → `CoverageDataSuitesManager.getInstance(Project).getSuites()`,
    `CoverageSuite.getCoverageData(CoverageDataManager): com.intellij.rt.coverage.data.ProjectData`.
  - `plugins/java/lib/modules/intellij.debugger.logpoints.*.jar` (6 modules) — logpoints are a first-class
    platform feature and a second probe mechanism.
  - `com.intellij.execution.TestStateStorage` in `lib/intellij.platform.execution.impl.jar` — the IDE's own
    record of recent runs/failures.
- Bundled MCP toolsets incl. `validate_inspection_kts` / `run_inspection_kts`:
  `https://www.jetbrains.com/help/idea/mcp-server.html`.
- Literature worth one deck slide each: **ClassInvGen** (arXiv 2502.18917, LLM class-invariant synthesis),
  **BALI** (2601.00882, branch-aware loop-invariant inference with LLMs), **RBCTest** (2504.17287,
  LLMs mine *and verify* test oracles), **Fuzzing Class Specifications** (2201.10874),
  *Auditing and Decomposing Feedback-Driven Evolution in LLM Test Generation under the Oracle Problem*
  (2608.19626 — shows how generated tests validated against a single program manufacture false confidence).
- Koog availability: `https://repo1.maven.org/maven2/ai/koog/koog-agents/maven-metadata.xml` → **1.2.0**
  (published 2026-08-27).

**Deliberately not claimed:** that no tool anywhere mines invariants (Daikon does, in research settings),
and that plugin-side agent injection into IDE test runs is officially supported — ❓ verify that against the
IntelliJ Platform SDK docs before writing it on a slide; the *mechanism* is proven by JetBrains' own
coverage agent, the *sanctioned API surface* is what needs a doc check.
