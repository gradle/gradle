# Prototype: Maven settings.xml mirror support

Status: design note + spike. Not for merge. Feature flag: `org.gradle.internal.mavenMirrors` (Gradle property, off by default).

Scope of this cut:

- Parse mirrors from Maven `settings.xml` only (user + global, as already merged by `DefaultMavenSettingsProvider`).
- The full Maven `mirrorOf` grammar is honored (see the "mirrorOf matching" section): `*`, exact ids, `!id` negation, comma-separated lists, `external:*` and `external:http:*`, matched per repository against an "effective id" (`central` for Maven Central's URL, the Gradle repository name otherwise).
- Only remote `MavenArtifactRepository` instances are rewritten. Ivy, flat-dir and `mavenLocal()` are untouched.

## 1. Where to hook

**Chosen: (b) — inside `DefaultMavenArtifactRepository`, at the point the URL is finalized for use (`validateUrl()`).**

A correction to the brief first: the repository URL is *not* a `Property<URI>`. It is a plain mutable `Object` held by `DefaultUrlArtifactRepository`, resolved to a `URI` on every `getUrl()` call via `FileResolver.resolveUri`. There is no freezing/finalization semantics to fight, but there is also no single "set" moment to intercept — the backing object is live and can be mutated any time before resolution. That rules out rewriting in `setUrl(...)` (option (b)-at-construction): a later `setUrl` would silently undo the mirror, and `mavenCentral()` calls `setUrl` *after* the constructor runs anyway (`DefaultBaseRepositoryFactory.createMavenCentralRepository`).

The actual "URL is final now" moments are the two call sites of `validateUrl()`:

- `createDescriptor()` — builds the `MavenRepositoryDescriptor` (repo identity, build scans),
- `createResolver()` — builds the `MavenResolver` that performs network access.

Hooking `validateUrl()` covers both consistently: the descriptor and the resolver see the same (mirrored) URL. This is option (b) in spirit — central to Maven repos specifically, one class, no new decorator layers.

Why not the others:

- (a) DSL-add time (`DefaultRepositoryHandler`): misses repos added via `BaseRepositoryFactory` from other code paths (plugin portal, init scripts, `dependencyResolutionManagement`), and suffers the same "later `setUrl` undoes it" problem.
- (c) `RepositoryTransportFactory` decorator: transports are created per *scheme*, not per URL — the factory never sees the URL being fetched; rewriting would have to happen per-resource inside `ExternalResourceConnector`, which is much more invasive. It would help auth follow the mirror, but that's explicitly out of scope.
- (d) lifecycle post-processor walking `project.repositories`: fragile ordering (repos can be added after any chosen hook point), doesn't cover settings/init-script repositories, and mutating user-visible repository state behind the user's back is observable from build logic.

Known imperfections of the chosen hook (accepted for the prototype):

- `getUrl()` (the public DSL getter) still returns the *original* URL. Arguably a feature: user config stays what the user wrote; the rewrite happens at use time and is logged.
- Inside `createResolver()`, one secondary use of `getUrl()` (injector for component metadata suppliers/listers) still sees the original URL.
- The `HttpRedirectVerifier` (insecure-protocol enforcement) is built from the *original* URL, so an `http://` mirror of an `https://` repo bypasses the insecure-protocol check. The prototype logs a warning when the mirror URL is not `https`.

## 2. Timing of the settings.xml parse

`DefaultMavenSettingsProvider.buildSettings()` is documented as expensive. The prototype therefore:

- registers `MavenMirrorResolver` as a **build-scoped service** (`DependencyManagementBuildScopeServices`, right next to `MavenSettingsProvider`), and
- computes the wildcard mirror **lazily, once, on first `mirrorFor()` call**, caching the result for the rest of the build.

The feature-flag check happens *before* the parse: with the flag off (the default), `settings.xml` is never read and the overhead is one property lookup. `mirrorFor()` is only called from `validateUrl()`, i.e. only when a Maven repository is actually used, so builds without Maven repos never parse settings either.

```java
@ServiceScope(Scope.Build.class)
public interface MavenMirrorResolver {
    Optional<MirroredRepository> mirrorFor(URI original);
}
```

### Why build scope and not build-tree scope

Build scope means the parse and its memoized result are per build in the tree, so a
composite parses `settings.xml` once per included build that declares a Maven repository.
Build-tree scope would parse once for the whole tree.

`MavenMirrorResolver` cannot simply move, though: it injects `ProviderFactory` for the
configuration cache input, and both `ProviderFactory` and the underlying
`ValueSourceProviderFactory` are `@ServiceScope(Scope.Build.class)`, registered in
`BuildScopeServices`. Since `Scope.Build extends Scope.BuildTree`, a build-tree scoped
service cannot see them. The fingerprint they ultimately write to is already tree-wide
(`ConfigurationCacheFingerprintController` is build-tree scoped) — it is only the API for
reaching it that is build scoped, and dependency-management cannot reach the controller
directly.

Tree-wide caching therefore means splitting along that seam: a build-tree scoped holder
owning the parse and the memoized mirror list, plus a thin build-scoped
`MavenMirrorResolver` that delegates to it and registers the CC input through its own
`ProviderFactory`. Registering the value source once per build is harmless, since the
fingerprint is tree-scoped and dedupes.

Left at build scope for this cut. Widening only `MavenSettingsProvider` would not help —
it is stateless and re-parses on every `buildSettings()` call, so all the memoization
lives in the resolver. And `LocalMavenRepositoryLocator` is build scoped on `master` and
already reads settings per build, so per-build settings reading is the existing
convention rather than something this prototype introduces. Worth revisiting with a
measurement from a large composite.

## 3. Repository identity

`createDescriptor()` goes through the same `validateUrl()`, so the descriptor's root URI is the *mirror* URL. Consequences:

- Build scans / build operations report the mirror URL as the repository root, but the repository *name* stays e.g. `MavenRepo`. So scans show `MavenRepo` with an unexpected URL — confusing but truthful.
- Dependency-resolution caches (`ModuleRepositoryCaches`) key by a repository id derived from the descriptor, which includes the URL. Turning the mirror on/off therefore changes the repository identity and produces cache misses (re-downloads). That is arguably *correct* — the artifacts genuinely come from elsewhere — but it means toggling the flag is not free.
- `NamedMavenRepositoryDescriber` ("MavenRepo", "Google" display names) compares `getUrl()` against the default URL; since `getUrl()` is untouched, display names are stable.

Not fixed in this cut; noted for the real design (a mirror-aware descriptor should probably carry both original and mirror URL).

## 4. Authentication

### No leak of the original repository's credentials (done in this cut)

The initial analysis feared the original repo's credentials would be sent to the mirror host. Reading the transport code corrects that: Gradle already **host-scopes** HTTP credentials. `AbstractAuthenticationSupportedRepository.getConfiguredAuthentication()` attaches the hosts of `getRepositoryUrls()` — the *original*, un-mirrored URL — to each authentication, and `HttpClientConfigurer` registers credentials in HttpClient's `CredentialsProvider` under `AuthScope(host, port)`. The preemptive-auth interceptor also looks credentials up by the *target* host. So a mirror on a **different host never receives the original credentials**, even before this change.

That protection is implicit and incomplete, though:

- a mirror on the *same host and port* (different path) would still match the `AuthScope`, and Maven semantics say the original server's credentials don't apply to a mirror at all;
- it depends on HttpClient internals staying host-scoped;
- the failure mode is a silent 401 with no hint of why credentials were "lost".

So this cut makes the rule explicit: when the mirror applies, `DefaultMavenArtifactRepository.getTransport()` strips **all** configured authentication from the transport and logs:

```
Ignoring credentials configured for repository '<name>': its URL is rewritten by Maven mirror '<id>' and the repository credentials do not apply to the mirror.
```

The integration test proves non-leakage behaviorally: the mirror *requires* exactly the credentials configured on the original repository — resolution fails, so they were never sent.

Residual notes: the repository descriptor still reports `authenticated = true` (it describes the definition, not the effective transport) — build-scan reporting needs a decision in the real design. The `HttpRedirectVerifier` original-URL issue from section 1 still stands.

### Options for supplying the mirror's own credentials

Maven's model: a `<server>` entry in settings.xml whose `<id>` equals the mirror id supplies username/password. Passwords may be encrypted: `mvn --encrypt-password` produces `{...}`-wrapped values, decryptable with the master password stored in `~/.m2/settings-security.xml` (relocatable via `-Dsettings.security`, supports `<relocation>`). The master password itself is "encrypted" with a **fixed, well-known key** — anyone who can read `settings-security.xml` can recover every password. It is obfuscation against shoulder-surfing and accidental pastes, not protection against an attacker with file access. Maven 4 replaces this scheme (sec-dispatcher 4.x with pluggable dispatchers: GPG agent, prompts, env variables), so any implementation should isolate the decryption behind an interface.

**Option A — read the `<server>` matching the mirror id, apply as `PasswordCredentials`.**

- `MavenMirrorResolver` grows `credentialsFor(mirrorId)`; `Settings.getServers()` is already parsed by `MavenSettingsProvider`, so no new parsing.
- In `getAuthenticationsForUrlInUse()`, instead of returning an empty collection, return a `BasicAuthentication` carrying the server's username/password, host-scoped to the **mirror** host via `AuthenticationInternal.addHost(...)`.
- Decryption comes for free dependency-wise: the dependency-management runtime classpath already ships what Maven itself uses — `org.apache.maven.settings.crypto.DefaultSettingsDecrypter` (maven-settings-builder 3.9.5) backed by `org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher` + plexus-cipher 2.0. Non-`{...}` values pass through as plaintext; `{...}` values decrypt against `settings-security.xml` (honoring `-Dsettings.security`). A failed decryption should warn at lifecycle and resolve *without* credentials (deterministic 401) rather than send the undecrypted blob.
- CC story is already in place: the server entries live in settings.xml, whose checksum is a CC input while the feature flag is on. `settings-security.xml` would need adding to the same checksum value source.
- Limits: HTTP basic auth only (no wagon `privateKey`/scp, no NTLM config, no `<configuration>` HTTP headers). Fine for the realistic use case (Artifactory/Nexus/Sonatype mirrors).

**Beyond basic auth: what Maven's `<server>` can declare.** The settings.xml server model (verified against the `maven-settings` 3.9.5 jar Gradle ships) is: `username`/`password`, `privateKey`/`passphrase`, `filePermissions`/`directoryPermissions`, and a free-form `configuration` block. What that means for auth:

- **Per-server HTTP headers** — `<server><configuration><httpHeaders>` declares arbitrary headers for a server id; the idiomatic Maven way to do token/bearer auth (GitHub Packages, GitLab, cloud registries). Honored by both wagon-http and the native resolver transport that is the default since Maven 3.9. Gradle has a 1:1 counterpart (`HttpHeaderCredentials` + `HttpHeaderAuthentication`), and the prototype maps it (see Option C below). Two caveats: `<configuration>` is not a schema (`Server.getConfiguration()` returns untyped XML originally interpreted by the wagon in use; the native transport honors only a subset, `httpHeaders` being the reliable one), so support is explicitly best-effort; and Maven itself never decrypts configuration values — only `password` and `passphrase` — so encrypted header values do not work in Maven. The prototype decrypts them anyway, as a strict improvement (an encrypted value would be sent literally by Maven, i.e. broken there regardless).
- **Digest/NTLM are negotiated, not declared** — Maven's HTTP layer answers whatever challenge the server sends using the same `username`/`password` (NTLM domain conventionally as `DOMAIN\user`). Gradle behaves identically (`PasswordCredentials` with the default `AllSchemesAuthentication`), so no extra declarative surface is needed on either side.
- **SSH keys** (`privateKey`/`passphrase` and the permission fields) exist for the scp/sftp deployment wagons — irrelevant for HTTP mirrors.
- **Mutual TLS is not declarative** — client certificates are JVM-global system properties (`javax.net.ssl.keyStore` in `MAVEN_OPTS`/`.mvn/jvm.config`), never per-server in settings.xml. Nothing to map; Gradle is in the same position.

The realistic parity target for Option A is therefore: `username`/`password` (with decryption) covering basic/digest/NTLM, plus best-effort `<httpHeaders>` → `HttpHeaderCredentials` for token-authenticated mirrors.

**Option B — Gradle-native credential lookup keyed by the mirror id.**

- Reuse the existing `CredentialsProviderFactory` convention that backs `credentials(PasswordCredentials.class)`: Gradle properties `<mirrorId>Username` / `<mirrorId>Password`, typically set in `~/.gradle/gradle.properties` or via `ORG_GRADLE_PROJECT_*` environment variables.
- CC-safe by construction, zero crypto code, no dependence on Maven's weak encryption, and secrets live where Gradle users already keep them.
- Downside: no parity — a team pointing Gradle at an existing settings.xml must duplicate credentials, which undercuts the "works with your existing Maven setup" pitch of the feature.

**Option C — hybrid (recommended, implemented in this cut).** Option A as the default for Maven parity, with Option B as an override that wins when the Gradle properties are present. The override doubles as the escape hatch for Maven 4's new encryption format and for users who refuse to keep secrets in settings.xml.

How the prototype implements it:

- `MirroredRepository` carries optional `MirrorCredentials`, resolved once with the wildcard mirror. Precedence: `<mirrorId>Username`/`<mirrorId>Password` Gradle properties (both must be set; a partial pair warns and is ignored) > the settings.xml `<server>` entry whose id matches the mirror id > none.
- Passwords from the `<server>` entry go through `DefaultSecDispatcher` + `DefaultPlexusCipher` — the same classes Maven uses, already shipped in the distribution. Plaintext values pass through without touching settings-security.xml; `{...}`-wrapped values decrypt against the master password in `~/.m2/settings-security.xml` (the `settings.security` system property relocates it, as in Maven). A failed decryption logs a lifecycle warning naming the `<mirrorId>Username`/`<mirrorId>Password` remedy and the mirror is used *without* credentials (deterministic 401 instead of sending the undecrypted blob).
- `DefaultMavenArtifactRepository` wraps the credentials in an `AllSchemesAuthentication` — the same type Gradle uses for plain `credentials {}` blocks, so basic/digest/NTLM negotiation works — host-scoped to the **mirror** host only.
- When the matching `<server>` entry has no username/password but declares `<configuration><httpHeaders>`, the first header is applied as an `HttpHeaderAuthentication`/`HttpHeaderCredentials` pair (token auth), also host-scoped to the mirror. Only the first header can be honored — Gradle's transport attaches one header credential per host — so additional headers log a lifecycle warning and are ignored. Malformed header configuration (missing name or value) warns and is skipped. Username/password on the same server entry win over headers.
- CC: the Gradle property reads are tracked by `ProviderFactory`; settings-security.xml content is folded into the existing `MavenSettingsChecksumValueSource` (only fingerprinted while the feature flag is on).
- Verified by unit tests (property override, partial-pair fallback, real decryption against a generated settings-security.xml, decryption-failure fallback) and integration tests against a BASIC-auth mirror (plaintext server entry, encrypted server entry, property override), in both forking and configuration-cache variants.

Not applicable: interactive password prompting (Maven can prompt; the Gradle daemon is non-interactive).

## 5. `mavenLocal()` interaction

`DefaultMavenLocalArtifactRepository extends DefaultMavenArtifactRepository` and its `createResolver()` calls the inherited `validateUrl()` — so it *would* inherit mirror rewriting if nothing were done. Two guards make the exclusion airtight:

1. `DefaultBaseRepositoryFactory.createMavenLocalRepository()` constructs the local repo via the `DefaultMavenLocalArtifactRepository` constructor, which passes a `null` mirror resolver to the parent — mirroring is structurally impossible for `mavenLocal()` regardless of URL.
2. Defense in depth: the resolver only rewrites `http`/`https` URLs. `mavenLocal()` is always a `file://` URL, as is any other file-based Maven repo. (Maven's own `*` matches file repos too, but rewriting local paths to a remote mirror is more surprising than useful; `external:*` semantics are a follow-up anyway.)

## 6. Configuration cache

Two inputs need to be CC-safe; both are handled in this cut:

- **The feature flag** — read via `ProviderFactory.gradleProperty("org.gradle.internal.mavenMirrors")`. The build-scoped `ProviderFactory` (registered in `BuildScopeServices`) is injected into the resolver service. Gradle-property reads through `ProviderFactory` are tracked CC inputs.
- **The settings.xml contents** — tracked via `MavenSettingsChecksumValueSource`, a `ValueSource` that hashes the user and global settings.xml files (without parsing them). The resolver obtains it *only when the feature flag is on*, so builds with the flag off carry no settings.xml entry in their CC fingerprint. Value sources are re-evaluated at cache-load time, so editing settings.xml invalidates the cached configuration with the reason `Maven settings.xml content has changed` (the value source implements `Describable`).

Design choices worth noting:

- The value source returns a *checksum*, not the parsed mirror. The fingerprint check on every CC-hit build therefore only hashes two small files; the expensive `buildSettings()` parse still happens at most once per build and only when a Maven repository URL is actually finalized.
- Value sources cannot inject build-scoped services, so the value source constructs `DefaultMavenFileLocations` itself to locate the files. This duplicates the location logic entry point (not the logic), and means the `M2_HOME`/`user.home` inputs behind it are not individually tracked — acceptable for the prototype.

## 7. `mirrorOf` matching

Maven matches `mirrorOf` patterns against repository *ids*, which Gradle repositories don't have. The prototype bridges that with one concept:

**Effective id.** A repository's effective id is `central` when its root URL is Maven Central's, and its Gradle repository *name* otherwise. Both facts are grounded:

- The Super POM (verified in the `maven-model-builder` 3.9.5 jar Gradle ships) declares Maven Central as `<id>central</id>` → `https://repo.maven.apache.org/maven2`, for both `<repositories>` and `<pluginRepositories>`. So `<mirrorOf>central</mirrorOf>` — the most common corporate configuration — maps cleanly onto any Gradle repository pointing at that URL. Matching by **URL** rather than by Gradle name (`MavenRepo`) also catches `maven { url = "https://repo.maven.apache.org/maven2" }` written longhand, and matches the setting's *intent*. A central-URL repository is matchable *only* as `central` (the effective id shadows the name), keeping one id per repository like Maven.
- Every other repository matches by its Gradle name. This is a documented convention, not parity: settings.xml ids come from POM/settings declarations that have no relation to Gradle names unless the team aligns them deliberately. Sharp edge: unnamed repositories get auto-generated names (`maven`, `maven2`, … in declaration order), so a `mirrorOf` matching those is order-sensitive — name your repositories if you want to match them. The "Applying Maven mirror" lifecycle line makes every match visible.

**Pattern grammar.** The matcher is a port of the one Maven 3.9 actually uses (`org.eclipse.aether.util.repository.DefaultMirrorSelector`, verified against the resolver-util 1.9.16 bytecode): `*`, exact id, `!id` negation, comma-separated lists, `external:*` (not localhost/127.0.0.1, not `file://`), and `external:http:*` (external and plain http). **There are no partial globs** — `corp-*` is not Maven syntax and is deliberately not invented here. Semantics ported exactly: mirrors are tried in settings declaration order and the first whose pattern matches wins; within a pattern, a matching `!id` short-circuits to "no match", while positive tokens keep scanning so a later `!id` can override an earlier `*` (`*,!central` works).

**Blocked mirrors.** Maven 3.8+ mirrors carry `<blocked>` — and Maven's own bundled `conf/settings.xml` contains `maven-default-http-blocker` (`mirrorOf` `external:http:*`, URL `http://0.0.0.0/`, blocked). Since the settings provider merges global settings, supporting `external:http:*` *without* honoring `blocked` would silently rewrite external http repositories to `http://0.0.0.0/` on any machine with `M2_HOME` set. So a matched blocked mirror fails resolution of that repository with an explicit error, Maven-style — a prerequisite for `external:http:*`, not an optional extra.

Notes:

- The plugin portal (`gradlePluginPortal()`) is a Maven-format repository built through the same factory, so `*` captures it — probably desired in locked-down environments; its effective id is its Gradle name, so it can be exempted with `!<name>`.
- `mirrorOfLayouts` is ignored: Gradle only has the "default" layout, and Maven's default value (`default,legacy`) matches everything anyway, so ignoring is behavior-identical.
- Follow-up idea, not in this cut: an end-of-build info summary of mirrors that matched nothing (typo diagnostics — `mirrorOf` failures are silent in Maven too).

## What the spike changes

- New: `mvnsettings/MavenMirrorResolver.java` (internal interface + `MirroredRepository` value), `mvnsettings/DefaultMavenMirrorResolver.java`, `mvnsettings/MirrorOfMatcher.java` (port of Maven's pattern matcher), `mvnsettings/MavenSettingsChecksumValueSource.java` (CC input tracking).
- `DependencyManagementBuildScopeServices`: registers the resolver (build scope).
- `DefaultDependencyManagementServices.createBaseRepositoryFactory` / `DefaultBaseRepositoryFactory`: thread the resolver through to Maven repo construction.
- `DefaultMavenArtifactRepository`: nullable resolver field; `validateUrl()` applies the mirror and logs `Applying Maven mirror '<id>' for repository '<name>': <original> -> <mirror>` (once per rewrite target); `getTransport()` replaces the repository's configured authentication (with a lifecycle warning when it had any) by the mirror's own credentials, host-scoped to the mirror.
- `DefaultMavenLocalArtifactRepository`: passes `null` resolver.

## Gaps before this could be a real feature

1. **Auth**: mostly closed in this cut (leak prevention + Option C hybrid credentials with decryption + first-header `<httpHeaders>` mapping). Remaining: multiple HTTP headers per mirror, and the Maven 4 encryption format.
2. **`mirrorOf` matching**: closed in this cut (full Maven grammar, effective ids, blocked mirrors — see section 7). Remaining: the name-matching convention needs a real design discussion before anything user-visible ships, and match diagnostics for typos.
3. **Repository identity/reporting**: descriptor should expose original + mirror; build scans and the resolution cache key need a deliberate decision.
4. **Service scope**: the resolver is build scoped, so `settings.xml` is parsed once per
   build rather than once per build tree (see section 2). The split that would fix it is
   cheap but adds a layer; it needs a measurement from a large composite to justify.
5. **Surface**: environment-variable/DSL opt-in story, interaction with `dependencyResolutionManagement` repositories and repository content filtering, Ivy repos, documentation.
