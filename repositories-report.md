# Repositories Report

`./gradlew :repositories` (or `./gradlew repositories` at the root project) prints a
unified, diagnostics-only view of every repository declared in a build, across every
settings-level and project-level declaration site, including repositories added by
plugins. The report is incubating.

## What the report shows

### Section 1: All Repositories

Flat, globally numbered list of every repository in the build, emitted in this order:

1. `settings.buildscript.repositories`
2. `settings.pluginManagement.repositories`
3. `settings.dependencyResolutionManagement.repositories`
4. Each project (alphabetical by path) — its `buildscript.repositories` followed by
   its `repositories` block.

Each entry renders:

```
<Name> (<number>) [*]
    Location:   <url> [(ur)|(ua)|(m)]
    Type:       MAVEN | MAVEN_LOCAL | IVY | FLAT_DIR | CUSTOM
    Roles:      <sorted list of RepositoryRole values>
    Secure:     false         # only when allowInsecureProtocol=true
    Auth:       <scheme class names sorted alphabetically>  # only when declared
    Credentials: PRESENT      # only when credentials{} declared (no values leak)
    Content:    <rules joined with ", ">
    Defined in: settings > <block>  | project ':path' > <block>
```

### Section 2: Repositories by Location

Groups repositories by "who uses them":

- `settings uses` lists every repository declared in any of the three settings buckets.
- `project ':foo' uses` lists (a) every PLUGINS and DRM repo inherited from settings,
  plus (b) project-local `buildscript.repositories` and `repositories` declarations.

### Legend

A final Legend section is emitted only when at least one marker was actually rendered
in the report above.

## Markers

| Marker | Meaning |
| --- | --- |
| `(*)` | Identical repository declaration found in multiple locations. Appears on the entry, not on the `Location:` line. |
| `(ur)` | Unreachable. The URL could not be contacted after the HEAD (+ GET fallback) probe. Rendered on the `Location:` line. |
| `(ua)` | Unauthorized. The URL returned 401 or 403. Rendered on the `Location:` line. |
| `(m)` | Malformed URL. The URL could not be parsed as a URI. Rendered on the `Location:` line. |
| `(o)` | Offline. Appears on the "All Repositories" heading when the build was invoked with `--offline`; per-repo reachability markers are suppressed. |

## Filtering

`./gradlew :repositories --project :foo` limits Section 2 to a single project and
suppresses the settings bucket block. Unknown project paths fail with a clear error
message.

## Reachability probes

For every unique remote HTTP(S) URL (local and `FLAT_DIR` repositories are skipped),
the task issues an HTTP `HEAD`. If the response is any 4xx/5xx, it retries with `GET`
and classifies on the GET response. Each probe is bounded by a 5-second per-URL
timeout. Probes run in parallel on a small fixed daemon thread pool (capped at 8).

At task start the report emits (non-offline only):

```
Probing <N> repository URL(s)...
...
done.
```

`URISyntaxException` in the probe produces `MALFORMED_URL`, rendered as `(m)`.

Credentials are never sent during the probe — any URL gated behind HTTP auth is
classified as `UNAUTHORIZED` (`(ua)` marker).

### Offline mode

With `--offline`, no probes run, the "All Repositories" heading carries `(o)`, and
the Legend explains offline mode.

## Null URL handling

If a `MavenArtifactRepository` or `IvyArtifactRepository` is misconfigured such that
`getUrl()` returns null, the `Location:` line renders the sentinel `<NO_URL>` instead
of the literal string `"null"`.

## Repository types

| Type | Underlying Gradle repository |
| --- | --- |
| `MAVEN` | `MavenArtifactRepository` (non-local) |
| `MAVEN_LOCAL` | `mavenLocal()` |
| `IVY` | `IvyArtifactRepository` |
| `FLAT_DIR` | `FlatDirectoryArtifactRepository` |
| `CUSTOM` | Any other `AbstractArtifactRepository` subclass. `Location:` surfaces the concrete class name. |

## Configuration cache

The task is CC-compatible. Model construction runs at configuration time via a
`Cached<>` field; reachability probes run at task-execution time only.

## Tests

| Test class | Count |
| --- | --- |
| `DefaultRepositoryContentDescriptorDescribeRulesTest` | 12 |
| `ConsoleRepositoriesReportRendererTest` | 18 |
| `RepositoryReportModelFactoryTest` | 10 |
| `RepositoryReachabilityCheckerTest` | 17 |
| `RepositoriesReportTaskIntegrationTest` | 35 |

## Verification commands

```bash
./gradlew :dependency-management:test --tests "*DefaultRepositoryContentDescriptorDescribeRulesTest*"
./gradlew :software-diagnostics:test --tests "*ConsoleRepositoriesReportRendererTest*"
./gradlew :software-diagnostics:test --tests "*RepositoryReportModelFactoryTest*"
./gradlew :software-diagnostics:test --tests "*RepositoryReachabilityCheckerTest*"
./gradlew :software-diagnostics:embeddedIntegTest --tests "*RepositoriesReportTaskIntegrationTest*"
```
