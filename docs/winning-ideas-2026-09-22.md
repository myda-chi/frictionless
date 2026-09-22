# Winning ideas — reading of the official brief + prior-art-checked candidates

Written 2026-09-22 for the 42 Abu Dhabi × JetBrains "Help the Developer" challenge.
Every novelty claim below was checked against a named source today (marketplace API, IDE help page, or the
real platform jar in the Gradle cache). Anything I could not verify is marked **UNVERIFIED**.

---

## 0. What the docx actually rewards

Judging order (from the brief): **1) Implementation & PoC → 2) UX → 3) Innovation → 4) Technical depth →
5) Presentation.** Two of those deserve emphasis because they reorder the usual hackathon math:

- **PoC runs end-to-end beats a beautiful deck.** "does it actually run and solve the developer pain
  point end-to-end, not just a mocked screenshot". A deterministic core (something that either runs or
  doesn't, with output on screen) is worth more than a prettier AI feature you cannot demonstrate.
- **Innovation is explicitly defined against one failure mode**: "anything that goes beyond 'wrap a
  prompt around a common IDE action'". The brief then names the two starting points most likely to be
  judged as prompt-wrappers — *routine test creator* and *refactor suggester* — and tells you exactly
  what would rescue them: repo conventions, surrounding context, edge cases a generic prompt misses.
- **Depth is defined as** "use of real codebase context (not just the selected snippet), multi-step or
  agentic logic where it's warranted, and integration with JetBrains tooling".
- **AI is mandatory**: "Your application is expected to be an AI solution" (OpenAI key provided in a
  vault via GitLab; Koog offered explicitly if the logic is agentic). So "no AI, just a good tool" is
  not an option — but "AI as the thing that *chooses and explains*, deterministic tools as the thing
  that *decides*" is the strongest reading of criteria 1 + 3 at once.

The strategy that falls out of this: **pick a pain where the agent's own words are unreliable, and let
the IDE be the judge.** The IDE owns data an LLM cannot obtain by reading your repo (coverage from an
instrumented run, test-runner state, semantic call graphs, inspection/quick-fix machinery). That is the
difference between "wrap a prompt" and a product.

---

## 1. Ground truth — what already exists (verified today, 2026-09-22)

### 1.1 The IDE's own agent surface (IntelliJ 2026.2 / build 262.10968.63)

The bundled MCP Server ships these toolsets (`jetbrains.com/help/idea/mcp-server.html`, help version
2026.2): Analysis, Code Insight, Database, Debugger (**14 `xdebug_*` tools**, incl. set-breakpoint,
step, read frame values, **mutate a variable**), Execution, File, Formatting, Inspection Generator,
Inspection KTS, Patch, Read, Refactoring (**`rename_refactoring` only**), Search, Skill Search,
Terminal, Universal, VCS.

**Absent from the bundled toolset list, as of 2026.2 (checked on the help page today):**
coverage / line-hit data · quick fixes & intentions · structured test results · Local History ·
profiler / performance. VCS tools are only `get_repositories`, `git_status` — no blame, no log.

### 1.2 …but a third-party plugin already filled two of those gaps — for agents

**MCP Server AI Companion** (`plugins.jetbrains.com/plugin/31060`): **7,571 downloads, FREE**, vendor
unverified (Maxime HAMM). Its own description (14.3 KB, read in full today) advertises ~100 tools,
including:

| Already exposed to agents by that plugin | Implication |
|---|---|
| `get_build_output`, `get_console_output`, **test results**, run configurations | test-result exposure is **taken** |
| `get_file_problems`, `get_quick_fixes`, **`apply_quick_fix`**, `list_inspections` | "expose quick fixes to agents" is **taken** |
| **`get_local_history`** (Local History revisions, labels, diffs) | Local History is **taken** |
| `show_diff`, `navigate_to`, `highlight_text`, git ops incl. blame & file history, PSI tree dump | navigation/diff/git are **taken** |

Grepping that full description for `coverage` / `mutation` / `flaky` returned **nothing**. So the
sharpest still-open gap in the agent-facing surface is **coverage — the product of an instrumented
run**, not of reading IDE state. That is consistent with why it is missing: you cannot answer it by
reading a file, you have to *run* something and parse the result.

### 1.3 Marketplace crowding, lane by lane (downloads, today)

| Lane | Evidence | Read |
|---|---|---|
| AI test generation | Diffblue Cover 210,893 · Machinet 116,030 · TestSpark 8,780 · Qodo 648,649 · Cody 486,548 | **saturated** — 6 vendors will be on the judges' minds |
| Mutation testing in IDE | PIT Mutation Testing 99,627 · PITest Helper 3,188 · Mutation Tester 3,099 | running mutants is **solved**; *using* mutation to judge AI tests is not |
| Coverage UI | CoverageQuickLink 5,799 · Live Coverage 825 | human-facing coverage views exist |
| "Why did this change?" | **WhyLine 25** (git blame + Jira matching + optional LLM rerank) · Jolli Memory 708 · Recap 150 | thin, but **not empty** |
| Test impact / run only affected | Affected 664 · NGM TestRunner 671 · Develocity 115,801 (enterprise, build-side) | thin in the IDE |
| Flaky tests | **Flaky Test Linter 70 · Flaky Test Marker Companion 12** · Develocity 115,801 | searches for `flaky test detection quarantine` → **0 results**. Essentially **empty in the IDE** |
| Risk checks on AI changes | **AI Change Radar 83** · Agentic Review 146 · ClawDEEA 710 | thin but the closest neighbours to idea A |

Existing IDE features worth naming on stage so nobody "catches" you: Annotate/Git blame, Show History
for Selection, Issue Navigation (a hyperlink regex, fetches nothing), Tasks & Contexts (points forward
only), GitHub PR tool window (no reverse lookup from a line to its discussion), Find Usages/Call
Hierarchy (no risk or coverage overlay).

### 1.4 APIs that make these ideas buildable (verified against the real platform jar)

IDE distribution: `~/.gradle/caches/9.7.1/transforms/f44560136cf9b9cf511ccab7bf51d8d2/transformed/idea-2026.2.3`
(IU 262.10968.63). `javap`/`jar tf` run with `JAVA_HOME=~/.local/tools/jdk21`.

| Need | Verified fact | Where |
|---|---|---|
| Coverage suites exist per project | `CoverageDataSuitesManager.getInstance(Project)`, `getSuites(): CoverageSuite[]` | `lib/intellij.platform.coverage.jar` |
| Line-hit data reachable | `CoverageSuite.getCoverageData(CoverageDataManager): com.intellij.rt.coverage.data.ProjectData` | same jar (+ `intellij.platform.coverage.agent.jar` bundles the instrumenting agent) |
| The IDE's own record of recent test runs/failures | `com.intellij.execution.TestStateStorage` (+ `$Record`) | `lib/intellij.platform.execution.impl.jar` |
| Intentions / quick fixes are public API | `com.intellij.codeInsight.intention.IntentionManager` | `lib/intellij.platform.analysis.jar` |
| Register new agent tools | bundled plugin `plugins/mcpserver/lib/mcpserver.jar`; toolset = class implementing `McpToolset` + one `plugin.xml` line (verified in a prior session, incl. the `isExperimental()` trap) | `plugins/mcpserver/` |
| Agent framework from the brief | Koog resolves on Maven Central: group `ai.koog`, `koog-agents` latest **1.2.0**, published 2026-08-27 | `repo1.maven.org/maven2/ai/koog/koog-agents/maven-metadata.xml` |

**Not verified** (do not claim on stage without checking): whether an IntelliJ Platform plugin can
programmatically *start* a coverage-instrumented run through public API without user interaction — that
is the one real technical risk in idea A below, and it deserves a 60-minute spike before you commit.

---

## 2. The candidates

Ranked by my judgement of expected value against the brief's own priority order (PoC → UX → innovation
→ depth). Each carries its honest weakness.

---

### A. Green Light — "your change is done" is a claim; this demands evidence

**One line.** For any uncommitted change, show the IDE-verified facts: it compiles, inspections are
clean, the tests that cover it pass *and actually execute the changed lines* — and if a changed line is
executed by nothing, say so and fix that gap.

**The pain.** AI writes the change, says "fixed, tests pass", and the human cannot cheaply check. Your
own spec's evidence base already quotes the numbers (LinearB: AI PRs wait 4.6× longer; Sonar: 96% don't
fully trust AI code). The missing artifact is not another reviewer — it is a *verdict with evidence*.

**The demo (2 min, runs live).** Agent edits 3 files → "done, tests pass". Click **Verify**. Tool
window: ✓ compiles, ✓ inspections clean, ✗ **"4 changed lines are never executed by any passing test"**
with those lines highlighted in the editor. One button runs *only* the covering tests with coverage
(9 s, not 3 min) and re-renders. Then: "generate the missing test" → the gap closes → the ledger goes
green with per-line hit counts. Closing line: *"Green tests are a claim. We ask for evidence."*

**Why it is not a prompt wrapper.** The verdict's core input — which changed lines were executed — only
exists after an instrumented run, and no IDE tool (bundled or third-party, including the 100-tool plugin
above) exposes it. Compose with `execute_run_configuration`-style runner control (depth, judged
criterion 4) and expose the same verdict as an MCP tool, so the *agent* can check itself before claiming
done — which is the 2026-shaped version of "help the developer".

**Prior art.** Closest: AI Change Radar (83) "local risk checks for AI-assisted code changes"; Agentic
Review (146); Qodo (648,649, review-side, not IDE-verified); coverage UIs (5,799 / 825) aimed at humans
browsing results, not at validating a specific diff. **No plugin found that answers "is this change
verified?" with coverage evidence.** The coverage-shaped hole is confirmed by absence from the
100-tool agent bridge.

**Build (≈6 person-days, 4 people).** A platform layer (diff + changed-line mapping via UAST, run
launch, results/coverage ingestion, background tasks with progress, fake seams for tests) + a coverage
module + a UI module (tool window + gutter markers) + an MCP toolset. Reuses the architecture discipline
you already wrote in the Unroll spec (frozen contracts, fakes, threading rules).

**Risk.** *High-value, one unverified dependency*: launching a coverage run programmatically. Spike it
first; if it fails, the fallback is to run the covering tests via a generated run configuration and read
the resulting coverage suite (the IDE writes it), or to drive the bundled agent directly. Second risk:
"isn't this just coverage?" — answer on stage: coverage tells you the whole project's state; this tells
you the *evidence for the specific change in front of you*, including the lines with zero hits.

**Rubric.** PoC 5 · UX 4.5 · Innovation 4.5 · Depth 5 · Presentation 5.

---

### B. Flake Court — prove it's flaky before you blame the code

**One line.** A test failed once. Before you touch anything: reproduce it under a differential harness,
name the exact mechanism, fix it, and prove the fix by re-running the same harness until the failure
rate is zero.

**The pain.** Flaky tests are the most-loathed thing in a developer's day (re-run until green, lose
trust in the whole suite). And the honest market read: the two IDE plugins in this lane have **70 and 12
downloads**; the serious tooling (Develocity, 115,801) is enterprise + build-side. In the IDE, this is
open ground.

**The demo (2 min, objectively undeniable).** Select the flaky test in the demo repo → **Prosecute** →
50 runs, 3 red, "2 distinct failure orders". The verdict card: *shared mutable static* (`Cache.INSTANCE`
written by another test) + `assertEquals` on an unordered `HashSet` + a `Thread.sleep` race — each with
the passing-vs-failing evidence diff. Apply the fix → re-run the same 50 → 50 green, stability report.
Unlike most demos, the judges can see the failure happen on screen.

**Why it is not a prompt wrapper.** The product *is* the differential: N instrumented runs through the
IDE's own runner, then an attribution step that only means something because of the raw red/green
evidence behind it. The AI/agent (Koog: hypothesise → propose instrumentation → re-run → conclude) is
the prosecutor; the runner is the judge. An agent with a shell can approximate this — write that down
honestly — but it pays full build+suite cost per repetition and cannot show per-run IDE results.

**Prior art.** Flaky Test Linter (70), Flaky Test Marker Companion (12), Develocity (enterprise).
`flaky test detection quarantine` → 0 marketplace results. Your nearest-"no tool does this" claim of the
six ideas, and it survives the "my agent already does this" attack better than Unroll does.

**Build (≈6 person-days).** Platform: run-control + results collection repeated N times, seeded/ordered
variants, time budget. Prosecutor: the differential engine. Localizer: heuristics (statics, order
dependence, clock/locale/random/cwd/env, unordered collections, sleeps/async, leaked threads/ports,
external I/O) + LLM ranking of candidates against the evidence. Proof: fix application + re-run
report + gutter/test-tree annotations.

**Risk.** Needs a *convincingly* flaky test in the demo repo — fine, you write it, and you should say on
stage that you wrote it (judges respect that far more than an accidental flake). Second risk: a judge
says "TeamCity already does flaky detection" — answer: detection in CI tells you *that* it happened;
this tells you *why*, locally, in the run you already have, and proves the fix.

**Rubric.** PoC 5 · UX 4 · Innovation 5 · Depth 4.5 · Presentation 5.

---

### C. Teeth — tests that can actually fail

**One line.** Generate the test, but then prove it has teeth: mutate the production code and require
your new test to fail. Report the mutants it does *not* catch as "changes your test would not notice".

**The pain.** AI-generated tests are green and worthless more often than anyone admits: they assert the
implementation's current output, including its bugs. The brief asks for exactly this ("does it catch
edge cases a generic prompt would miss") — and there is now literature behind the failure mode:
*"Auditing and Decomposing Feedback-Driven Evolution in LLM Test Generation under the Oracle Problem"*
(arXiv 2608.19626, 2026-08-20) shows generated tests validated against a single accepted program
"create spurious fault detections and apparent evolutionary gains"; *"Evaluating the effectiveness of
class-level LLM-generated test suites in Python"* (arXiv, 2026-09-21). High coverage, weak fault
detection. That is a deck slide with a citation.

**The demo (2 min).** Ask for a test for `esc()` → it matches the repo's conventions (AssertJ vs JUnit,
naming, fixture style — mined from existing tests, which is the "real codebase context" the brief asks
for) → runs green → now **mutate**: flip the boolean, change the boundary, return early. The test still
passes → *"this test would not notice if the code were wrong."* Regenerate with the failures as
feedback → the mutant dies → "kill score 5/6", and the surviving mutant is shown as the known blind spot.

**Prior art.** The category is the crowded one: Diffblue 210,893 · Machinet 116,030 · TestSpark 8,780 ·
Qodo 648,649; mutation *running* exists (PIT 99,627, Mutation Tester 3,099). The combination — AI test ×
IDE-applied mutation × kill score as the *acceptance criterion* — is not a single shipped flow. You will
be competing with six vendors' slides in a lane the judges prompted for, so you must lead with the teeth
check, not with "we generate tests".

**Build (≈6 person-days).** Convention miner (test sources → style profile) + generation + execution
loop + a small mutation engine (IDE-applied semantic mutations via PSI, not PIT's bytecode — cheaper,
faster, demo-friendly) + result UI.

**Risk.** Mutation engines are easy to get wrong in a way that embarrasses (compile errors per mutant).
Keep the mutant set tiny and curated for the demo (6 mutants, one class).

**Rubric.** PoC 4.5 · UX 4 · Innovation 3.5 (expected input, novel angle) · Depth 5 · Presentation 4.5.

---

### D. Only What Matters — run 14 tests instead of 812

**One line.** Given your change, compute the tests that can possibly be affected using the IDE's own
reference graph + the last coverage data, run only those, and show the time saved and the confidence
rule.

**Demo (90 s).** Change one method → "812 tests → 14 selected (9 s vs 3 min 10 s)" → run → jump to the
one failure. Then deliberately edit a reflection/DI-heavy path and show the **fallback rule** firing:
"graph incomplete → running the full suite" (this *sells* the honesty, and it is the part a judge will
probe).

**Prior art.** Affected (664), NGM TestRunner (671), Develocity predictive test selection (enterprise).
Thin in the IDE. **Why it ranks below A/B:** its wow is arithmetic on a stopwatch, which is strong on
PoC but weaker on "originality of the problem"; and correctness arguments eat demo time.

**Build (≈5 person-days).** Changed-symbol set → inverse reference walk (`ReferencesSearch`/hierarchy)
→ test source mapping → over-approx selection + fallback; UI = one button + a selection report.

**Rubric.** PoC 4.5 · UX 4.5 · Innovation 3.5 · Depth 4.5 · Presentation 4.

---

### E. Definition of Done — requirement-level coverage

**One line.** Paste/link an issue; the plugin maps each acceptance criterion to the code that implements
it and the test that asserts it, and shows the matrix: implemented+tested / implemented+untested /
not found.

**Why it is interesting.** Line coverage is a proxy; *requirement* coverage is what a lead actually
wants, and it is genuinely un-served (nearest: Supersigil 141, Qodo review-side). It also reuses the
GitHub/issue plumbing you already built on the Unroll branch — the branch
`feature/issue#7_setup-unroll-to-github-api` and its plan already resolve commits → PRs → linked issues
against the live GitHub API.

**Why it ranks lower.** Semantic matching between prose criteria and code is the fuzziest core of the
six; a partly-wrong matrix is worse than no matrix in a live demo, and you cannot prove correctness on
stage in 2 minutes. Doable only with a tightly curated demo issue.

**Rubric.** PoC 3.5 · UX 3.5 · Innovation 5 · Depth 4.5 · Presentation 4.

---

## 3. Unroll (the incumbent) — honest verdict

You already have a 1,411-line spec, a repo, a 4-person split, and a branch for GitHub enrichment. That
is real sunk value; do not throw it away lightly. But three findings should change what you say on stage,
and they cost you nothing to fix.

1. **"No IDE gives you this today" is half-true — fix the wording.** Verified: **WhyLine**
   (`plugin/31638`, **25 downloads**) does "why did this code change?" inside the IDE — git blame/commits/
   diffs matched against Jira with a multi-signal scorer, plus an optional OpenAI rerank. What it does
   *not* do: PR/review threads, rejected alternatives, what happened to the code since. Say exactly that.
   Judges reward the team that names the prior art and differentiates in one sentence; they punish the
   team that claims a vacuum and gets caught.
2. **The "my agent already does this" attack is the real threat.** By the moat test — *can an agent get
   this another way?* — git log/blame, PR fetch and reading tests are all reachable from a terminal, so
   all three actions are Tier 3/2 on the "data that only exists inside the IDE" scale. The IDE's built-in
   MCP server already hands an agent call-hierarchy (`analyze_calls`) and file history. Two upgrades move
   you to Tier 1–2 and are *cheap*:
   - **Alive** — don't paste prose "input→output examples": **execute** the covering test through the
     IDE's runner and show the literal values from the run (and the assertion that pins them). Real
     execution is JDK-instrumented data, not a summarised file.
   - **Blast Radius** — use the IDE's call hierarchy + **last coverage data** (verified reachable:
     `CoverageSuite.getCoverageData(...)`, see §1.4) to mark *changed-but-unexecuted* callers, and
     Local History for churn instead of only git.
3. **AI is optional in the design; the brief wants an AI solution.** "Synthesis optional, off by
   default" is the right call for PoC determinism — keep the deterministic core — but pull at least one
   AI step into the *mandatory* loop (e.g. the ranking of which evidence matters for this line), or a
   judge applying criterion 3 will bucket it as "git blame with an LLM garnish".

**Cheapest differentiation that no agent can copy:** add a `verify_change` MCP tool to the same plugin,
so the *agent that wrote the code* can ask the IDE "why is this here / what breaks if I change it"
through your implementation. You would be the third plugin to extend the MCP server (7.5k-download one
uses the idea; the official Inventor's version of the surface is absent for graph+coverage reasoning).
This converts Unroll from "a nicer viewer for a human" into "the IDE's answer service for agents" — the
2026-shaped framing of "help the developer".

**Where Unroll still wins:** UX and presentation. Three joined questions, one right-click, demoing on
its own repository history. If your team's strength is polish and storytelling rather than
instrumentation, Unroll-with-the-three-fixes is a legitimate path to a top finish.

---

## 4. Ranked shortlist and a decision filter

| # | Idea | PoC | UX | Innov | Depth | Prior art pressure | Main risk |
|---|---|---|---|---|---|---|---|
| 1 | **A. Green Light** (change-verified-by-coverage) | 5 | 4.5 | 4.5 | 5 | low–medium (coverage absent from agent tools; AI Change Radar 83) | launching a coverage run programmatically — spike first |
| 2 | **B. Flake Court** (prove + fix + prove) | 5 | 4 | 5 | 4.5 | **lowest** (70 / 12 downloads, 0 search hits) | needs a convincing flaky test (you write it) |
| 3 | **C. Teeth** (AI tests that can fail) | 4.5 | 4 | 3.5 | 5 | high (6 vendors, PIT) | judged as "another test generator" |
| 4 | **D. Only What Matters** (test impact) | 4.5 | 4.5 | 3.5 | 4.5 | low in IDE, enterprise above | correctness story eats demo time |
| 5 | **E. Definition of Done** (requirement coverage) | 3.5 | 3.5 | 5 | 4.5 | low | fuzzy core, curated demo needed |
| — | **Unroll (hardened)** | 4.5 | 5 | 3.5→4.5 | 3.5→4.5 | WhyLine 25 (weak) | "my agent does this already" |

**Pick with three questions, in this order:**

1. *Which one can you make undeniable with the artifact you can actually produce by Sunday morning?* A
   demo repo with a genuinely flaky test makes B undeniable; a repo with a real coverage gap makes A
   undeniable. Choose the one whose evidence you can manufacture honestly in an hour.
2. *Does the AI sit where the leverage is?* In A and B the AI explains, ranks and proposes, while the
   IDE decides — that reads as taste and it satisfies both "AI solution" and "not a prompt wrapper".
3. *Can one person own the risky dependency and spike it in 60 minutes?* A: the programmatic coverage
   run. B: driving N runner invocations + collecting structured results (`TestStateStorage` exists —
   `lib/intellij.platform.execution.impl.jar`). C: compile-safe PSI mutations. Whoever owns it spikes
   first, before anyone writes UI.

If I had to pick one for a JetBrains-judged room on this brief: **A**, with **B** as the higher-originality
alternative if you want the most defensible "nobody has built this well" claim. If the team is already
deep in Unroll and polish is your edge: **keep Unroll, apply the three fixes in §3** — and open the deck
with the WhyLine comparison, because naming the 25-download plugin that tried the same thing is the
single most credibility-generating sentence available to you.

---

## 5. Sources (all checked 2026-09-22)

- Brief: `~/Downloads/Help the Developer.docx` (extracted text; 36 paragraphs).
- MCP Server toolset list & tool docs: `https://www.jetbrains.com/help/idea/mcp-server.html` (help version 2026.2).
- Marketplace figures: `https://plugins.jetbrains.com/api/searchPlugins?search=…&max=N` and
  `/api/plugins/{id}` for **31638** (WhyLine, 25) and **31060** (MCP Server AI Companion, 7,571, free).
- Platform APIs: `jar tf` / `javap` against
  `~/.gradle/caches/9.7.1/transforms/f44560136cf9b9cf511ccab7bf51d8d2/transformed/idea-2026.2.3` (IU 262.10968.63).
- Koog availability: `https://repo1.maven.org/maven2/ai/koog/koog-agents/maven-metadata.xml` → latest **1.2.0** (2026-08-27).
- arXiv: **2608.19626** "Auditing and Decomposing Feedback-Driven Evolution in LLM Test Generation under the
  Oracle Problem" (2026-08-20); "Evaluating the effectiveness of class-level LLM-generated test suites in
  Python" (2026-09-21, id from the same query — confirm before citing the number).
- Your existing plan: `~/Desktop/demo/specification.md` (Unroll, 1,411 lines) and
  `~/Desktop/demo/.agents/plan-issue-7-github-api.md`.
