# Issue #7 — “setup unroll to github api” — Implementation Plan

> **Issue:** https://github.com/myda-chi/frictionless/issues/7
> Body: *“For unroll to get issue and PR metadata, we need to set the api for this calls”*
>
> **For Hermes:** use `subagent-driven-development` to execute this task-by-task if you want it
> delegated. Each task is self-contained: files, code, command, expected result, commit message.

**Goal:** make Unroll resolve a commit to its PR **and** its linked issues through the GitHub REST
API, with token auth, disk caching, rate-limit/offline degradation, and no EDT blocking — i.e.
specification §9.2.3 `GitHubClient` + the enrichment policy that produces `EnrichedCommit`.

**Architecture:** one pure-Kotlin HTTP client behind an injectable `HttpTransport` seam, a
`ResponseCache` seam over the §9.1.6 `DiskCache`, and a policy layer (`GitHubEnricher`) that caps,
skips non-semantic commits, dedupes refs and merges everything into `List<EnrichedCommit>`.
All I/O happens inside `Task.Backgroundable`; unit tests are network-free and platform-free.

**Tech stack:** Kotlin 2.x, Gradle + IntelliJ Platform Gradle Plugin 2.16.0 (repo as-is),
JDK 21 `java.net.http.HttpClient`, Gson 2.11.0, JUnit 4, `PasswordSafe` for the token.

---

## 0. Interpretation of the issue

The issue text is one line and admits two readings:

1. **“set the api” = wire up the GitHub REST calls** — implement the client that fetches PR/issue
   metadata (§9.2.3) and the enrichment that consumes it. ← **this plan takes this reading**
2. “set the api” = set the API *version/headers* — a 10-line subset of (1) with no player behind it.

Reading 1 is what §9.2.3 + the §9.2 DoD (“≥2 commits enriched with an issue or PR”) require, and it
subsumes reading 2. If you actually meant something narrower, the first four tasks below still apply
and you can stop after Task 9.

**Explicit non-goals** (other issues own them): `GitLog` (§9.2.1), the timeline renderer
(§9.2.4 `UnrollRenderer`), Alive, Blast Radius, Synthesis. `UnrollRenderer`’s “top-3 comments by
priority” heuristic and the `+N older commits` cap belong to the render issue, not this one.

---

## 1. Ground truth verified against the real API (22:19 +04, 2026-09-20)

Everything below was executed, not recalled. Re-run the same commands to re-confirm.

| What | Result | Evidence |
|---|---|---|
| Anonymous core rate limit | **limit 60/hr**, 47 remaining | `curl -sS https://api.github.com/rate_limit` → `{"core": (60, 48)}`; `x-ratelimit-limit: 60` header on `GET /repos/myda-chi/frictionless/issues/7` |
| Spec’s header set works with **no** preview header | 200 | `GET /repos/myda-chi/frictionless/issues/7` with `Accept: application/vnd.github+json` + `X-GitHub-Api-Version: 2022-11-28` |
| `GET /commits/{sha}/pulls` finds the PR for a merged commit | `[{number: 8, title: "added specification file", merged_at: "2026-09-20T18:12:08Z"}]` | `curl …/commits/75fe54c/pulls` |
| …and returns `[]` for a commit pushed with no PR | `[]` | `curl …/commits/fd8ccbd/pulls` (the “Template cleanup” commit) |
| PR vs issue disambiguation from `/issues/{n}` | PR #8 has a `pull_request` key; issue #7 does **not** | `curl …/issues/8` vs `…/issues/7` |
| Inline review-comment payload fields | `path, line, original_line, side, subject_type, in_reply_to_id, diff_hunk, body, created_at, user.login` | `GET /repos/JetBrains/intellij-platform-gradle-plugin/pulls/2076/comments`; observed `line=406` but `original_line=393` (outdated comment) → **must fall back to `original_line`** |
| Conditional requests are free (token required for the free part) | “Making a conditional request does not count against your primary rate limit … a 304 response is returned and the request was made while correctly authorized with an `Authorization` header” | [GitHub REST best practices](https://docs.github.com/en/rest/using-the-rest-api/best-practices-for-using-the-rest-api) |
| `PasswordSafe` API shape (checked against the platform jar cached locally, `idea-2026.2.3`) | `com.intellij.ide.passwordSafe.PasswordSafe.getInstance()`; `CredentialStore.getPassword(CredentialAttributes): String?` and `setPassword(CredentialAttributes, String)`; `com.intellij.credentialStore.generateServiceName(String, String): String`; `Credentials.getPasswordAsString()` | `javap -cp ~/.gradle/…/idea-2026.2.3/lib/intellij.platform.credentialStore.jar com.intellij.ide.passwordSafe.PasswordSafe` etc. |
| `git log -L` emits the §9.2.1 marker format as specified | `\x02<sha>\x1f<parents>\x1f<author>\x1f<ISO date>\x1f<subject>\x1f<body>\x03`, root commit has empty parents | `git log -L 1,3:README.md -s --pretty=format:'%x02%H%x1f%P%x1f%an%x1f%aI%x1f%s%x1f%b%03'` |

Two facts worth carrying into the pitch: **the token is effectively mandatory** (60/hr anonymous,
and the 304-is-free behaviour only applies to authenticated requests), and **our repo already has
one commit with no PR** (`fd8ccbd`, pushed by the template action) — so the “empty `/pulls` → fall
through to issue refs” path is not hypothetical, it is the first thing the demo target will hit.

---

## 2. Repo reality vs the specification

The repo is the **untouched IntelliJ Platform Plugin Template**. `git log` shows three commits
(`Initial commit`, `Template cleanup`, `added specification file (#8)`); there is no `api/`,
`history/`, `settings/`, `cache/`, no Gson, and no `src/main/kotlin/dev/unroll/**`.

| Spec says | Repo actually has | Action |
|---|---|---|
| §5.1 Kotlin 2.1.0, IJ Platform plugin 2.2.1, `intellijIdeaCommunity("2025.2")`, sinceBuild 252 | Kotlin **2.1.20**, platform settings plugin **2.16.0**, `intellijIdea("2025.2.6.2")` (IDEA Ultimate), Gradle wrapper 9.5.0 | Leave as-is for this issue. Bump only Gson in. Dependabot PRs #4/#5/#6 already propose Kotlin 2.4.20 / Gradle 9.7.1 / settings 2.19.0 — merging them is a separate platform commit |
| §5.1 `bundledPlugin("com.intellij.java")`, `org.jetbrains.kotlin`, `Git4Idea`, `JUnit` | none of them; only `testFramework(TestFrameworkType.Platform)` | Not needed for this issue — the GitHub client is pure JVM + Gson |
| §5.2 plugin id `dev.unroll.intellij` | `<id>com.github.mydachi.frictionless</id>`, `<name>frictionless</name>`, one tool window + a post-startup activity | See **Decision 2** — the spec’s id trips the platform verifier |
| §7 `api/Model.kt`, `api/Services.kt`, `api/Fakes.kt` are FROZEN and land in “PR #1, first 45 minutes” | do not exist | This issue needs a **subset** of §7.1 (`IssueRef`, `Comment`, `PullRequestInfo`, `IssueInfo`, `CommitInfo`, `EnrichedCommit`). Task 3 creates exactly that subset, verbatim from the spec, and nothing else |
| §9.1.5 `UnrollSettings` with `githubToken` in `PasswordSafe` | does not exist | Task 4 creates the minimal version (`githubToken`, `maxCommitsEnriched`, `useFakeServices`) |
| §9.1.6 `DiskCache` | does not exist | Task 5 creates it (≈35 lines), with an injectable root so it is unit-testable |
| §4.1 “all four generate a PAT (`public_repo`) and configure it in the plugin’s settings” | no settings UI | Task 16 (A’s) — needed for #7’s DoD, but small |
| Labels `platform`, `unroll`, `alive`, `blast`, `demo-target` (§4.1) | **no labels exist in the repo** | Create them when you write the ~15 issues — cheap, and issue #7 is a `unroll` item |

> **Coordination note.** Per §6/§8, `api/`, `settings/` and `cache/` are Person A’s files. Tasks 3–5
> create them because issue #7 cannot start without them. The cheap, protocol-compliant move is:
> land Tasks 2–5 as one small PR titled `platform: contracts + settings + cache for Unroll   Refs #7`
> and **get A’s eyes on it in review** — that is exactly the “one substantive comment per PR” rule
> from §4.2, and it doubles as demo material.

---

## 3. Decisions to settle before Task 1

### Decision 1 — package/group naming (blocks every file path below)

The spec writes `dev.unroll.*` everywhere (§5.2, §6, §7, §9). The repo has
`com.github.mydachi.frictionless` and `rootProject.name = "IntelliJ Platform Plugin Template"`.

- **Recommended: adopt `dev.unroll.*` now**, in one mechanical commit, *before* four people start
  copy-pasting §7 code samples that reference `dev.unroll.api`. Steps: move
  `src/main/kotlin/com/github/mydachi/frictionless/**` → `src/main/kotlin/dev/unroll/**` (delete the
  template’s `MyBundle.kt`, `MyProjectService.kt`, `MyProjectActivity.kt`,
  `MyToolWindowFactory.kt` — none of it is in §6’s layout), update the two `plugin.xml` extension
  class names, set `group = dev.unroll` in `../gradle.properties`, set `rootProject.name = "unroll"`.
- **Alternative:** keep `com.github.mydachi.frictionless` and substitute it for `dev.unroll` in
  every path in this plan. Costs nothing mechanically; costs a permanent translation step for
  everyone reading §5–§13 as their build contract.

**This plan’s paths assume the recommended option.** No functional difference either way.

### Decision 2 — the plugin id must not be `dev.unroll.intellij`

Verified from the platform verifier’s own source (`intellij-plugin-verifier`, `master`):

```kotlin
// intellij-plugin-structure/structure-intellij/…/verifiers/PluginIdVerifier.kt
val DEFAULT_ILLEGAL_PREFIXES = listOf("com.example", "net.example", "org.example", "edu.example", "com.intellij", "org.jetbrains")
val PRODUCT_ID_RESTRICTED_WORDS = listOf("aqua", "clion", …, "intellij", "qodana", "phpstorm", …)

id.split('.').filter { PRODUCT_ID_RESTRICTED_WORDS.contains(it.lowercase()) }
  .forEach { TemplateWordInPluginId(descriptorPath, id, it) }
```

The check is **per dot-separated component**, so `dev.unroll.intellij` contains the reserved
component `intellij` and raises `TemplateWordInPluginId` (“The plugin ID ‘dev.unroll.intellij’
should not include the word ‘intellij’.”). The class declares `Level.WARNING`, but
`problems/remapping/plugin-problems.json` remaps it to **`error` for the `new-plugin` profile** —
and `VerifyPluginStructureTask.verifyPlugin` fails the build on any `PluginCreationSuccess`-breaking
error or unacceptable warning. Our CI runs `./gradlew verifyPlugin` (`../.github/workflows/build.yml`,
job `verify`), so this is a real (if borderline) red build plus a guaranteed Marketplace blocker.

**Fix:** plugin **id** `dev.unroll.plugin` (or `dev.unroll.idea`). The Kotlin *package* may stay
`dev.unroll.*` — the verifier only looks at the `<id>` element. This is a two-character-classes fix
that saves an afternoon. Note it in the spec as an erratum (§5.2 currently says `dev.unroll.intellij`).

### Decision 3 — §7.1 cannot express “this linked ref is a PR”

`IssueInfo(number, title, body, url, comments)` has no kind field, but §9.2.2 explicitly says a bare
`#123` must be recorded as `ISSUE` and *“let the API disambiguate”*. Disambiguation then has nowhere
to land, so the renderer cannot badge `#29` as an issue vs `#31` as a PR.

- **Recommended:** add `val kind: RefKind` (default `RefKind.ISSUE`) to `api/Model.kt::IssueInfo`.
  Additive with a default, so no call site breaks — but §7 rule 1 says tell the other three first.
- **Fallback if §7 is untouchable today:** keep `IssueInfo` as-is and let the enrichment step
  surface the kind through the existing `warnings` channel — ugly; don’t.

Either way: the internal `EnrichedRef` wrapper used inside this issue carries the kind, so switching
later is a one-line change.

---

## 4. Target design

```
src/main/kotlin/dev/unroll/
├── api/Model.kt              ← Task 3: §7.1 subset (frozen types, verbatim) + IssueInfo.kind
├── settings/UnrollSettings.kt← Task 4: githubToken (PasswordSafe) + maxCommitsEnriched
├── cache/DiskCache.kt        ← Task 5: §9.1.6, injectable root
└── history/
    ├── IssueRefs.kt          ← Task 7: §9.2.2 regexes  (pure)
    ├── GitHubHttp.kt         ← Task 8: HttpTransport seam + JDK 21 implementation
    ├── GitHubClient.kt       ← Tasks 9–11: endpoints, auth, cache/etag, rate limit
    └── GitHubEnricher.kt     ← Tasks 12–14: policy → List<EnrichedCommit> + warnings
```

Three seams, no more (YAGNI):

| Seam | Why it exists | Test double |
|---|---|---|
| `HttpTransport` | so no test ever opens a socket | `FakeTransport` — a `Map<String, HttpResult>` |
| `ResponseCache` | so tests don’t touch `PathManager` | `InMemoryCache` |
| `token: String` passed in, not read by the client | so tests don’t touch `PasswordSafe` | literal `"test-token"` |

Call shape per enriched commit (from §9.2.3’s table, exactly):

```
GET /repos/{o}/{r}/commits/{sha}/pulls          → PR numbers (may be [])
GET /repos/{o}/{r}/pulls/{n}                    → PR detail
GET /repos/{o}/{r}/pulls/{n}/comments?per_page=50  → inline review comments
GET /repos/{o}/{r}/issues/{n}/comments?per_page=50 → PR conversation / issue comments
GET /repos/{o}/{r}/issues/{n}                   → issue detail (and the pull_request check)
```

Governors: **≤ `maxCommitsEnriched` semantic commits** (newest first), **≤ 2 issue refs per commit**,
pool of **4** workers, **10 s** per request, **25 s** wall-clock budget across the whole enrichment,
**every response cached by URL** with a 7-day TTL, ETag/`If-None-Match` on re-runs.

---

## 5. Tasks

### Task 1 — Settle Decision 1 (naming) and move the template out of the way

**Objective:** the repo compiles with the spec’s package layout and none of the template demo code.

**Files:** `../settings.gradle.kts`, `gradle.properties`, `src/main/kotlin/**`, `src/main/resources/META-INF/plugin.xml`, `src/test/kotlin/**`, `README.md`, `CHANGELOG.md`

1. `mkdir -p src/main/kotlin/dev/unroll` and move the four template classes only if you want to keep
   them; per §6 the layout has no `MyToolWindowFactory`, so **delete** `MyBundle.kt`,
   `MyProjectService.kt`, `MyProjectActivity.kt`, `MyToolWindowFactory.kt`,
   `../src/main/resources/messages/MyBundle.properties`, and the template test
   `src/test/kotlin/…/MyPluginTest.kt` (keep `src/test/testData/rename/*` or delete — nothing uses it).
2. `plugin.xml`: drop `<resource-bundle>` and the `<extensions>` block, delete the tool-window and
   post-startup-activity entries; set `<id>dev.unroll.plugin</id>` (Decision 2) and keep
   `<name>Unroll</name>`.
3. `../gradle.properties`: `group = dev.unroll`; `settings.gradle.kts`: `rootProject.name = "unroll"`.
4. Run `JAVA_HOME=$HOME/.local/tools/jdk21 ./gradlew compileKotlin` — expect `BUILD SUCCESSFUL`
   (measured 2026-09-20 22:46 on this machine: **BUILD SUCCESSFUL in 22s**, 1 task executed — the
   IDEA `2025.2.6.2` platform is already in the local Gradle cache, warmed by an earlier build today.
   No large download. Expect ~20–25s per clean compile, not minutes.)
5. Commit: `platform: adopt spec package layout, drop template demo code   Refs #7`

### Task 2 — Add Gson to the build

**Files:** `../build.gradle.kts`

```kotlin
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("com.google.gson:gson:2.11.0")   // ← the only runtime dependency we ship (§5.1)

    intellijPlatform {
        intellijIdea("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)
    }
}
```

Run: `JAVA_HOME=$HOME/.local/tools/jdk21 ./gradlew compileKotlin` → `BUILD SUCCESSFUL`.
Commit: `platform: add gson   Refs #7`

### Task 3 — `api/Model.kt`: the §7.1 subset Unroll needs

**Objective:** the frozen types this issue produces/consumes exist, verbatim from §7.1, with the
Decision-3 addition.

**Files:** Create `src/main/kotlin/dev/unroll/api/Model.kt`

Copy from specification §7.1 exactly: `RefKind`, `IssueRef`, `CommitInfo`, `Comment`,
`PullRequestInfo`, `IssueInfo`, `EnrichedCommit`. Plus `CodeTarget`/`UnrollReport` when you need
them (Task 15) — keep the copy 1:1 so the spec stays the source of truth. Apply the one addition:

```kotlin
data class IssueInfo(
    val number: Int,
    val title: String,
    val body: String,
    val url: String,
    val comments: List<Comment>,
    val kind: RefKind = RefKind.ISSUE      // ← Decision 3 (additive; tell the other three)
)
```

Run: `JAVA_HOME=$HOME/.local/tools/jdk21 ./gradlew compileKotlin` → `BUILD SUCCESSFUL`.
Commit: `platform: add frozen Unroll contracts (api/Model.kt)   Refs #7`

### Task 4 — `settings/UnrollSettings.kt` (token + two knobs)

**Objective:** one place that answers “what is the GitHub token” and “how many commits do we enrich”.

**Files:** Create `src/main/kotlin/dev/unroll/settings/UnrollSettings.kt`

```kotlin
package dev.unroll.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "UnrollSettings", storages = [Storage("unroll.xml")])
class UnrollSettings : PersistentStateComponent<UnrollSettings.State> {

    data class State(
        var maxCommitsEnriched: Int = 6,      // §9.1.5
        var useFakeServices: Boolean = false  // §9.1.5 — the demo parachute
    )

    private var state = State()
    override fun getState() = state
    override fun loadState(s: State) { state = s }

    var maxCommitsEnriched: Int
        get() = state.maxCommitsEnriched
        set(v) { state.maxCommitsEnriched = v }

    var useFakeServices: Boolean
        get() = state.useFakeServices
        set(v) { state.useFakeServices = v }

    /** Never in the XML — PasswordSafe only (§9.1.5). */
    var githubToken: String?
        get() = PasswordSafe.instance.getPassword(githubCredentialAttributes())
        set(value) { PasswordSafe.instance.setPassword(githubCredentialAttributes(), value) }

    companion object {
        /** Pure, so it is unit-testable without a running IDE. */
        fun githubCredentialAttributes(): CredentialAttributes =
            CredentialAttributes(generateServiceName("Unroll", "github"))

        fun getInstance(): UnrollSettings =
            ApplicationManager.getApplication().getService(UnrollSettings::class.java)
    }
}
```

Register in `plugin.xml`:

```xml
<extensions defaultExtensionNs="com.intellij">
  <applicationService serviceImplementation="dev.unroll.settings.UnrollSettings"/>
</extensions>
```

**Test** — Create `src/test/kotlin/dev/unroll/settings/UnrollSettingsTest.kt`. Do **not** touch
`PasswordSafe` in a test (it would hit the OS keychain and be flaky/headless-hostile). Assert only
what is pure:

```kotlin
class UnrollSettingsTest {
    @Test fun `credential attributes are stable for the same inputs`() {
        assertEquals(UnrollSettings.githubCredentialAttributes(),
                     UnrollSettings.githubCredentialAttributes())
    }
    @Test fun `defaults match the spec`() {
        val s = UnrollSettings()
        assertEquals(6, s.maxCommitsEnriched)
        assertEquals(false, s.useFakeServices)
    }
}
```

Run: `JAVA_HOME=$HOME/.local/tools/jdk21 ./gradlew cleanTest test --tests 'dev.unroll.settings.*'`
→ expect `2 tests completed, 2 passed` (read `build/test-results/test/*.xml`, **not** console grep —
an `UP-TO-DATE` task prints nothing and a stdout grep returns a false negative).
Commit: `platform: UnrollSettings with PasswordSafe token   Refs #7`

### Task 5 — `cache/DiskCache.kt` (§9.1.6) + the `ResponseCache` seam

**Objective:** every GitHub body cached by URL, and a cache failure never fails a feature.

**Files:** Create `src/main/kotlin/dev/unroll/cache/DiskCache.kt`

```kotlin
package dev.unroll.cache

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/**
 * §9.1.6. File name = SHA-1(key). Root = <system>/unroll/<repoKey>/<kind>/.
 * `root` is injectable purely so unit tests can point it at a temp dir.
 */
class DiskCache(
    private val repoKey: String,
    private val root: Path = Paths.get(PathManager.getSystemPath(), "unroll"),
    private val now: () -> Instant = Instant::now
) {
    private fun dir(kind: String): Path = root.resolve(repoKey).resolve(kind)

    private fun file(kind: String, key: String): Path =
        dir(kind).resolve(sha1(key) + ".json")

    fun get(kind: String, key: String, ttl: Duration): String? = try {
        val f = file(kind, key)
        if (!Files.exists(f)) null
        else if (Files.getLastModifiedTime(f).toInstant().plus(ttl).isBefore(now())) { Files.deleteIfExists(f); null }
        else Files.readString(f)
    } catch (e: Exception) { thisLogger().warn("Unroll cache read failed", e); null }

    fun put(kind: String, key: String, json: String) {
        try {
            val f = file(kind, key)
            Files.createDirectories(f.parent)
            Files.writeString(f, json)
        } catch (e: Exception) { thisLogger().warn("Unroll cache write failed", e) }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
```

GitHub only needs two methods from it, so declare the seam in `history/`:

```kotlin
// src/main/kotlin/dev/unroll/history/ResponseCache.kt
package dev.unroll.history

import dev.unroll.cache.DiskCache
import java.time.Duration

/** The 2 methods GitHubClient needs. DiskCache satisfies it via the adapter below. */
interface ResponseCache {
    fun get(kind: String, key: String, ttl: Duration): String?
    fun put(kind: String, key: String, json: String)
}

class DiskResponseCache(private val delegate: DiskCache) : ResponseCache {
    override fun get(kind: String, key: String, ttl: Duration) = delegate.get(kind, key, ttl)
    override fun put(kind: String, key: String, json: String) = delegate.put(kind, key, json)
}
```

**Test** — `src/test/kotlin/dev/unroll/cache/DiskCacheTest.kt` with `TemporaryFolder`:

```kotlin
@Test fun `round-trips a value`() { cache.put("pr", "u", """{"a":1}"""); assertEquals("""{"a":1}""", cache.get("pr","u", Duration.ofDays(1))) }
@Test fun `expired entry is a miss`() { /* put with now = T, read with now = T+8d → null */ }
@Test fun `missing parent directories are created`() { /* put into a non-existent root, then assert file exists */ }
@Test fun `a corrupt root does not throw`() { /* root = a regular *file* → get/put return null/no-throw */ }
```

Run: `… ./gradlew cleanTest test --tests 'dev.unroll.cache.*'` → 4 passed.
Commit: `platform: DiskCache for GitHub responses   Refs #7`

### Task 6 — Capture test fixtures for the GitHub payloads

**Objective:** tests that assert against *real* JSON shapes, not shapes we invented.

**Files:** Create `src/test/resources/github/*.json` + `src/test/resources/github/PROVENANCE.md`

```bash
mkdir -p src/test/resources/github
set -H="Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28" \
      -H "Authorization: Bearer $GITHUB_TOKEN" -H "User-Agent: unroll-intellij-plugin"
# our own repo — the shapes the demo target actually produces today
curl -sS $H https://api.github.com/repos/myda-chi/frictionless/commits/75fe54c/pulls      -o src/test/resources/github/commits-75fe54c-pulls.json
curl -sS $H https://api.github.com/repos/myda-chi/frictionless/pulls/8                   -o src/test/resources/github/pull-8.json
curl -sS $H https://api.github.com/repos/myda-chi/frictionless/issues/7                  -o src/test/resources/github/issue-7.json
curl -sS $H https://api.github.com/repos/myda-chi/frictionless/issues/8                  -o src/test/resources/github/issue-8-is-a-pr.json
curl -sS $H https://api.github.com/repos/myda-chi/frictionless/commits/fd8ccbd/pulls      -o src/test/resources/github/commits-fd8ccbd-pulls-empty.json
# an inline review comment, since our repo has none yet (verified empty: PR #8 → 0 comments)
curl -sS $H "https://api.github.com/repos/JetBrains/intellij-platform-gradle-plugin/pulls/2076/comments?per_page=2" \
     -o src/test/resources/github/inline-comments-external.json
```

Record provenance in `PROVENANCE.md` (repo, endpoint, capture date, token-authenticated?) — a fixture
with no provenance is a fixture nobody trusts. Re-capture `inline-comments-*.json` from **our** repo
at Sat 16:00 once the first reviewed PR exists, so the demo’s review-comment path is covered by our
own data. Trim the external file to 1–2 comments so it doesn’t bloat the repo.
Commit: `unroll: github test fixtures   Refs #7`

### Task 7 — `history/IssueRefs.kt` (§9.2.2) (pure, high value)

**Files:** Create `src/main/kotlin/dev/unroll/history/IssueRefs.kt`

```kotlin
package dev.unroll.history

import dev.unroll.api.IssueRef
import dev.unroll.api.RefKind

object IssueRefs {
    val HASH_REF = Regex("""(?i)\b(?:fix(?:e[sd])?|close[sd]?|resolve[sd]?|refs?|see)?\s*#(\d+)\b""")
    val URL_REF  = Regex("""https://github\.com/[^/\s]+/[^/\s]+/(issues|pull)/(\d+)""")

    /** subject + "\n" + body, dedupe by number. First mention wins (URL beats bare hash). */
    fun extract(subject: String, body: String): List<IssueRef> {
        val text = "$subject\n$body"
        val out = LinkedHashMap<Int, IssueRef>()
        URL_REF.findAll(text).forEach { m ->
            val n = m.groupValues[2].toInt()
            val kind = if (m.groupValues[1] == "pull") RefKind.PULL_REQUEST else RefKind.ISSUE
            out.putIfAbsent(n, IssueRef(kind, n, m.value))
        }
        HASH_REF.findAll(text).forEach { m ->
            val n = m.groupValues[1].toInt()
            out.putIfAbsent(n, IssueRef(RefKind.ISSUE, n, "https://github.com/\$owner/\$repo/issues/$n"))
        }
        return out.values.toList()
    }
}
```

`$owner/$repo` is not known here — pass the repo URL in, or let `GitHubClient` build the link
(`owner`/`repo` are already injected there). Simplest: return `IssueRef(…, url = "")` and let the
enricher fill `url` from the API response (`html_url`), which is authoritative anyway.

**Test** — `src/test/kotlin/dev/unroll/history/IssueRefsTest.kt`, table-driven, literal assertions:

| Input | Expected |
|---|---|
| `"html: stop double-escaping (#29)"` | `[29]` |
| `"Fixes #14"` | `[14]` |
| `"Refs #22, refs #9"` | `[22, 9]` |
| `"see https://github.com/myda-chi/frictionless/pull/8"` | `[8]` as `PULL_REQUEST` |
| `"bump to #1.2.3"` / `"issue #0"` / `"C#"` / `"#abc"` | `[]` |
| `"Fixes #14\nRefs #14"` | `[14]` (deduped, once) |
| `"closes #31 and #29"` | `[31, 29]` |

Run: `… ./gradlew cleanTest test --tests 'dev.unroll.history.IssueRefsTest'` → all pass.
Commit: `unroll: extract issue refs from commit messages   Refs #7`

> If §9.2.2 has its own issue, this task is the whole of it — say so on the PR so nobody double-works.

### Task 8 — `history/GitHubHttp.kt`: the transport seam

**Files:** Create `src/main/kotlin/dev/unroll/history/GitHubHttp.kt`

```kotlin
package dev.unroll.history

import com.intellij.openapi.diagnostic.thisLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

fun interface HttpTransport {
    fun get(url: String, headers: Map<String, String>): HttpResult
}

data class HttpResult(
    val status: Int,                    // -1 = transport failure (never a valid HTTP status)
    val body: String = "",
    val etag: String? = null,
    val rateLimitRemaining: Int? = null
)

class JdkHttpTransport(
    private val timeoutPerRequest: Duration = Duration.ofSeconds(10),   // §9.2.3
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
) : HttpTransport {
    override fun get(url: String, headers: Map<String, String>): HttpResult = try {
        val req = HttpRequest.newBuilder(URI.create(url))
            .timeout(timeoutPerRequest)
            .GET()
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        HttpResult(
            status = res.statusCode(),
            body = res.body(),
            etag = res.headers().firstValue("etag").orElse(null),
            rateLimitRemaining = res.headers().firstValue("x-ratelimit-remaining")
                .map(String::toIntOrNull).orElse(null)
        )
    } catch (e: Exception) {                      // timeout, DNS, TLS, interrupt — all one shape
        thisLogger().warn("Unroll: GET $url failed", e)
        HttpResult(status = -1)
    }
}
```

Test: `src/test/kotlin/dev/unroll/history/FakeTransport.kt` (test fixture, not production):

```kotlin
/** Canned responses keyed by URL. Records every request for assertions. */
class FakeTransport(
    private val byUrl: Map<String, HttpResult> = emptyMap(),
    private val default: HttpResult = HttpResult(status = 404)
) : HttpTransport {
    val requests = mutableListOf<Pair<String, Map<String, String>>>()
    override fun get(url: String, headers: Map<String, String>): HttpResult {
        requests += url to headers
        return byUrl[url] ?: default
    }
}
```

(No production behaviour to test here — the seam is verified through Tasks 9–14.)

### Task 9 — `history/GitHubClient.kt`: auth, URLs, cache/ETag, rate limit

**Files:** Create `src/main/kotlin/dev/unroll/history/GitHubClient.kt`

```kotlin
package dev.unroll.history

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.thisLogger
import java.time.Duration

class GitHubClient(
    private val owner: String,
    private val repo: String,
    private val transport: HttpTransport,
    private val token: String,
    private val cache: ResponseCache,
    budgetNanos: Long = Duration.ofSeconds(25).toNanos(),      // §9.2.3
    private val clock: () -> Long = System::nanoTime
) {
    private val gson = Gson()
    private val base = "https://api.github.com"
    private val deadline = clock() + budgetNanos

    /** Observed state, read by the enricher to build warnings. */
    var rateLimited = false; private set
    var tokenRejected = false; private set
    var transportFailures = 0; private set
    var requestCount = 0; private set
    val budgetExhausted get() = clock() > deadline

    private data class Cached(val etag: String?, val body: String)

    private val headers = mapOf(
        "Accept" to "application/vnd.github+json",             // §9.2.3, verified working 2026-09-20
        "X-GitHub-Api-Version" to "2022-11-28",
        "Authorization" to "Bearer $token",
        "User-Agent" to "unroll-intellij-plugin"
    )

    /** GET + disk cache + conditional request. Null on any failure — never throws. */
    private fun json(path: String, kind: String): JsonElement? {
        if (rateLimited || tokenRejected || budgetExhausted) return null
        val url = base + path
        val hit = cache.get(kind, url, TTL)?.let {
            runCatching { gson.fromJson(it, Cached::class.java) }.getOrNull()
        }
        val h = if (hit?.etag != null) headers + ("If-None-Match" to hit.etag!!) else headers
        requestCount++
        val res = transport.get(url, h)

        if (res.rateLimitRemaining != null && res.rateLimitRemaining <= 0) rateLimited = true

        return when {
            res.status == 304 && hit != null -> parse(hit.body)
            res.status in 200..299 -> {
                cache.put(kind, url, gson.toJson(Cached(res.etag, res.body)))
                parse(res.body)
            }
            res.status == -1 -> { transportFailures++; null }
            res.status == 401 -> { tokenRejected = true; null }
            res.status == 403 || res.status == 429 -> { rateLimited = true; null }
            else -> null                                        // 404, 5xx, anything else
        }
    }

    private fun parse(body: String): JsonElement? =
        runCatching { JsonParser.parseString(body) }
            .onFailure { thisLogger().warn("Unroll: malformed GitHub JSON", it) }
            .getOrNull()

    private fun array(path: String, kind: String): List<JsonObject> =
        json(path, kind)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject } ?: emptyList()

    private fun obj(path: String, kind: String): JsonObject? =
        json(path, kind)?.takeIf { it.isJsonObject }?.asJsonObject

    // ── §9.2.3 endpoints, one function each ───────────────────────────────────────────
    fun pullRequestsForCommit(sha: String): List<JsonObject> = array("/repos/$owner/$repo/commits/$sha/pulls", "pr")
    fun pullRequest(number: Int): JsonObject?                = obj("/repos/$owner/$repo/pulls/$number", "pr")
    fun pullRequestReviewComments(number: Int): List<JsonObject> =
        array("/repos/$owner/$repo/pulls/$number/comments?per_page=50", "pr")
    fun issueComments(number: Int): List<JsonObject> =
        array("/repos/$owner/$repo/issues/$number/comments?per_page=50", "issue")
    fun issue(number: Int): JsonObject? = obj("/repos/$owner/$repo/issues/$number", "issue")

    companion object {
        val TTL: Duration = Duration.ofDays(7)                  // §9.1.6: pr/issue = 7 days
        private val REMOTE = Regex("""github\.com[:/]([^/]+)/([^/.]+?)(\.git)?/?$""")   // §9.2.1

        /** "https://github.com/o/r.git" | "git@github.com:o/r.git" → ("o", "r"), else null. */
        fun parseRemote(remoteUrl: String): Pair<String, String>? =
            REMOTE.find(remoteUrl.trim())?.let { it.groupValues[1] to it.groupValues[2] }
    }
}
```

**Tests** — `src/test/kotlin/dev/unroll/history/GitHubClientTest.kt` (all four are cheap and catch the
silent failures):

```kotlin
@Test fun `parses https and ssh remotes`() {
    assertEquals("myda-chi" to "frictionless", GitHubClient.parseRemote("https://github.com/myda-chi/frictionless.git"))
    assertEquals("myda-chi" to "frictionless", GitHubClient.parseRemote("git@github.com:myda-chi/frictionless.git"))
    assertNull(GitHubClient.parseRemote("https://gitlab.com/o/r.git"))
}
@Test fun `sends the spec headers on every request`() {
    val t = FakeTransport(mapOf(url to ok("[]")))
    GitHubClient("o","r",t,"tok",InMemoryCache()).pullRequestsForCommit("abc")
    val h = t.requests.single().second
    assertEquals("application/vnd.github+json", h["Accept"])
    assertEquals("2022-11-28", h["X-GitHub-Api-Version"])
    assertEquals("Bearer tok", h["Authorization"])
    assertEquals("unroll-intellij-plugin", h["User-Agent"])
}
@Test fun `second call is conditional and served from cache on 304`() {
    // 1st: 200 + etag. 2nd: 304 with the same etag and an EMPTY body.
    // Assert: the second request carries If-None-Match, and the parsed result still has the body data.
}
@Test fun `403 with x-ratelimit-remaining 0 sets rateLimited`() { /* and stops further requests: t.requests.size == 1 */ }
@Test fun `401 sets tokenRejected and stops`() { }
@Test fun `404 and 5xx return null but do not stop`() { }
@Test fun `budget exhaustion stops before the first request`() { /* budgetNanos = 0 */ }
```

Run: `… ./gradlew cleanTest test --tests 'dev.unroll.history.GitHubClientTest'` → all pass.
Commit: `unroll: GitHub REST client with caching and rate-limit handling   Refs #7`

### Task 10 — JSON → model mapping (pure functions, own test file)

**Files:** Create `src/main/kotlin/dev/unroll/history/GitHubMapping.kt`

```kotlin
package dev.unroll.history

import com.google.gson.JsonObject
import dev.unroll.api.Comment
import dev.unroll.api.IssueInfo
import dev.unroll.api.PullRequestInfo
import dev.unroll.api.RefKind
import java.time.Instant

internal fun JsonObject.str(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
internal fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

/** §7.1 Comment. inlineOn: "Html.kt:14" — falls back to original_line for outdated comments. */
internal fun toComment(o: JsonObject): Comment = Comment(
    author = o.obj("user")?.str("login") ?: "unknown",
    body = o.str("body") ?: "",
    createdAt = runCatching { Instant.parse(o.str("created_at")) }.getOrNull() ?: Instant.EPOCH,
    inlineOn = o.str("path")?.let { p ->
        val line = o.str("line") ?: o.str("original_line")
        if (line == null) p else "$p:$line"
    }
)

internal fun toPullRequest(o: JsonObject): PullRequestInfo? {
    val n = o.str("number")?.toIntOrNull() ?: return null
    return PullRequestInfo(
        number = n, title = o.str("title") ?: "", body = o.str("body") ?: "",
        url = o.str("html_url") ?: "", author = o.obj("user")?.str("login") ?: "unknown",
        reviewComments = emptyList(), conversation = emptyList()   // filled by the enricher
    )
}

internal fun toIssue(o: JsonObject): IssueInfo? {
    val n = o.str("number")?.toIntOrNull() ?: return null
    return IssueInfo(
        number = n, title = o.str("title") ?: "", body = o.str("body") ?: "",
        url = o.str("html_url") ?: "", comments = emptyList(),
        kind = if (o.has("pull_request")) RefKind.PULL_REQUEST else RefKind.ISSUE
    )
}
```

Fixtures make this exact: `/issues/8` has `pull_request` (verified), `/issues/7` does not (verified),
and the external inline-comment fixture has `line=406` / `original_line=393` — assert
`inlineOn == "<path>:406"`, then a second fixture variant with `"line": null` → `…:393`.

Run: `… ./gradlew cleanTest test --tests 'dev.unroll.history.GitHubMappingTest'` → all pass.
Commit: `unroll: map GitHub JSON to the frozen model   Refs #7`

### Task 11 — `history/GitHubEnricher.kt`: the policy layer

**Objective:** commits in → `List<EnrichedCommit>` out, with the §9.2.3 caps and the §11 warnings.

**Files:** Create `src/main/kotlin/dev/unroll/history/GitHubEnricher.kt`

```kotlin
package dev.unroll.history

import com.intellij.openapi.progress.ProgressIndicator
import dev.unroll.api.*
import dev.unroll.settings.UnrollSettings
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class EnrichmentResult(val commits: List<EnrichedCommit>, val warnings: List<String>)

class GitHubEnricher(
    private val owner: String,
    private val repo: String,
    private val token: String?,
    private val transport: HttpTransport,
    private val cache: ResponseCache,
    private val settings: UnrollSettings,
    private val pool: Int = 4                       // §9.2.3: fixed pool of 4
) {
    fun enrich(commits: List<CommitInfo>, indicator: ProgressIndicator? = null): EnrichmentResult {
        if (token.isNullOrBlank()) {
            return EnrichmentResult(commits.map { EnrichedCommit(it, null, emptyList()) },
                listOf("Add a GitHub token in Settings → Tools → Unroll."))     // §11 verbatim
        }
        val client = GitHubClient(owner, repo, transport, token, cache)
        val targets = commits.filter { it.isSemantic }.take(settings.maxCommitsEnriched).map { it.sha }.toSet()

        val executor: ExecutorService = Executors.newFixedThreadPool(pool)
        val results: Map<String, EnrichedCommit> = try {
            executor.invokeAll(
                commits.map { c ->
                    Callable {
                        indicator?.checkCanceled()
                        if (c.sha !in targets || client.budgetExhausted) c.sha to EnrichedCommit(c, null, emptyList())
                        else c.sha to enrichOne(client, c)
                    }
                },
                WALL_CLOCK_BUDGET_SECONDS, TimeUnit.SECONDS     // §9.2.3: 25 s overall, then partial
            ).mapNotNull { it.getOrNull() }.toMap()
        } catch (e: Exception) {
            emptyMap()
        } finally { executor.shutdownNow() }

        val ordered = commits.map { results[it.sha] ?: EnrichedCommit(it, null, emptyList()) }
        return EnrichmentResult(ordered, warnings(client, ordered.size))
    }

    private fun enrichOne(client: GitHubClient, c: CommitInfo): EnrichedCommit {
        val pr = client.pullRequestsForCommit(c.sha).firstOrNull()?.let { toPullRequest(it) }
        val filled = pr?.let {
            val n = it.number
            it.copy(
                reviewComments = client.pullRequestReviewComments(n).map(::toComment),
                conversation = client.issueComments(n).map(::toComment)
            )
        }
        val refs = IssueRefs.extract(c.subject, c.body)
            .filter { it.number != filled?.number }        // don't fetch the commit's own PR twice
            .distinctBy { it.number }
            .take(2)                                       // keep the call count near §9.2.3's ≈24
        val issues = refs.mapNotNull { ref ->
            val o = client.issue(ref.number) ?: return@mapNotNull null
            toIssue(o)?.copy(comments = client.issueComments(ref.number).map(::toComment))
        }
        return EnrichedCommit(c, filled, issues)
    }

    private fun warnings(client: GitHubClient, commitCount: Int): List<String> = buildList {
        if (client.tokenRejected) add("GitHub token rejected — check Settings → Tools → Unroll.")
        if (client.rateLimited) add("GitHub rate limit reached — showing what we have.")     // §11
        if (client.transportFailures > 0 && client.requestCount == client.transportFailures)
            add("GitHub unreachable — showing commit history only.")                          // new copy (§11 “Offline”)
        if (client.budgetExhausted) add("GitHub call timed out — showing what we have.")       // new copy
    }

    companion object {
        /** §9.2.3: 25 s overall budget, then return partial. */
        const val WALL_CLOCK_BUDGET_SECONDS: Long = 25
    }
}
```

Two of those strings are **new copy** not in §11 (token-rejected, timed-out). §11 has “Offline”
without literal text. Run them past A before merging so the copy stays consistent on stage.

**Tests** — `src/test/kotlin/dev/unroll/history/GitHubEnricherTest.kt`:

| Test | Assertion |
|---|---|
| `no token means no HTTP calls` | `transport.requests.isEmpty()` and warnings == `["Add a GitHub token in Settings → Tools → Unroll."]` |
| `skips non-semantic commits` | with `maxCommitsEnriched = 1`, a non-semantic newest commit → the *next* semantic one is enriched |
| `caps at maxCommitsEnriched` | 3 semantic commits, cap 1 → exactly 1 PR fetch (count requests by URL prefix) |
| `direct commit falls through to issue refs` | `commits/{sha}/pulls → []`, message `"fix double-escaping   Refs #29"` → `issues.size == 1`, `pullRequest == null` |
| `bare hash that is a PR is disambiguated` | fixture `issue-8-is-a-pr.json` → `kind == PULL_REQUEST` |
| `dedupes the PR’s own number from issue refs` | message `"… (#8)"` with PR #8 → issue #8 fetched once, not twice |
| `rate limit stops the run and warns` | 403 + `x-ratelimit-remaining: 0` → warning equals the §11 string, no further requests |
| `timeout on transport degrades to timeline` | `HttpResult(-1)` → `pullRequest == null`, no exception, warning present |
| `never throws on malformed JSON` | body `"<html>502</html>"` → `null`, no exception |

Run: `… ./gradlew cleanTest test --tests 'dev.unroll.history.*'` → all pass.
Commit: `unroll: enrich commits with PR and issue metadata   Refs #7`

### Task 12 — Token/remote guards and the “no GitHub remote” path

**Files:** Modify `src/main/kotlin/dev/unroll/history/GitHubEnricher.kt` (or the `HistoryService` call site)

```kotlin
// in GitHistoryService.unroll(), after git log:
val remote = runGit("remote", "get-url", "origin").stdout.trim()
val (owner, repo) = GitHubClient.parseRemote(remote)
    ?: return UnrollReport(target, commits.map { EnrichedCommit(it, null, emptyList()) }, truncated,
                           warnings = listOf("No GitHub remote — showing commit history only."))  // §11 verbatim

val result = GitHubEnricher(owner, repo, settings.githubToken, JdkHttpTransport(), DiskResponseCache(cache), settings)
    .enrich(commits, indicator)
return UnrollReport(target, result.commits, truncated, warnings = result.warnings)
```

Tests: `parseRemote` table (Task 9) plus one enricher test asserting the exact §11 copy.
Commit: `unroll: degrade cleanly without a GitHub remote   Refs #7`

### Task 13 — Wire it into the action path (B’s `GitHistoryService`)

**Objective:** the client actually runs, on the right thread, and never on the EDT (§10).

**Files:** Modify `src/main/kotlin/dev/unroll/history/GitHistoryService.kt`; the action already wraps
`services.history(project).unroll(target, indicator)` inside `Task.Backgroundable` (§9.1.2).

Checks to assert in review (no unit test can prove this — say it plainly in the PR):
- [ ] No `java.net.http` call reachable from `actionPerformed` outside `Task.Backgroundable.run`
- [ ] `GitHubClient` is constructed inside `run()`, never in a service field
- [ ] `executor.shutdownNow()` in a `finally`
- [ ] `indicator.checkCanceled()` inside the per-commit `Callable`

If `GitLog` (§9.2.1) hasn’t merged yet, land this task behind
`settings.useFakeServices` with the §3.2 fixture commits so the path is exercised end to end.
Commit: `unroll: run github enrichment in background   Refs #7`

### Task 14 — Live smoke test (opt-in, never in CI)

**Files:** Create `src/test/kotlin/dev/unroll/history/GitHubClientLiveTest.kt`

```kotlin
@Ignore("Hits the real GitHub API. Run deliberately: GITHUB_TOKEN=<pat> ./gradlew test --tests '*GitHubClientLiveTest'")
class GitHubClientLiveTest {
    @Test fun `real repo resolves the spec PR for commit 75fe54c`() {
        val token = System.getenv("GITHUB_TOKEN") ?: return     // skip when unset
        val client = GitHubClient("myda-chi", "frictionless", JdkHttpTransport(), token, InMemoryCache())
        assertEquals(8, client.pullRequestsForCommit("75fe54c").first()["number"].asInt)
        assertNull(client.pullRequestsForCommit("fd8ccbd").firstOrNull())   // direct commit → []
    }
}
```

Run (deliberate): `GITHUB_TOKEN=$(cat ~/.unroll-pat) JAVA_HOME=$HOME/.local/tools/jdk21 ./gradlew test --tests '*GitHubClientLiveTest'`
Expected with `@Ignore` on: `SKIPPED`; with the token exported and the annotation removed: 2 assertions pass.
Note in the commit message that `./gradlew check` in CI must stay green **without** network or a token.
Commit: `unroll: opt-in live smoke test for the github client   Refs #7`

### Task 15 — Settings UI: paste the token (A’s, but #7’s DoD needs it)

**Files:** Create `src/main/kotlin/dev/unroll/settings/UnrollConfigurable.kt`; register in `plugin.xml`

```xml
<applicationConfigurable parentId="tools" instance="dev.unroll.settings.UnrollConfigurable"
                         id="dev.unroll.settings" displayName="Unroll"/>
```

UI: a `JPasswordField` for the GitHub token (write through `UnrollSettings.githubToken`), a spinner for
`maxCommitsEnriched`, a checkbox for `useFakeServices`, and the §9.1.5 disclaimer line. No network call
in the configurable — validate the token by saving, not by calling `/user`.
Commit: `platform: settings UI for the github token   Refs #7`

### Task 16 — Verification pass

```bash
export JAVA_HOME=$HOME/.local/tools/jdk21
cd ~/Projects/frictionless

# 1. unit tests, forced to run (never trust an UP-TO-DATE no-op)
./gradlew cleanTest test --tests 'dev.unroll.*'
python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET
t = f = s = 0
for x in glob.glob('build/test-results/test/*.xml'):
    r = ET.parse(x).getroot(); t += int(r.get('tests', 0)); f += int(r.get('failures', 0)) + int(r.get('errors', 0)); s += int(r.get('skipped', 0))
print(f"tests={t} failures={f} skipped={s}")
EOF
# expected: failures=0, skipped=0 (the live test is @Ignore'd → it shows as skipped only when run)

# 2. packaged + structure-validated (this is the rung that proves plugin.xml is legal)
./gradlew buildPlugin verifyPluginStructure
unzip -l build/distributions/*.zip | head
# expected: BUILD SUCCESSFUL, no TemplateWordInPluginId / ForbiddenPluginIdPrefix problems,
#           build/distributions/unroll-0.0.1.zip contains dev/unroll/history/GitHubClient.class
```

Report the rung honestly (per the confidence ladder): **Compiles → Packaged → Validated → Live**.
Task 16 reaches *Validated*. **Live** needs `./gradlew runIde` on a machine with a display and a real
PAT — do that once before the demo and say so, rather than implying CI covered it.

---

## 6. Acceptance criteria (mirrors the §9.2 DoD)

1. On our own repo’s demo target: **≥2 commits enriched with an issue or PR title**, each linking to
   `github.com/myda-chi/frictionless/...`.
2. The commit that links `#29` shows the **PR** it came from (not just the issue).
3. No token → zero HTTP requests, timeline still renders, exact §11 copy shown.
4. Rate-limited / offline → partial timeline + warning, never a modal, never an exception.
5. ≤ `maxCommitsEnriched` commits enriched; non-semantic commits skipped; re-running the action
   twice sends **0 requests** the second time (cache hit) — verify with a request counter.
6. `./gradlew check` green with **no network access and no token** (CI runs it that way).
7. All I/O off the EDT; the IDE never freezes on a cold cache (25 s worst case, cancellable).

---

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Plugin id `dev.unroll.intellij` fails `verifyPlugin` (Decision 2) | High if copied from §5.2 | Use `dev.unroll.plugin`; add an erratum to §5.2 |
| A’s `api/`, `settings/`, `cache/` never land and B blocks | Medium | Tasks 3–5 are the minimum; land them as one `platform:` PR **today** and say so in the group chat |
| Anonymous 60/hr burns out on a demo machine (measured: ~13 used in a normal afternoon) | Low | Token is mandatory by design (§11); pre-warm the disk cache Sat night + Sun morning (§14) |
| Call count creeps past §9.2.3’s “≈24”: 6 commits × (1 pulls + 1 pr + 2 comment lists + 2 refs × 2) = 48 | Medium | 25 s wall-clock budget is the real governor; cap 2 refs/commit; dedupe the PR’s own number; cache by URL |
| `original_line` vs `line` makes review comments point at the wrong line | Medium | Task 10 test uses the real fixture where `line=406`/`original_line=393` |
| ~~First build downloads ~1–2 GB of IDEA 2025.2.6.2~~ **struck — claim was wrong** | n/a | Measured 2026-09-20 22:46: `compileKotlin` = **BUILD SUCCESSFUL in 22s**. The platform is already cached; no download occurred. Still: compile once before the weekend, not at 18:00 |
| Kotlin/`java.net.http` on the platform classpath conflicts | Low | JDK 21 module, zero third-party deps apart from Gson (§5.1) |
| `PasswordSafe` unavailable/headless (Linux keychain) | Low | Token is read once and passed in; unit tests never touch `PasswordSafe`; `isMemoryOnly()` is the tell |
| Fixtures captured externally drift from our repo’s shapes | Low | `PROVENANCE.md` + re-capture at Sat 16:00 from our own reviewed PR |
| Grepping Gradle stdout gives false negatives on skipped tasks | Medium | Always parse `build/test-results/test/*.xml` or force with `cleanTest` (Task 16 does both) |

---

## 8. Open questions (need a human answer, not a guess)

1. **Decision 1** — adopt `dev.unroll.*` or keep `com.github.mydachi.frictionless`?
2. **Decision 3** — may `IssueInfo` gain `kind: RefKind`, or must §7 stay frozen (renderer loses the
   PR/issue badge)?
3. Do we fetch PR **reviews** (`/pulls/{n}/reviews` — approve/request-changes bodies)? §9.2.3 doesn’t
   list it and §3.2’s “Review · 4 comments” maps to inline comments. Default: **no**.
4. Is `IssueRefs` (§9.2.2) inside issue #7 or its own issue? This plan assumes inside, because the
   client needs it to know which issue numbers to fetch.
5. Two warning strings (token-rejected, call-timed-out) are not in §11 — approve or give me better copy.
6. `maxRefsPerCommit = 2` is my invention to keep the call count near §9.2.3’s arithmetic. OK?

---

## 9. Deliberately out of scope

`GitLog`/`git log -L` parsing (§9.2.1), `UnrollRenderer` (§9.2.4) including the top-3-comment
priority and `+N older commits`, `Synthesis` (§9.5), Alive (§9.3), Blast Radius (§9.4),
`api/Fakes.kt` (§7.3), the `DiskCache` TTL policy review, dependabot bumps, and the ~15-issue
write-up from §4.1.
