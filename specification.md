# Unroll — IntelliJ Plugin
### 42 Abu Dhabi × JetBrains Hackathon · 22–23 September 2026

> **Mission:** Help the developer.
> **Our answer:** We write code once but read it forever. Now that AI writes most of it, reading *is* the job. Unroll makes the IDE answer the three questions you actually have about a line of code.
>
> **And we demo it on itself.** The plugin reads the history of the repository that produced it.

---

## 0. How to use this document

| Audience | Read |
|---|---|
| Team members | §1–§4, then your own component spec in §9, then §14–§16. **§4 is mandatory for everyone — it governs how you commit all weekend.** |
| AI coding agent | §5–§13 are the build contract. §7 (shared contracts) is frozen API — do not invent types not listed there. |
| Whoever builds the deck | §2, §3, §15, §16 contain everything needed, including numbers and sources |

**Two frozen rules:**
1. §7 is the integration contract between four people working in parallel. Nobody changes a signature there without telling the other three.
2. §4 is the demo. Every commit you make this weekend is demo material. Break the protocol and there is nothing to show.

---

## 1. Executive summary

**Unroll** is an IntelliJ IDEA plugin. Right-click any line of code and get three answers no IDE gives you today:

| Action | Question it answers | Shortcut |
|---|---|---|
| **Unroll** | *Why is this here?* — the issue, the PR debate, the rejected alternatives, and what happened to this code since | `Ctrl+Alt+U` |
| **Alive** | *What does it actually do?* — real input→output examples pulled from the tests that cover it | `Ctrl+Alt+L` |
| **Blast Radius** | *What breaks if I change it?* — every caller, which ones have no test, and how volatile this code has been | `Ctrl+Alt+B` |

All three are **mandatory**. A fourth capability, **Synthesis** (an AI-written four-line answer above the Unroll timeline), is **optional, off by default, and provider-agnostic** — bring any OpenAI-compatible or Anthropic-compatible endpoint and key.

Works on **Java and Kotlin**, via UAST.

Understand → verify → change safely. Thirty seconds, without leaving the editor.

---

## 2. The problem (evidence base for the pitch)

### 2.1 The one-sentence version

Git blame tells you *who* touched a line. Nothing tells you *why* it exists, *what it does*, or *what it will take down with it*.

### 2.2 What developers actually spend their time on

| Finding | Source |
|---|---|
| AI-generated PRs sit **4.6× longer** waiting for review; agentic PRs have **5.3× longer** pickup time | LinearB, 2026 Software Engineering Benchmarks |
| **38%** say reviewing AI code takes more effort than human code; **96%** don't fully trust it | Sonar, 2026 |
| Reviewers are forced to "**reverse-engineer intent from noise**" — diffs cannot convey historical decisions, acceptance criteria, or architectural conventions | *Rethinking Code Review in the Age of AI*, arXiv 2605.17548 |
| **34%** of GitHub PR descriptions are **empty** | empirical study cited in the above |
| Developers spend **20–50%** of working hours debugging; the hard part is data flow, not setting breakpoints | bugstack, 2026 |
| Most common complaint about AI output: "**almost right, but not quite**" | Stack Overflow Developer Survey 2025 |
| Agents' most frequent real defects: missing authorization on new endpoints, missing input validation, silently duplicating existing code | vibe-coding field reports, 2025–26 |
| The most-recommended way to understand unfamiliar code is **reading its tests** — yet no tool makes that one keystroke | recurring across onboarding literature |

**Sources**
- https://www.builder.io/blog/developers-drowning-in-ai-prs
- https://arxiv.org/pdf/2605.17548
- https://blog.codacy.com/ai-breaking-code-review-how-engineering-teams-survive-pr-bottleneck
- https://engineering.salesforce.com/scaling-code-reviews-adapting-to-a-surge-in-ai-generated-code/
- https://bugstack.ai/blog/developer-debugging-time-2026
- https://dev.to/lostintangent/what-are-the-biggest-challenges-when-onboarding-to-a-new-codebase-project-35o4
- https://en.wikipedia.org/wiki/Vibe_coding

### 2.3 What IntelliJ gives you today (be honest about this on stage)

| Feature | What it actually does | What it does NOT do |
|---|---|---|
| **Annotate / Git Blame** | Author, date, revision hash; tooltip shows the commit message | Says nothing about the ticket, the discussion, or the reasoning |
| **Issue Navigation** | A regex that turns `ABC-123` into a **hyperlink** | Fetches nothing. String matching plus a URL template. Opens a browser. |
| **Tasks & Contexts** | *Can* fetch issue summaries — but points **forward** (pick a ticket, create a branch) | Plays no part in reading history |
| **GitHub PR tool window** | Browse PRs and their review threads | No reverse lookup from a line of code to the discussion that produced it |
| **Find Usages / Call Hierarchy** | A tree of callers | No risk judgement, no test-coverage overlay, no churn signal |

Four half-features. Nobody joins them up. **That gap is the product.**

---

## 3. The solution

### 3.1 The flow

```
                right-click on a line / selection / function
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        │                         │                         │
    ▸ Unroll                  ▸ Alive                 ▸ Blast Radius
   why is this here?      what does it do?         what breaks if I change it?
        │                         │                         │
  commits + PRs +           tests covering it →       callers + coverage gaps
  review threads +          literal input→output      + risk flags + churn
  follow-up fixes           examples
        │                         │                         │
  [Continue → native        [Run these → native        [Run all → native
   commit diff view]         JUnit runner]              JUnit runner]
        │
  (optional) Synthesis: 4-line answer from any LLM endpoint
```

**Design principle — delegate, don't rebuild.** We do not build a diff viewer (IntelliJ has one). We do not build a test runner (IntelliJ has one). We build the context that is missing and put it in front of both. Say this on stage; it reads as taste, not as a gap.

### 3.2 Action 1 — Unroll

```
esc()  ·  5 commits touched this block

  ▸ 7c1a94   Sun 11:20   "html: stop double-escaping already-escaped entities"
      PR #31 · Issue #29 "Review comments render as &amp;amp;"
      Review · 4 comments
        @teammate: "why not just skip esc() in the renderer?"
        @author:   "can't — B and D both call it with raw API text"
      → rejected: skipping escape at the call site

  ▸ 4fd022   Sat 19:05   "html: escape quotes for attribute contexts   Refs #22"
  ▸ b90c17   Sat 16:41   "reformat"                          (no semantic change — dimmed)
  ▸ 2ea885   Sat 12:10   "html: handle null and empty        Fixes #14"
  ▸ 0a33c1   Sat 10:47   "html: initial escaping helper      Refs #9"

                                                        [ Continue → diff ]
```

Three things no existing tool gives you: **rejected alternatives** from the review thread, the **whole timeline** rather than one commit, and the **follow-up commits** that tell you this code was already patched once.

### 3.3 Action 2 — Alive

```
esc()  ·  7 tests describe this

  escapes ampersand                ("a & b")        →  "a &amp; b"
  escapes angle brackets           ("<script>")     →  "&lt;script&gt;"
  escapes double quotes            ("say \"hi\"")   →  "say &quot;hi&quot;"
  leaves plain text untouched      ("hello")        →  "hello"
  handles empty string             ("")             →  ""
  handles null                     (null)           →  ""
  does not double-escape           ("&amp;")        →  "&amp;"

                                                        [ Run these ▸ ]
```

**Key engineering decision: we do not execute anything to get these values. We extract the assertions.** An assertion *is* an input→output pair, already written, already literal. Pure UAST. Instant, deterministic, no coverage engine, no instrumentation.

When it returns nothing, *"no tests describe this"* is itself the answer the developer needed.

### 3.4 Action 3 — Blast Radius

```
esc()  ·  11 callers across 4 packages

  ⚠  3 callers have no test coverage
       BlastRenderer.renderCaller()        ← cross-package
       UnrollRenderer.renderComment()      ← cross-package
       AliveRenderer.renderRow()           ← cross-package
  ⚠  changed 5× in the last 2 days, once tagged "fix double-escaping"
  ✓  8 callers covered by 7 tests                        [ Run all ▸ ]
```

The money line is the **cross-reference**: *these callers depend on you and nothing will catch you if you break them.* Find Usages cannot say that.

### 3.5 Optional — Synthesis (any provider)

Off by default. When an endpoint and key are configured, a four-field header appears above the timeline once the answer arrives. The raw timeline renders immediately and never waits for it.

```
  Why this exists:  shared HTML escaping for all three renderers.
  Rejected:         skipping escape at the call site — B and D pass raw API text.
  Since then:       patched for quotes, then for double-escaping (#29).
  Watch out:        callers assume it is null-safe. It only became so in 2ea885.
```

Provider-agnostic by design — see §9.5. Preset dropdown (OpenAI · OpenRouter · Groq · Together · Ollama · Anthropic · Custom), paste a key, pick a model. No vendor SDK, no bundled dependencies.

---

## 4. The demo repository is **our own repo**

This is a design constraint on the whole weekend, not a detail. On Sunday we right-click our own plugin code and Unroll explains it. That only works if the history we produce contains something worth reading.

**Why this beats an external repo**
- No clone, no multi-minute indexing, no rate limits on a small repo
- Every artifact on screen is unambiguously real and ours
- The closing line writes itself: *"we built this in two days — and this is it, reading its own history"*
- Zero dependence on someone else's repository being shaped the way we hoped

**The cost:** we have to work like a real team for two days. That is the protocol below.

### 4.1 Repository setup (Person A, Saturday 09:00, before anything else)

- Public GitHub repo `unroll-intellij` under one account, all four as collaborators
- **Branch protection on `main`: no direct pushes.** Everything goes through a PR.
- All four generate a personal access token (`public_repo` scope) and configure it in the plugin's settings as soon as settings exist
- Issue labels: `platform`, `unroll`, `alive`, `blast`, `demo-target`

### 4.2 The protocol — non-negotiable

| Rule | Why it matters for the demo |
|---|---|
| **One GitHub issue per work item** — write them all at 09:00 Saturday, ~15 of them | Unroll's issue panel is empty without them |
| **Branch per issue, PR per branch.** `feat/9-html-escaping` | `git log -L` → commit → PR is the entire Unroll chain |
| **Every commit message ends with `Refs #N` or `Fixes #N`** | This is how commits resolve to issues. A commit without one is invisible to Unroll. |
| **Every PR gets one reviewer, who must leave a substantive comment** — a question or a challenge, never "LGTM" | The review thread *is* the "why". No comments, no demo. |
| **When a reviewer proposes an alternative, the author replies explaining why not** | This produces the "Rejected" line, which is our single best moment on stage |
| **When you fix your own bug, say so: `fix double-escaping regression from 4fd022`** | This produces the "Since then" signal |
| **Every PR that changes behaviour adds a test with literal assertions** | These become Alive's rows |
| **Run one pure reformat commit on Saturday afternoon and say `reformat` in the subject** | Proves the dimmed non-semantic-commit feature works |

Overhead: roughly 20 minutes per person per day. It buys the entire demo.

### 4.3 The demo target — `Html.esc()`

We designate **one function** early and route real work through it. Primary: `Html.esc(String?): String` in `ui/Html.kt`.

Why it is the right choice:
- **Pure** — a string in, a string out. Perfect literal assertions for Alive.
- **Called by all three renderers** (B, C and D) — real cross-package callers for Blast Radius, and some will genuinely lack tests
- **Genuinely evolves** — escaping is exactly the kind of function that accumulates edge cases over two days
- Owned by A, but *changed* by whoever hits the edge case — so the history has multiple authors, which looks right

**Planned PR arc** (these are real changes we actually need — we are sequencing them, not faking them):

| # | Issue | Change | Produces |
|---|---|---|---|
| 1 | #9 | initial `esc()` — `&`, `<`, `>` | the origin commit |
| 2 | #14 | null / empty handling — B hits an NPE rendering an empty PR body | a `Fixes #14` commit with a real cause |
| 3 | #22 | escape `"` — D needs it for an attribute context | a second author touching the same lines |
| 4 | — | reformat pass across `ui/` | the dimmed non-semantic commit |
| 5 | #29 | double-escaping bug — review argues for skipping `esc()` at the call site, author explains why not | **the rejected alternative + the regression fix** |

Backup target if `esc()` somehow stays trivial: `IssueRefs.extract(String)` in `history/` — also pure, also literal-testable, and it will genuinely churn (bare `#123`, `Fixes #12`, full URLs, false positives, dedupe).

Record the locked target here once PR #1 merges:

```
DEMO TARGET
  file:     src/main/kotlin/dev/unroll/ui/Html.kt
  function: esc
  issues:   #9 #14 #22 #29
  as of:    <commit sha on Sunday morning>
```

### 4.4 The historian (Person A)

At **Saturday 18:00** and **Sunday 09:00**, A runs all three actions on the demo target and asks:

- [ ] Does Unroll show ≥4 commits, ≥2 with a linked issue?
- [ ] Is there at least one review comment containing a real question?
- [ ] Is there at least one rejected alternative visible in a thread?
- [ ] Is there a follow-up commit that names an earlier one?
- [ ] Does Alive show ≥5 rows with correct literal values?
- [ ] Does Blast Radius show ≥8 callers with ≥2 uncovered?

Any box unticked → A says so in the group chat and we deliberately create the missing artifact through real work in the next PR. **Do not fabricate comments after the fact.** Judges can read a repo, and a staged thread reads as staged.

---

## 5. Tech stack & project setup

| Concern | Choice | Why |
|---|---|---|
| Language | **Kotlin** | Our source *is* the demo target, and Kotlin is what we write fastest |
| Build | **Gradle** + IntelliJ Platform Gradle Plugin **2.x** | Current supported path |
| Target IDE | IntelliJ IDEA **Community 2025.2** (`sinceBuild 252`) | Judges will run Community |
| Code analysis | **UAST** (`org.jetbrains.uast`) over Java + Kotlin PSI | One code path, both languages — and it is the only way to analyse our own Kotlin source |
| Git | `Git4Idea` for repo discovery; `GeneralCommandLine` for `git log -L` | Shelling out is predictable and testable |
| HTTP | JDK `java.net.http.HttpClient` | Zero dependencies, no classpath conflicts |
| JSON | `com.google.gson:gson` | Small, no conflicts |
| LLM (optional) | **Generic HTTP provider, no vendor SDK** | Plug in any OpenAI-compatible or Anthropic-compatible endpoint. See §9.5. |
| UI | `JEditorPane` (`text/html`) inside `JBPopup` | Fastest path to a good-looking popup |

> **"Works on Java and Kotlin" is a pitch point, not just plumbing.** At a JetBrains event, a Java-only plugin looks unfinished. UAST costs us roughly half a day in Person C's component and buys a line on stage.

### 5.1 `build.gradle.kts`

```kotlin
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "dev.unroll"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.2")
        bundledPlugin("com.intellij.java")          // Java PSI + Java UAST
        bundledPlugin("org.jetbrains.kotlin")       // Kotlin PSI + Kotlin UAST
        bundledPlugin("Git4Idea")
        bundledPlugin("JUnit")                      // "Run these" handoff
    }
    implementation("com.google.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
}

kotlin { jvmToolchain(21) }

intellijPlatform {
    pluginConfiguration { ideaVersion { sinceBuild = "252" } }
}
```

No vendor SDKs. The only runtime dependency we ship is Gson.

### 5.2 `src/main/resources/META-INF/plugin.xml`

```xml
<idea-plugin>
    <id>dev.unroll.intellij</id>
    <name>Unroll</name>
    <vendor>Team Unroll — 42 Abu Dhabi</vendor>

    <description><![CDATA[
      Why is this line here? What does it actually do? What breaks if I change it?
      Unroll answers all three without leaving the editor. Java and Kotlin.
    ]]></description>

    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.java</depends>
    <depends>org.jetbrains.kotlin</depends>
    <depends>Git4Idea</depends>

    <extensions defaultExtensionNs="com.intellij">
        <applicationService serviceImplementation="dev.unroll.settings.UnrollSettings"/>
        <applicationConfigurable parentId="tools"
            instance="dev.unroll.settings.UnrollConfigurable"
            id="dev.unroll.settings" displayName="Unroll"/>

        <projectService serviceInterface="dev.unroll.api.HistoryService"
                        serviceImplementation="dev.unroll.history.GitHistoryService"/>
        <projectService serviceInterface="dev.unroll.api.ReferenceIndexService"
                        serviceImplementation="dev.unroll.alive.UastReferenceIndexService"/>
        <projectService serviceInterface="dev.unroll.api.AliveService"
                        serviceImplementation="dev.unroll.alive.AssertionAliveService"/>
        <projectService serviceInterface="dev.unroll.api.BlastRadiusService"
                        serviceImplementation="dev.unroll.blast.UastBlastRadiusService"/>
        <projectService serviceInterface="dev.unroll.api.ChurnService"
                        serviceImplementation="dev.unroll.blast.GitChurnService"/>
        <projectService serviceInterface="dev.unroll.api.SynthesisService"
                        serviceImplementation="dev.unroll.synthesis.LlmSynthesisService"/>
    </extensions>

    <actions>
        <group id="Unroll.Group" text="Unroll" popup="true">
            <add-to-group group-id="EditorPopupMenu" anchor="first"/>
            <action id="Unroll.History" class="dev.unroll.action.UnrollAction"
                    text="Unroll — Why Is This Here?">
                <keyboard-shortcut first-keystroke="ctrl alt U" keymap="$default"/>
            </action>
            <action id="Unroll.Alive" class="dev.unroll.action.AliveAction"
                    text="Alive — What Does It Do?">
                <keyboard-shortcut first-keystroke="ctrl alt L" keymap="$default"/>
            </action>
            <action id="Unroll.BlastRadius" class="dev.unroll.action.BlastRadiusAction"
                    text="Blast Radius — What Breaks?">
                <keyboard-shortcut first-keystroke="ctrl alt B" keymap="$default"/>
            </action>
        </group>
    </actions>
</idea-plugin>
```

---

## 6. Architecture & package layout

```
src/main/kotlin/dev/unroll/
├── api/                         ← §7 FROZEN CONTRACTS (Person A owns)
│   ├── Model.kt
│   ├── Services.kt
│   └── Fakes.kt
├── action/                      ← Person A
│   ├── UnrollAction.kt  AliveAction.kt  BlastRadiusAction.kt
│   └── CodeTargetResolver.kt
├── ui/                          ← Person A   ★ contains the demo target
│   ├── UnrollPopup.kt
│   └── Html.kt                  ★ Html.esc() — see §4.3
├── settings/                    ← Person A
│   ├── UnrollSettings.kt  UnrollConfigurable.kt
├── cache/                       ← Person A
│   └── DiskCache.kt
├── history/                     ← Person B
│   ├── GitHistoryService.kt  GitLog.kt  IssueRefs.kt
│   ├── GitHubClient.kt
│   └── UnrollRenderer.kt
├── alive/                       ← Person C
│   ├── UastReferenceIndexService.kt   (shared with D)
│   ├── AssertionAliveService.kt  AssertionExtractor.kt
│   └── AliveRenderer.kt
├── blast/                       ← Person D
│   ├── UastBlastRadiusService.kt  RiskFlagger.kt
│   ├── GitChurnService.kt
│   └── BlastRenderer.kt
└── synthesis/                   ← Person A (optional)
    ├── LlmProvider.kt           provider interface + two adapters
    └── LlmSynthesisService.kt
```

**You create files only inside your own directory, plus your renderer.** The only shared files are in `api/`, and only A edits them.

---

## 7. Shared contracts — FROZEN

Person A writes this in the first 45 minutes and opens PR #1. Everyone codes against it.

Note the UAST choice shows up here: we carry **`PsiElement` pointers**, never `PsiMethod`, and resolve to `UMethod` at the point of use. That is what lets the same code path serve Java and Kotlin.

### 7.1 `api/Model.kt`

```kotlin
package dev.unroll.api

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import java.time.Instant

/** What the user right-clicked on, normalised. Language-agnostic. */
data class CodeTarget(
    val virtualFile: VirtualFile,
    val repoRelativePath: String,
    val repoRoot: VirtualFile,
    val startLine: Int,                 // 1-based, inclusive
    val endLine: Int,                   // 1-based, inclusive
    val functionPointer: SmartPsiElementPointer<PsiElement>?,  // resolves to a UMethod
    val functionName: String?,          // "esc"
    val containerFqn: String?,          // "dev.unroll.ui.Html"
    val displayName: String,            // "esc()" or "Html.kt:12–20"
    val fromSelection: Boolean          // true = user selected a range explicitly
)

// Selection contract (see §9.1.1):
//  · startLine..endLine is ALWAYS valid and clamped to the document — Unroll uses it directly.
//  · functionPointer is the ONE function the action operates on. When a selection spans
//    several functions, the resolver asks the user which one BEFORE building this object,
//    so Alive and Blast Radius never have to deal with ambiguity.
//  · functionPointer == null means the range covers no function at all (imports, fields,
//    a blank block). Unroll still works; Alive and Blast Radius disable themselves.

// ─────────────────────────── Unroll (Person B) ───────────────────────────

enum class RefKind { ISSUE, PULL_REQUEST }

data class IssueRef(val kind: RefKind, val number: Int, val url: String)

data class CommitInfo(
    val sha: String,
    val shortSha: String,
    val author: String,
    val date: Instant,
    val subject: String,
    val body: String,
    val isMerge: Boolean,
    val isSemantic: Boolean,            // false = whitespace/format-only for this file
    val refs: List<IssueRef>
)

data class Comment(
    val author: String,
    val body: String,
    val createdAt: Instant,
    val inlineOn: String? = null        // "Html.kt:14" for inline review comments
)

data class PullRequestInfo(
    val number: Int,
    val title: String,
    val body: String,
    val url: String,
    val author: String,
    val reviewComments: List<Comment>,  // inline code review
    val conversation: List<Comment>     // PR conversation tab
)

data class IssueInfo(
    val number: Int,
    val title: String,
    val body: String,
    val url: String,
    val comments: List<Comment>
)

data class EnrichedCommit(
    val commit: CommitInfo,
    val pullRequest: PullRequestInfo?,  // null when committed directly
    val issues: List<IssueInfo>
)

data class Synthesis(
    val whyExists: String,
    val rejected: String,
    val sinceThen: String,
    val watchOut: String
)

data class UnrollReport(
    val target: CodeTarget,
    val commits: List<EnrichedCommit>,  // newest first
    val truncated: Boolean,
    val synthesis: Synthesis? = null,   // filled in asynchronously, may stay null
    val warnings: List<String> = emptyList()
)

// ─────────────────────────── Alive (Person C) ───────────────────────────

enum class ExampleKind { VALUE, EXCEPTION, BOOLEAN, NULLCHECK, UNKNOWN }

data class TestExample(
    val testFunctionName: String,       // "escapes ampersand"  (Kotlin backticks) or "escapesAmpersand"
    val humanName: String,              // "escapes ampersand"
    val args: List<String>,             // ["\"a & b\""]
    val expected: String,               // "\"a &amp; b\""
    val kind: ExampleKind,
    val parameterised: Boolean,
    val navigateTo: SmartPsiElementPointer<PsiElement>?
)

data class AliveReport(
    val target: CodeTarget,
    val examples: List<TestExample>,
    val testFunctionCount: Int,
    val unparsedCount: Int,
    val testClassPointers: List<SmartPsiElementPointer<PsiElement>>
)

// ────────────────────── Blast Radius (Person D) ──────────────────────

enum class RiskFlag { NO_TEST, SCHEDULED, ENTRY_POINT, ASYNC, PUBLIC_API, CROSS_MODULE }

data class CallerInfo(
    val displayName: String,            // "BlastRenderer.renderCaller()"
    val module: String?,
    val filePath: String,
    val line: Int,
    val coveredByTest: Boolean,
    val flags: Set<RiskFlag>,
    val navigateTo: SmartPsiElementPointer<PsiElement>?
)

data class ChurnInfo(
    val windowDays: Int,
    val changeCount: Int,
    val regressionSubjects: List<String>
)

data class BlastRadiusReport(
    val target: CodeTarget,
    val callers: List<CallerInfo>,      // risk-sorted, most dangerous first
    val moduleCount: Int,
    val uncoveredCount: Int,
    val truncated: Boolean,
    val churn: ChurnInfo,
    val testFunctionPointers: List<SmartPsiElementPointer<PsiElement>>
)
```

### 7.2 `api/Services.kt`

```kotlin
package dev.unroll.api

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.uast.UMethod

interface HistoryService {
    /** Background thread only. Must not touch the EDT. */
    fun unroll(target: CodeTarget, indicator: ProgressIndicator): UnrollReport
}

interface ReferenceIndexService {
    /** Callers in production sources. Java and Kotlin. Call inside a read action. */
    fun findCallers(method: UMethod): List<UMethod>
    /** Callers in test sources. */
    fun findTestCallers(method: UMethod): List<UMethod>
}

interface AliveService {
    fun analyse(target: CodeTarget, indicator: ProgressIndicator): AliveReport
}

interface BlastRadiusService {
    fun analyse(target: CodeTarget, indicator: ProgressIndicator): BlastRadiusReport
}

interface ChurnService {
    fun churn(target: CodeTarget, windowDays: Int): ChurnInfo
}

interface SynthesisService {
    /** Null when disabled, unconfigured, or on any failure. Never throws. */
    fun synthesise(report: UnrollReport, indicator: ProgressIndicator): Synthesis?
}
```

### 7.3 `api/Fakes.kt` — the unblocker

A ships these in the first hour so **B, C and D can build and demo their UI before anyone's real service exists.** Selected when `UnrollSettings.useFakeServices == true`. Keep them to the end — they are also the parachute if the venue network dies.

```kotlin
object FakeHistoryService : HistoryService          { /* returns the §3.2 example */ }
object FakeReferenceIndexService : ReferenceIndexService { /* 3 hardcoded UMethods */ }
object FakeAliveService : AliveService              { /* returns the §3.3 example */ }
object FakeBlastRadiusService : BlastRadiusService  { /* returns the §3.4 example */ }
```

---

## 8. Team split

| Person | Component | Owns | Depends on | Unblocked by |
|---|---|---|---|---|
| **A** | **Platform + Synthesis + historian** | `api/`, `action/`, `ui/`, `settings/`, `cache/`, `synthesis/` | nobody | — |
| **B** | **Unroll** | `history/` | A's contracts | A merges PR #1 by 10:00 |
| **C** | **Alive (+ the shared UAST reference layer)** | `alive/` | A's contracts | same |
| **D** | **Blast Radius** | `blast/` | A's contracts + **C's `ReferenceIndexService`** | `FakeReferenceIndexService` until C lands it |

**The one cross-dependency:** D needs `findCallers` / `findTestCallers`, which C owns. A ships a fake at 10:00; **C must merge the real one by 16:00 Saturday** (an hour later than a Java-only build would need, because UAST is the harder path) and announce it. D builds risk flags, churn and rendering against the fake until then.

**A is also the historian** (§4.4). That role is real work — budget for it.

---

## 9. Component specifications

### 9.1 Person A — Platform

#### 9.1.1 `CodeTargetResolver`

**Multiline selections are a first-class input, not an edge case.** Selecting a block and asking
"why is *this* here?" is the most natural way to use Unroll. The resolver therefore always produces
a valid line range, and separately resolves *which function* (if any) Alive and Blast Radius apply to.

Two entry points, because one of them may need to ask the user something:

```kotlin
object CodeTargetResolver {
    /** Cheap, read-action only, never shows UI. Used by AnAction.update(). */
    fun peek(e: AnActionEvent): CodeTarget?
    /** May show a chooser popup on the EDT. Used by actionPerformed(). */
    fun resolveForAction(e: AnActionEvent, requiresFunction: Boolean): CodeTarget?
}
```

```
1. editor  = e.getData(CommonDataKeys.EDITOR)   ?: return null
   psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return null
   document = editor.document

2. repoRoot = GitRepositoryManager.getInstance(project)
                .getRepositoryForFileQuick(psiFile.virtualFile)?.root ?: return null
   repoRelativePath = VfsUtilCore.getRelativePath(psiFile.virtualFile, repoRoot) ?: return null

3. LINE RANGE  ── always produced, always valid
   val sel = editor.selectionModel
   if (sel.hasSelection()) {
       var start = document.getLineNumber(sel.selectionStart) + 1
       var end   = document.getLineNumber(sel.selectionEnd) + 1
       // ★ OFF-BY-ONE: shift+down selects up to column 0 of the FOLLOWING line.
       //   Without this, "select 3 lines" asks git about 4.
       if (sel.selectionEnd > sel.selectionStart &&
           document.getLineStartOffset(end - 1) == sel.selectionEnd) end -= 1
       startLine = start.coerceAtLeast(1)
       endLine   = end.coerceIn(startLine, document.lineCount)
       fromSelection = true
   } else {
       val uMethod = psiFile.findElementAt(editor.caretModel.offset)
                            ?.toUElement()?.getParentOfType<UMethod>()
       if (uMethod != null) startLine..endLine = uMethod.sourcePsi!!.textRange → lines
       else                 startLine = endLine = document.getLineNumber(caretOffset) + 1
       fromSelection = false
   }

4. CANDIDATE FUNCTIONS  ── every function whose range INTERSECTS the line range
   //  intersects, not contains: selecting the middle 3 lines of a function still finds it,
   //  and selecting a whole class finds all of its functions.
   val selRange = TextRange(document.getLineStartOffset(startLine - 1),
                            document.getLineEndOffset(endLine - 1))
   val candidates = PsiTreeUtil.findChildrenOfType(psiFile, PsiElement::class.java)
       .asSequence()
       .mapNotNull { it.toUElement() as? UMethod }
       .filter { it.sourcePsi?.textRange?.intersects(selRange) == true }
       .distinctBy { it.sourcePsi }
       .toList()
   // Cheaper equivalent if the above is slow on big files: walk up from the element at
   // selectionStart to the enclosing UClass, then iterate uClass.methods and filter by intersects.

5. PICK ONE  ── so Alive and Blast Radius never see ambiguity
   when (candidates.size) {
       0    -> chosen = null            // Unroll still works; the other two disable themselves
       1    -> chosen = candidates[0]
       else -> chosen = if (!requiresFunction) candidates[0]   // Unroll: just label it
               else JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(candidates.map { it.name })
                    .setTitle("Which function?")
                    .createPopup().showInBestPositionFor(editor)  // → user picks, or cancels
   }

6. functionPointer = SmartPointerManager.createSmartPsiElementPointer(chosen?.sourcePsi)
   // store sourcePsi, NOT javaPsi — for Kotlin, javaPsi is a synthetic light element

7. containerFqn = chosen?.getContainingUClass()?.qualifiedName
   displayName   = when {
       fromSelection && candidates.size > 1 -> "${file.name}:$startLine–$endLine (${candidates.size} functions)"
       fromSelection                        -> "${file.name}:$startLine–$endLine"
       chosen != null                       -> "${chosen.name}()"
       else                                 -> "${file.name}:$startLine"
   }
```

**Behaviour by selection shape**

| What the user selected | Unroll | Alive | Blast Radius |
|---|---|---|---|
| Nothing, caret inside a function | history of the whole function | ✓ | ✓ |
| Nothing, caret on an import / field | history of that one line | disabled | disabled |
| 3 lines inside one function | **history of those 3 lines only** | ✓ that function | ✓ that function |
| Lines spanning two functions | history of the whole selected range | chooser → one function | chooser → one function |
| A whole class | history of the whole range | chooser | chooser |
| A blank block / comment only | history of those lines | disabled, with a reason | disabled, with a reason |

**Why the chooser rather than grouped output:** it is ~15 lines in one place, it costs C and D nothing, and it keeps their contracts to exactly one function. Grouped per-function results are a stretch goal, not a Saturday problem.

`update()` calls `peek()` only — never the chooser — sets `isEnabledAndVisible`, and uses `ActionUpdateThread.BGT`. Alive and Blast Radius additionally require `functionPointer != null`. All PSI/UAST access inside a read action; the chooser runs on the EDT before the background task starts.

#### 9.1.2 Action skeleton (all three share this shape)

```kotlin
class UnrollAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = CodeTargetResolver.resolve(e) != null
    }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor  = e.getData(CommonDataKeys.EDITOR) ?: return
        val target  = CodeTargetResolver.resolve(e) ?: return

        object : Task.Backgroundable(project, "Unrolling ${target.displayName}…", true) {
            override fun run(indicator: ProgressIndicator) {
                val report = Services.history(project).unroll(target, indicator)
                var popup: JBPopup? = null
                ApplicationManager.getApplication().invokeLater {
                    popup = UnrollPopup.show(project, editor, UnrollRenderer.render(report))
                }
                // Optional, non-blocking: synthesis refreshes the popup in place when it lands.
                Services.synthesis(project).synthesise(report, indicator)?.let { s ->
                    ApplicationManager.getApplication().invokeLater {
                        popup?.let { UnrollPopup.update(it, UnrollRenderer.render(report.copy(synthesis = s))) }
                    }
                }
            }
        }.queue()
    }
}
```

#### 9.1.3 `UnrollPopup`

```kotlin
object UnrollPopup {
    fun show(project: Project, editor: Editor, html: String, onLink: (String) -> Unit = {}): JBPopup
    fun update(popup: JBPopup, html: String)
}
```

- `JEditorPane`, `contentType = "text/html"`, `isEditable = false`
- Prepend `UIUtil.getCssFontDeclaration(JBUI.Fonts.label())` plus our CSS. Colours via `JBColor` — never hardcoded hex, so Darcula and Light both look right.
- `JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, editorPane)`
  `.setResizable(true).setMovable(true).setRequestFocus(true).setMinSize(Dimension(720, 400))`
  `.createPopup().showInBestPositionFor(editor)`
- `HyperlinkListener` routes the scheme `unroll://<verb>/<arg>`:
  `open` → `BrowserUtil.browse` · `nav` → `Navigatable.navigate(true)` · `diff` → native commit view · `run` → JUnit runner · `expand` → toggle section

#### 9.1.4 `Html.kt` — **the demo target**

```kotlin
object Html {
    fun esc(s: String?): String        // ★ §4.3 — the function we demo on
    fun row(vararg cells: String): String
    fun section(title: String, body: String): String
    fun dim(s: String): String
    fun warn(s: String): String
}
```

Treat `esc()` with deliberate care: every edge case gets its own issue, its own PR, its own reviewer question, and its own test with a literal assertion. This is real work — it is also Sunday's demo.

#### 9.1.5 `UnrollSettings` + `UnrollConfigurable`

Application-level `PersistentStateComponent`:

| Setting | Type | Default | Notes |
|---|---|---|---|
| `githubToken` | secret | — | `PasswordSafe`, **not** in XML |
| `llmBaseUrl` | String | `https://api.openai.com/v1` | see presets, §9.5 |
| `llmApiKey` | secret | — | `PasswordSafe` |
| `llmModel` | String | — | free text; whatever the endpoint serves |
| `llmFlavor` | enum | `OPENAI_COMPATIBLE` | or `ANTHROPIC_MESSAGES` |
| `synthesisEnabled` | Boolean | **false** | master switch |
| `maxCommitsEnriched` | Int | 6 | caps GitHub calls |
| `churnWindowDays` | Int | 180 | |
| `useFakeServices` | Boolean | false | dev + demo parachute |

```kotlin
val attrs = CredentialAttributes(generateServiceName("Unroll", "github"))
PasswordSafe.instance.setPassword(attrs, token)
```

Settings → Tools → Unroll. Provider preset dropdown fills `llmBaseUrl` + `llmFlavor`. One-line disclaimer: *"When Synthesis is enabled, commit messages and issue/PR text for the selected code are sent to the endpoint you configure."*

#### 9.1.6 `DiskCache`

```kotlin
class DiskCache(private val repoKey: String) {
    fun get(kind: String, key: String, ttl: Duration): String?
    fun put(kind: String, key: String, json: String)
}
```
Root `PathManager.getSystemPath()/unroll/<repoKey>/<kind>`; `repoKey` = SHA-1 of the origin URL; file name = SHA-1 of `key`. TTLs: `pr`/`issue` 7 days, `commits` 1 day, `synthesis` 30 days. **Every read and write in a try/catch** — a cache failure must never fail a feature.

**Definition of done (A):** sandbox IDE launches, three menu items appear, each opens a popup on fake data, settings save a token, cache round-trips, repo protocol live.

---

### 9.2 Person B — Unroll

Language-agnostic — this component never touches PSI, so UAST changes nothing here.

#### 9.2.1 `GitLog`

`GeneralCommandLine("git", …).withWorkDirectory(repoRoot.path)`, stdout as UTF-8.

```
git log -L <startLine>,<endLine>:<repoRelativePath> -s \
    --pretty=format:'%x02%H%x1f%P%x1f%an%x1f%aI%x1f%s%x1f%b%x03'
```

- `-s` suppresses the patch, leaving only commits that touched that line range
- **Wide selections return a lot.** The resolver guarantees a valid, clamped range, but selecting a
  whole class can return 30+ commits. Cap the rendered timeline at **20**, newest first, and append
  `+N older commits`. Enrichment is separately capped by `maxCommitsEnriched`.
- Git exits non-zero on an invalid range — treat any non-zero exit as "no line history" and take the
  `--follow` fallback rather than surfacing the error
- Split on `\u0002`, drop the trailing `\u0003`, split fields on `\u001f`
- `%P` = parents; **2+ → `isMerge = true`**
- **Limitation:** `-L` cannot combine with `--follow`, so a rename truncates the trail. On 0 results, fall back to `git log --follow -n 5 … -- <path>` and add a warning: *"line-level history unavailable (file was renamed) — showing file history instead."*

**Semantic check** per commit:
```
git show --format= -w --numstat <sha> -- <repoRelativePath>
```
Empty output → whitespace/format-only → `isSemantic = false`. Merges are also non-semantic.

Origin: `git remote get-url origin` → `github\.com[:/]([^/]+)/([^/.]+)(\.git)?`

#### 9.2.2 `IssueRefs` (backup demo target — keep it clean and tested)

```kotlin
val HASH_REF = Regex("""(?i)\b(?:fix(?:e[sd])?|close[sd]?|resolve[sd]?|refs?|see)?\s*#(\d+)\b""")
val URL_REF  = Regex("""https://github\.com/[^/\s]+/[^/\s]+/(issues|pull)/(\d+)""")
```
Run over `subject + "\n" + body`, dedupe by number. A bare `#123` may be an issue or a PR — GitHub's `/issues/{n}` resolves both, so record `ISSUE` and let the API disambiguate.

#### 9.2.3 `GitHubClient`

Base `https://api.github.com`. Headers on every request:
```
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
Authorization: Bearer <token>
User-Agent: unroll-intellij-plugin
```

| Need | Endpoint |
|---|---|
| PRs containing a commit | `GET /repos/{o}/{r}/commits/{sha}/pulls` |
| PR detail | `GET /repos/{o}/{r}/pulls/{n}` |
| PR inline review comments | `GET /repos/{o}/{r}/pulls/{n}/comments?per_page=50` |
| PR conversation comments | `GET /repos/{o}/{r}/issues/{n}/comments?per_page=50` |
| Issue detail | `GET /repos/{o}/{r}/issues/{n}` |
| Issue comments | `GET /repos/{o}/{r}/issues/{n}/comments?per_page=50` |

- Token effectively mandatory (60/hr anonymous vs 5,000 authenticated). **Our own repo is small, so rate limits are a non-issue — one of the quiet wins of demoing on ourselves.**
- Enrich at most `maxCommitsEnriched` commits, newest first, skipping non-semantic ones. Worst case ≈ 24 calls.
- Fixed pool of 4, 10s per request, 25s overall budget, then return partial.
- Cache every response body by URL.
- Empty `/commits/{sha}/pulls` is normal for direct commits — fall through to issue refs. **With branch protection on, most of our commits will have a PR, which is exactly what we want.**

#### 9.2.4 `UnrollRenderer`

Newest first: short SHA, relative date, subject; if enriched, issue/PR title + link, plus up to **3** comments chosen by priority:
1. comments containing `?` (questions — where reasoning lives)
2. comments containing `because`, `can't`, `won't`, `instead`, `why`
3. longest remaining

Dim non-semantic commits, collapse runs into `+2 formatting commits`.
Footer `[ Continue → diff ]` → `unroll://diff/<sha>` for the newest semantic commit:

```kotlin
VcsLogContentUtil.runInMainLog(project) { it.vcsLog.jumpToCommit(HashImpl.build(sha), repoRoot) }
```
Accepted fallback if that fights back: `BrowserUtil.browse(commitUrl)` — but try native first, it is a better beat.

**Definition of done (B):** on our own repo's demo target, Unroll shows ≥4 commits, ≥2 enriched with an issue or PR, ≥1 real review comment, Continue opens a diff. Works offline (timeline only + warning).

---

### 9.3 Person C — Alive (the UAST path)

**This is the hardest component. It is also the one that makes "works on Java and Kotlin" true.** Budget accordingly; you have an extra hour in the schedule.

#### 9.3.1 UAST primer for this component

```kotlin
import org.jetbrains.uast.*

psiElement.toUElement()                         // any PSI → UElement (Java or Kotlin)
element.getParentOfType<UMethod>()              // enclosing function, either language
uMethod.sourcePsi                               // the real source element — use for pointers & navigation
uMethod.javaPsi                                 // PsiMethod view; for Kotlin this is a light element
uMethod.uAnnotations                            // annotations, qualified names, both languages
uExpression.evaluate()                          // ★ constant folding — resolves literals AND const refs
uCallExpression.valueArguments                  // argument expressions
uCallExpression.methodName                      // "assertEquals"
```

`evaluate()` is the single most valuable call in this component: it turns `100.00`, `"a & b"`, `-1`, `TRUE`, and `private const val X = 5` into real values, in both languages, without you writing per-language code.

> **On selections:** you always receive exactly one resolved function in `target.functionPointer`.
> The resolver handles multiline selections, partial selections and multi-function selections
> (§9.1.1) and asks the user to pick when it is ambiguous. If `functionPointer` is null your action
> was never enabled. You do not need to look at `startLine`/`endLine` at all.

#### 9.3.2 `UastReferenceIndexService` (shared with D)

```kotlin
override fun findCallers(method: UMethod): List<UMethod> = ReadAction.compute<_, RuntimeException> {
    val scope = GlobalSearchScope.projectScope(project)
    // Search BOTH views: javaPsi finds Java call sites and most Kotlin ones via light classes;
    // sourcePsi catches Kotlin references the light-class mapping misses. Dedupe after.
    val searchTargets = listOfNotNull(method.javaPsi, method.sourcePsi).distinct()
    searchTargets
        .flatMap { ReferencesSearch.search(it, scope).findAll() }
        .mapNotNull { it.element }
        .filterNot { TestSourcesFilter.isTestSources(it.containingFile?.virtualFile ?: return@filterNot true, project) }
        .mapNotNull { it.toUElement()?.getParentOfType<UMethod>() }
        .distinctBy { it.sourcePsi }
        .take(200)
}
```

`findTestCallers` is the same with the filter inverted **and** restricted to functions carrying a test annotation — short name of any `uAnnotations` entry in `Test`, `ParameterizedTest`, `RepeatedTest`, `TestFactory`.

Read actions everywhere; `indicator.checkCanceled()` inside loops.

#### 9.3.3 `AssertionExtractor`

For each test function referencing the target, find the `UCallExpression` calling it and walk **up** to classify:

| Shape | `kind` | `expected` |
|---|---|---|
| `assertEquals(X, target(...))` | VALUE | render of `X` |
| `assertThat(target(...)).isEqualTo(X)` | VALUE | render of `X` |
| `assertThat(target(...)).isTrue()` / `.isFalse()` | BOOLEAN | `true` / `false` |
| `assertThat(target(...)).hasSize(N)` | VALUE | `size N` |
| `assertTrue(target(...))` / `assertFalse(...)` | BOOLEAN | `true` / `false` |
| `assertNull(target(...))` / `assertNotNull(...)` | NULLCHECK | `null` / `not null` |
| `assertThrows(X::class.java) { target(...) }` | EXCEPTION | `X` simple name |
| **Kotlin** `assertFailsWith<X> { target(...) }` | EXCEPTION | `X` simple name |
| no recognisable assertion | UNKNOWN | `?` — still shown, increments `unparsedCount` |

**Argument rendering** (`renderExpr(e: UExpression): String`):
```
1. e.evaluate()             → if non-null, format it (quote strings, keep numbers bare)
2. UReferenceExpression     → resolve() to a PsiEnumConstant → its name
3. otherwise                → e.sourcePsi?.text, truncated to 40 chars
```

**Parameterised tests are a gift.** `@ParameterizedTest` + `@CsvSource(["a, B, c", …])` — each string is already an input→output row. Read via `uAnnotation.findAttributeValue("value")`, iterate the array's `valueArguments`, `evaluate()` each, split on `,`, trim. `parameterised = true`. Same for `@ValueSource`.

**Human name — Kotlin gives us this for free.** Kotlin test functions are conventionally named with backticks:
```kotlin
@Test fun `escapes ampersand`() { assertEquals("a &amp; b", Html.esc("a & b")) }
```
Rule: if the function name contains a space, use it verbatim. Otherwise split on `_` and camelCase boundaries, lowercase, drop a leading `test` / `should`, join with spaces.

> **Write our own tests with backtick names.** They read beautifully in the Alive popup and cost nothing. Make this a review-comment expectation in §4.2.

#### 9.3.4 Output rules

- Sort: VALUE and BOOLEAN first, then NULLCHECK, then EXCEPTION, then UNKNOWN
- Cap 12 rows, append `+N more`
- Zero tests → the **empty state is a feature**: *"No tests describe `esc()`. Nothing will catch you if you change it."* + link to Blast Radius
- Rows navigate via `unroll://nav/<pointerId>` using `sourcePsi`

#### 9.3.5 "Run these" (stretch — last, timebox 45 min)

```kotlin
val loc = PsiLocation.fromPsiElement(testFn.sourcePsi)
val ctx = ConfigurationContext.createEmptyContextForLocation(loc)
ctx.configuration?.let {
    ExecutionUtil.runConfiguration(it.configurationSettings, DefaultRunExecutor.getRunExecutorInstance())
}
```
Fallback: navigate to the first test function and let the user press the gutter icon.

**Definition of done (C):** on `Html.esc()` in our own Kotlin source, Alive shows ≥5 rows with correct literal values, rows navigate, the empty state renders on an untested function, and `ReferenceIndexService` is merged by **16:00 Saturday**.

---

### 9.4 Person D — Blast Radius

> **On selections:** same guarantee as Alive — exactly one resolved function, ambiguity already
> settled by the resolver (§9.1.1). Ignore `startLine`/`endLine` except when passing the target to
> `ChurnService`, which is file-scoped anyway.

#### 9.4.1 Caller analysis

1. `callers = ReferenceIndexService.findCallers(uMethod)` (cap 40; set `truncated` beyond)
2. `coveredByTest = ReferenceIndexService.findTestCallers(caller).isNotEmpty()` — **depth 1 only**, no recursion
3. `module = ModuleUtilCore.findModuleForPsiElement(caller.sourcePsi)?.name`
4. Flags per `RiskFlagger`
5. Sort by risk weight: `NO_TEST 4 · SCHEDULED 3 · ENTRY_POINT 3 · ASYNC 2 · CROSS_MODULE 1 · PUBLIC_API 1`

#### 9.4.2 `RiskFlagger`

Match annotations by **short name** off `uMethod.uAnnotations` and the containing `UClass` — works for Java and Kotlin without resolving any framework.

| Flag | Condition |
|---|---|
| `NO_TEST` | no depth-1 test caller |
| `SCHEDULED` | annotated `Scheduled`, `Cron`, `KafkaListener`, `EventListener`, `JmsListener`; **or** class name matches `(?i).*(job\|scheduler\|cron\|worker\|consumer\|listener)$` |
| `ENTRY_POINT` | class annotated `RestController`, `Controller`, `WebServlet`; **or** method annotated `*Mapping` |
| `ASYNC` | annotated `Async`; **or** return type simple name in `CompletableFuture`, `Future`, `Mono`, `Flux`, `Deferred` |
| `PUBLIC_API` | method and class both public and package contains neither `internal` nor `impl` |
| `CROSS_MODULE` | caller's module differs from the target's |

> On our own small repo most callers will be `CROSS_MODULE` (different packages) and some genuinely `NO_TEST`. That is a real, honest result — do not inflate it.

#### 9.4.3 `GitChurnService`

Independent of B's code — its own small git call.
```
git log --since="<windowDays> days ago" --pretty=format:'%H%x1f%s' -- <repoRelativePath>
```
```kotlin
val REGRESSION = Regex("""(?i)\b(revert|regress\w*|hotfix|roll ?back|fix ?up|broke\w*|double-escap\w*)\b""")
```
> For the demo, set `churnWindowDays` to something small (7) so "changed 5× in the last 2 days" reads as the dramatic signal it is. Make it a setting, default 180, and change it live if it lands better.

#### 9.4.4 Rendering

```
<function>  ·  N callers across M packages

⚠  U callers have no test coverage
     <top 5 risky callers, each with its flag as a trailing note>
⚠  changed C× in the last <window>, once tagged "<regression subject>"    [only if C > 0]
✓  K callers covered by T tests                                            [ Run all ▸ ]
```

Empty state: *"Nothing calls `x()` in this project — dead code or an external entry point."* Render it confidently, not as an error.

**Definition of done (D):** on `Html.esc()`, Blast Radius lists real callers from all three renderers, ≥2 flagged, coverage split correct, churn line present, callers navigate.

---

### 9.5 Person A — Synthesis (optional, provider-agnostic, ships last)

**No vendor SDK.** Raw `java.net.http.HttpClient` behind a provider interface, so any endpoint and key works. This also means zero bundled dependencies and zero classpath risk.

**Gate:** `settings.synthesisEnabled && llmApiKey != null && llmModel.isNotBlank()`. Off → render nothing. No placeholder, no error, no latency. **The plugin must be fully usable with zero AI.**

#### 9.5.1 `LlmProvider.kt`

```kotlin
package dev.unroll.synthesis

enum class ApiFlavor { OPENAI_COMPATIBLE, ANTHROPIC_MESSAGES }

interface LlmProvider {
    /** Returns raw response text, or null on any failure. Never throws. */
    fun complete(system: String, user: String, maxTokens: Int = 1024): String?
}

class HttpLlmProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val flavor: ApiFlavor
) : LlmProvider {

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build()

    override fun complete(system: String, user: String, maxTokens: Int): String? = try {
        val (url, body, headers) = when (flavor) {
            ApiFlavor.OPENAI_COMPATIBLE -> Triple(
                "$baseUrl/chat/completions",
                mapOf(
                    "model" to model,
                    "max_tokens" to maxTokens,
                    "response_format" to mapOf("type" to "json_object"),
                    "messages" to listOf(
                        mapOf("role" to "system", "content" to system),
                        mapOf("role" to "user", "content" to user)
                    )
                ),
                mapOf("Authorization" to "Bearer $apiKey")
            )
            ApiFlavor.ANTHROPIC_MESSAGES -> Triple(
                "$baseUrl/messages",
                mapOf(
                    "model" to model,
                    "max_tokens" to maxTokens,
                    "system" to system,
                    "messages" to listOf(mapOf("role" to "user", "content" to user))
                ),
                mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01")
            )
        }

        val req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(45))
            .header("Content-Type", "application/json")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .POST(HttpRequest.BodyPublishers.ofString(Gson().toJson(body)))
            .build()

        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) return null

        val json = JsonParser.parseString(res.body()).asJsonObject
        when (flavor) {
            ApiFlavor.OPENAI_COMPATIBLE ->
                json["choices"].asJsonArray[0].asJsonObject["message"].asJsonObject["content"].asString
            ApiFlavor.ANTHROPIC_MESSAGES ->
                json["content"].asJsonArray[0].asJsonObject["text"].asString
        }
    } catch (e: Exception) {
        logger.warn("Synthesis provider call failed", e); null
    }
}
```

#### 9.5.2 Presets (settings dropdown)

| Preset | Base URL | Flavor | Notes |
|---|---|---|---|
| OpenAI | `https://api.openai.com/v1` | OPENAI_COMPATIBLE | |
| OpenRouter | `https://openrouter.ai/api/v1` | OPENAI_COMPATIBLE | one key, many models — best default for judges to try |
| Groq | `https://api.groq.com/openai/v1` | OPENAI_COMPATIBLE | fastest for a live demo |
| Together | `https://api.together.xyz/v1` | OPENAI_COMPATIBLE | |
| Ollama (local) | `http://localhost:11434/v1` | OPENAI_COMPATIBLE | no key needed — **works with the venue offline** |
| Anthropic | `https://api.anthropic.com/v1` | ANTHROPIC_MESSAGES | |
| Custom | user-supplied | user-supplied | |

> **Ollama is worth ten minutes.** A local model means Synthesis demos with the Wi-Fi unplugged, and "bring your own endpoint, including one on your laptop" is a genuine privacy answer if a judge asks whether we ship code to a vendor.

#### 9.5.3 Prompt and parsing

System message:
```
You explain code history to a developer who is about to change the code.
Reply with ONLY a JSON object, no markdown fence, with exactly these keys:
whyExists, rejected, sinceThen, watchOut.
Each value: one sentence, at most 25 words, plain English.
If the material does not support a field, use exactly "not recorded".
Never speculate beyond the material.
```

User message, built from the already-fetched `UnrollReport` — no extra network calls:
```
CODE: <displayName> in <repoRelativePath> lines <start>-<end>

COMMITS (newest first):
<shortSha · date · subject · body>

LINKED ISSUES AND PRs:
<number · title · body (truncate 1500 chars)>

DISCUSSION:
<author: body (truncate 800 chars)>
```

Parse defensively — models add fences even when told not to:
```kotlin
fun parseSynthesis(raw: String): Synthesis? = try {
    val cleaned = raw.substringAfter("```json", raw).substringBefore("```", raw)
    val body = cleaned.substring(cleaned.indexOf('{'), cleaned.lastIndexOf('}') + 1)
    Gson().fromJson(body, Synthesis::class.java)
} catch (e: Exception) { null }
```

Cache by newest SHA + line range, TTL 30 days — a re-run during the demo is then instant. Pre-warm before presenting.

---

## 10. Threading rules (non-negotiable)

Violating these produces `Slow operations are prohibited on EDT` and a frozen IDE in front of judges.

| Rule | How |
|---|---|
| No git, HTTP, or LLM calls on the EDT | everything inside `Task.Backgroundable.run(indicator)` |
| No PSI or UAST access off a read action | `ReadAction.compute<T, RuntimeException> { … }` |
| No PSI/UAST elements held across threads | `SmartPsiElementPointer` over `sourcePsi`; resolve just before use |
| All UI updates on the EDT | `ApplicationManager.getApplication().invokeLater { … }` |
| Long loops cancellable | `indicator.checkCanceled()` each iteration; set `indicator.text` |
| `AnAction.update` fast | `ActionUpdateThread.BGT`, no IO |

---

## 11. Error and empty states

Designed states with real copy, not stack traces. Judges notice.

| Situation | What the user sees |
|---|---|
| No git repo | "Unroll needs a Git repository." |
| No GitHub remote | Timeline only + "No GitHub remote — showing commit history only." |
| No GitHub token | Timeline only + "Add a GitHub token in Settings → Tools → Unroll." |
| Rate limited | Partial + "GitHub rate limit reached — showing what we have." |
| Offline | Cached results if present, else timeline only. Never a modal. |
| File renamed (`-L` empty) | File history + "Line-level history unavailable (file was renamed)." |
| Not a function (caret in a field / import, or a selection covering no function) | Unroll falls back to the line range and works normally. Alive and Blast Radius are greyed out in the menu. |
| Selection spans several functions | Chooser popup: "Which function?" — Unroll needs no answer and runs on the full range regardless |
| Selection spans several **files** (rare, from a diff view) | Take the file the selection starts in; add the warning "Showing history for `<file>` only." |
| Very wide selection (whole class / file) | Timeline capped at 20 commits + `+N older commits` |
| No tests (Alive) | "No tests describe `x()`. Nothing will catch you if you change it." + link to Blast Radius |
| No callers (Blast Radius) | "Nothing calls `x()` in this project — dead code or an external entry point." |
| Synthesis off or failing | Nothing at all. Raw popup unchanged. |

---

## 12. Testing

Our own tests are demo material (§4.2), so they are not optional. Three that matter most:

1. **`Html.esc()`** — the demo target. Every edge case gets a literal-assertion test with a backtick name. These are the rows Alive renders on stage.
2. **`GitLog` parser** — unit test over a captured stdout fixture. Parsing bugs are the most likely silent failure.
3. **`AssertionExtractor`** — light-service test over Java *and* Kotlin snippets covering `assertEquals`, `assertFailsWith`, and `@CsvSource`. This is the UAST risk; test it early.

Plus a manual smoke checklist in the README: the demo target, the three shortcuts, expected output. Run it before presenting.

---

## 13. Repository conventions

- `main` is protected and always demoable
- Commit subject: `<area>: <what>` + `Refs #N` / `Fixes #N` — e.g. `alive: extract CsvSource rows   Refs #18`
- `./gradlew runIde` before every PR. ~40s; it will save the weekend.
- Nothing added to `api/` without telling the group

---

## 14. Timeline and ship gates

**Saturday 22 September**

| Time | A — Platform | B — Unroll | C — Alive | D — Blast Radius |
|---|---|---|---|---|
| 09:00 | **Kickoff 30 min.** Create the repo, branch protection, **write all ~15 issues together**. Read §4 aloud. | | | |
| 09:30 | Gradle + plugin.xml + `api/` + fakes + `Html.esc()` v1 (PR #1, #9) | tokens, read GitHub API docs | **UAST spike** — resolve a Kotlin fun to `UMethod`, find one reference | explore `ReferencesSearch` on fakes |
| **10:00** | **GATE: PR #1 merged — contracts + fakes on `main`** | | | |
| 10:00–13:00 | actions, popup shell, HTML/CSS theming | `git log -L` parse + timeline | `UastReferenceIndexService` | caller analysis + risk flags (fakes) |
| **13:00** | **Checkpoint 1** — everyone merges; all three actions open a popup on fake data | | | |
| 13:00–16:00 | settings + PasswordSafe + DiskCache | GitHub client + enrichment | `AssertionExtractor` (Java + Kotlin) | `GitChurnService` |
| **16:00** | **GATE: C's `ReferenceIndexService` merged** — D drops the fake. **A runs the reformat commit.** | | | |
| 16:00–18:00 | help whoever is behind | rendering + Continue→diff | rendering + navigation | rendering + navigation |
| **18:00** | **Checkpoint 2 + historian audit (§4.4).** All three actions on real data, on our own repo. | | | |
| 18:00–20:00 | **GATE: Unroll works end-to-end on our own history.** If not, everyone helps B and Synthesis is cut. | | | |
| evening | polish, empty states, deliberately close out issue #29 (the rejected-alternative PR) | | | |

**Sunday 23 September**

| Time | Who | What |
|---|---|---|
| 09:00 | A | **Historian audit #2.** Any unticked box → we create the artifact through real work this morning. |
| 09:00–11:00 | B, C, D | finish rendering, fix last night's rough edges — each as a real PR with a real review |
| 09:00–11:00 | A | Synthesis + Ollama fallback — **only if all three actions are solid.** Otherwise polish UI. |
| **11:00** | all | **Checkpoint 3 — feature freeze.** Nothing new after this. |
| 11:00–13:00 | all | bug-fix only. Pre-warm every cache on the demo target. |
| 13:00–14:00 | all | **Rehearse three times, end to end, on the presentation machine.** |
| 14:00–15:00 | A + one other | build the deck from §2, §3, §16 |
| 15:00 | all | final rehearsal, submit |

**Cut order when behind** (from the top): Synthesis → "Run these" / "Run all" → native diff (fall back to browser) → parameterised-test support → churn line.
**Never cut:** the three actions, their empty states, or the commit protocol.

---

## 15. Demo script (target: 2 minutes 45)

Run on **our own repo**, our own IDE, the demo target from §4.3. Caches pre-warmed. Fake-services switch is the parachute.

| # | Time | Action | Say |
|---|---|---|---|
| 0 | 0:00 | `Html.kt` open on `esc()`. Nothing else on screen. | "This is our plugin's own source code. We wrote it over the last two days. This function escapes HTML — twenty lines, and it's been changed five times." |
| 1 | 0:15 | Turn on IntelliJ's **Annotate**. Hover a line. Click the `#29` — nothing, or a browser. | "Here's what the IDE gives you today. A hash, a name, a date. The issue number is just a hyperlink — IntelliJ has never read it. This is where the afternoon goes." |
| 2 | 0:40 | Close the browser. Right-click → **Unroll**. | "Unroll." |
| 3 | 0:50 | Point at the timeline. | "Five commits touched these exact lines. Not the file — these lines. Two linked issues. One of them is a reformat, which we grey out because it changed nothing." |
| 3b | +0:10 | *(optional — cut if running long)* Select just the three lines of the double-escape guard. Right-click → Unroll. | "And it isn't only whole functions. Select any three lines and ask the same question — this guard alone, and the single commit that put it there." |
| 4 | 1:05 | Point at the review exchange. | "And here's the bit that matters. A reviewer asked why we don't just skip escaping at the call site. The answer is right there: two other components pass raw API text. **That reasoning exists nowhere except a thread nobody ever links to.**" |
| 5 | 1:25 | Point at the follow-up commit. | "And it's already been patched once for double-escaping. So the reason may not even hold any more." |
| 6 | 1:35 | *(If Synthesis is on)* the four-line header. | "Optional, off by default, and it's your endpoint and your key — including a local model. Four lines instead of four tabs." |
| 7 | 1:50 | Right-click → **Alive**. | "I know why it's here. I still don't know what it does." |
| 8 | 2:00 | Point at the rows. | "Nothing generated these. They're the assertions in the tests that already cover this function. Real inputs, real outputs. Four seconds, and I've read no code." |
| 9 | 2:15 | Right-click → **Blast Radius**. | "Last question: what breaks if I touch it." |
| 10 | 2:25 | Point at the uncovered callers. | "Eleven callers across four packages. Three with no test at all. Find Usages gives you a tree — it doesn't tell you which branches will catch you." |
| 11 | 2:40 | Hands off the keyboard. | "Understand, verify, change safely. And everything you just saw is this plugin reading the two days of work that produced it. We write code once and read it forever — the IDE should act like it." |

**Rules for the driver:** nothing typed live, no font fiddling, no "let me just…". Everything pre-opened. If something fails, keep talking and move on — three features means two survivors is still a demo.

---

## 16. Presentation outline (~9 slides)

| # | Slide | Content |
|---|---|---|
| 1 | **Title** | *Unroll — why is this line here?* Team names, 42AD × JetBrains |
| 2 | **The shift** | "We write code once. We read it forever." Then: AI writes the code now — reading is the whole job. One line, big type. |
| 3 | **The cost** | Three numbers from §2.2: 4.6× review wait · 38% say AI code is harder to review · 34% of PR descriptions empty. Sources in small type. |
| 4 | **The quote** | *"Reviewers are forced to reverse-engineer intent from noise."* — arXiv 2605.17548, 2026. Our pitch in someone else's words. |
| 5 | **What the IDE has today** | §2.3 cut to four rows. End on: *four half-features, never joined up.* |
| 6 | **Unroll** | The §3.1 diagram. Three questions, one right-click. Java and Kotlin. |
| 7 | **DEMO** | §15 — on our own repo. Longest part of the slot. |
| 8 | **How it works** | One diagram: `git log -L` + GitHub API → Unroll · UAST + assertion extraction → Alive · the two crossed → Blast Radius. Say plainly: *we delegate the diff viewer and the test runner to IntelliJ, because JetBrains already perfected both.* Add: *no vendor lock-in — the optional AI layer takes any endpoint, including a local model.* |
| 9 | **What's next** | Jira / GitLab connectors · inline gutter hints · stale-context detection ("this reason no longer holds") · shared team cache. |

**Two lines to land:**
- *Git blame tells you who. Unroll tells you why — and whether that reason still holds.*
- *Everything in that demo was our own repo. The tool read the two days that made it.*

---

## 17. Risks and mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| **We forget the protocol and push straight to main** | **High** | Branch protection makes it impossible. Historian audits at Sat 18:00 and Sun 09:00. |
| Our history turns out thin on Sunday morning | Medium | §4.3 plans the PR arc in advance; audit #2 leaves a morning to fix it through real work |
| Engineered history reads as staged | Medium | Every comment is a real question about real code. Never write a comment after the fact to fill a gap. |
| **UAST harder than expected on Kotlin** | **Medium-high** | C spikes it in the first 30 minutes. If `sourcePsi` search fails, fall back to `javaPsi` only — Alive and Blast Radius then work on Java, and we demo Unroll (language-agnostic) on Kotlin. Say so honestly on stage. |
| Venue Wi-Fi fails | High | Everything cached to disk; pre-warm Sat night and Sun morning; Ollama for Synthesis; `useFakeServices` as last resort |
| GitHub rate limit | **Low** | Our repo is tiny — one of the wins of demoing on ourselves |
| `git log -L` empty after a rename | Medium | Documented `--follow` fallback + warning banner |
| Scope creep kills the demo | High | Cut list in §14 agreed in writing before anyone is tired |
| "Isn't this just git blame?" | High | Slide 5 answers it first, and demo step 1 shows blame failing on purpose |

---

## 18. Appendix

### 18.1 Git commands used

```bash
# commits touching a line range (Person B)
git log -L <start>,<end>:<path> -s --pretty=format:'%x02%H%x1f%P%x1f%an%x1f%aI%x1f%s%x1f%b%x03'

# fallback when the file was renamed
git log --follow -n 5 --pretty=format:'%x02%H%x1f%P%x1f%an%x1f%aI%x1f%s%x1f%b%x03' -- <path>

# is this commit whitespace-only for this file?
git show --format= -w --numstat <sha> -- <path>        # empty output = non-semantic

# churn (Person D)
git log --since="<N> days ago" --pretty=format:'%H%x1f%s' -- <path>

# origin
git remote get-url origin
```

### 18.2 IntelliJ / UAST APIs by component

| Component | Key APIs |
|---|---|
| Target resolution | `CommonDataKeys.EDITOR/PSI_FILE`, `toUElement()`, `getParentOfType<UMethod>()`, `SmartPointerManager`, `VfsUtilCore.getRelativePath` |
| Git repo discovery | `GitRepositoryManager.getRepositoryForFileQuick` |
| Background work | `Task.Backgroundable`, `ProgressIndicator`, `ReadAction.compute`, `invokeLater` |
| Search | `ReferencesSearch.search(javaPsi \| sourcePsi)`, `GlobalSearchScope.projectScope`, `TestSourcesFilter.isTestSources` |
| UAST | `UMethod`, `UClass`, `UCallExpression`, `UExpression.evaluate()`, `UAnnotation.findAttributeValue`, `getContainingUClass()` |
| Modules | `ModuleUtilCore.findModuleForPsiElement` |
| Popups | `JBPopupFactory.createComponentPopupBuilder`, `JBColor`, `UIUtil.getCssFontDeclaration` |
| Navigation | `SmartPsiElementPointer.element as Navigatable → navigate(true)` |
| Settings | `PersistentStateComponent`, `@State`/`@Storage`, `PasswordSafe`, `CredentialAttributes` |
| Commit view | `VcsLogContentUtil.runInMainLog`, `HashImpl.build` |
| Running tests | `ConfigurationContext`, `ExecutionUtil.runConfiguration`, `DefaultRunExecutor` |

### 18.3 Glossary for the deck

- **PSI** — IntelliJ's parsed model of source code.
- **UAST** — the Universal Abstract Syntax Tree: one API over Java, Kotlin and Scala. It is why Unroll works on both languages with a single code path.
- **`git log -L`** — git's line-range history. Every commit that ever touched a specific span of a file. The single command the whole Unroll feature rests on.
- **Blame** — per-line authorship of the *most recent* change only. Its limitation is our opportunity.

---

*Unroll — 42 Abu Dhabi × JetBrains Hackathon, 22–23 September 2026.*