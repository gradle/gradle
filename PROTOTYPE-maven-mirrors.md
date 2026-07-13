# Prototype: Maven settings.xml mirror support

Status: design note + spike. Not for merge. Feature flag: `org.gradle.internal.mavenMirrors` (Gradle property, off by default).

Scope of this cut:

- Parse mirrors from Maven `settings.xml` only (user + global, as already merged by `DefaultMavenSettingsProvider`).
- Only `<mirrorOf>*</mirrorOf>` is honored. Any other `mirrorOf` value produces a lifecycle warning and is skipped.
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

## 3. Repository identity

`createDescriptor()` goes through the same `validateUrl()`, so the descriptor's root URI is the *mirror* URL. Consequences:

- Build scans / build operations report the mirror URL as the repository root, but the repository *name* stays e.g. `MavenRepo`. So scans show `MavenRepo` with an unexpected URL — confusing but truthful.
- Dependency-resolution caches (`ModuleRepositoryCaches`) key by a repository id derived from the descriptor, which includes the URL. Turning the mirror on/off therefore changes the repository identity and produces cache misses (re-downloads). That is arguably *correct* — the artifacts genuinely come from elsewhere — but it means toggling the flag is not free.
- `NamedMavenRepositoryDescriber` ("MavenRepo", "Google" display names) compares `getUrl()` against the default URL; since `getUrl()` is untouched, display names are stable.

Not fixed in this cut; noted for the real design (a mirror-aware descriptor should probably carry both original and mirror URL).

## 4. Authentication

Not handled. The original repository's credentials continue to be attached to the transport (they are looked up by repository, not by URL), which means:

- credentials configured for the original repo **are sent to the mirror host** — a real leak-ish concern for the production design (Maven solves this with `<mirror><id>` matching a `<server>` entry's credentials);
- credentials the *mirror* needs cannot be supplied at all in this cut.

The prototype logs a lifecycle warning when the mirror URL uses `http` (also covering "clearly different transport security"). Host-difference is inherent to mirroring, so no warning on host alone beyond the standard "Applying Maven mirror" line, which always prints both URLs.

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

## What the spike changes

- New: `mvnsettings/MavenMirrorResolver.java` (internal interface + `MirroredRepository` value), `mvnsettings/DefaultMavenMirrorResolver.java`, `mvnsettings/MavenSettingsChecksumValueSource.java` (CC input tracking).
- `DependencyManagementBuildScopeServices`: registers the resolver (build scope).
- `DefaultDependencyManagementServices.createBaseRepositoryFactory` / `DefaultBaseRepositoryFactory`: thread the resolver through to Maven repo construction.
- `DefaultMavenArtifactRepository`: nullable resolver field; `validateUrl()` applies the mirror and logs `Applying Maven mirror '<id>' for repository '<name>': <original> -> <mirror>` (once per rewrite target).
- `DefaultMavenLocalArtifactRepository`: passes `null` resolver.

## Gaps before this could be a real feature

1. **Auth**: mirror credentials (Maven `<server>` matching by mirror id) + not leaking original-repo credentials to the mirror host.
2. **`mirrorOf` matching semantics**: `external:*`, `!excludes`, id lists, per-repo matching by repository id (requires a mapping from Gradle repo names to Maven repo ids — non-obvious).
3. **Repository identity/reporting**: descriptor should expose original + mirror; build scans and the resolution cache key need a deliberate decision.
4. **Surface**: environment-variable/DSL opt-in story, interaction with `dependencyResolutionManagement` repositories and repository content filtering, Ivy repos, documentation.
