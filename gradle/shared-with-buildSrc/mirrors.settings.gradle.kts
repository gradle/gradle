/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Redirects the repositories declared by this build to the caching mirrors on repo.grdev.net when running on CI.
 *
 * EMERGENCY BYPASS (repo.grdev.net / Artifactory outage)
 * -----------------------------------------------------
 * Set the TeamCity parameter `env.IGNORE_REPO_MIRROR` = `true` on the root `Gradle` project. It takes effect on the
 * next build; no code change and no `.teamcity` configuration regeneration is needed. Remove the parameter once
 * the mirror is healthy again.
 *
 * `env.IGNORE_REPO_MIRROR` is deliberately NOT declared in the `.teamcity` Kotlin DSL: a build-configuration-level
 * parameter would take precedence over the project-level one and would therefore block the emergency flip.
 *
 * `env.REPO_MIRROR_URLS` must stay set while the bypass is on. The bypass works by mapping mirror URLs back to
 * their upstream URLs, so it needs that variable to recognise which URLs are mirror URLs. That reverse mapping
 * is also what undoes `-Dorg.gradle.internal.plugins.portal.url.override=%gradle.plugins.portal.url%`, which
 * TeamCity bakes into the Gradle command line of essentially every build step.
 *
 * Still pinned to repo.grdev.net and needing their own TeamCity parameter edits if those builds matter:
 *   - `env.YARNPKG_MIRROR_URL`       - JS/docs builds
 *   - `gradle.internal.repository.url` - publishing only, irrelevant to `check`
 *
 * TESTING THE BYPASS WHILE THE MIRROR IS HEALTHY
 * ----------------------------------------------
 * Do not wait for the next outage to find out whether this still works. Simulate one on a single build by
 * pointing the mirror at a host that cannot resolve, using per-run TeamCity parameter overrides so nothing
 * shared is touched and no other branch is affected:
 *
 *   teamcity run start <buildTypeId> --branch <branch> \
 *     -P reverse.dep.*.env.REPO_MIRROR_URLS="<real value, repo.grdev.net replaced by repo-mirror-outage-test.invalid>" \
 *     -P reverse.dep.*.gradle.plugins.portal.url="https://repo-mirror-outage-test.invalid/artifactory/gradle-plugin-portal-prod/" \
 *     -P reverse.dep.*.env.IGNORE_REPO_MIRROR=true
 *
 * The `reverse.dep.*.` prefix is NOT optional on a composite/trigger build. TeamCity does not propagate plain
 * `-P` parameters to snapshot dependencies, so without it the overrides land only on the trigger build while
 * every build that actually resolves anything runs against the real mirror - and the run comes back green
 * having tested nothing. On a leaf build (e.g. `..._Check_CompileAllBuild`) plain `-P` is fine, because there
 * are no dependencies to propagate to. Verify before trusting a green result:
 *   teamcity api "/app/rest/builds/id:<a dependency build id>/resulting-properties"
 * must show env.IGNORE_REPO_MIRROR and the .invalid URLs.
 *
 * `.invalid` is reserved by RFC 6761 and never resolves, so any request that still goes to the "mirror" fails
 * fast and loudly instead of silently succeeding against the real one. Overriding `gradle.plugins.portal.url`
 * matters as much as the mirror list: it is what TeamCity injects as
 * `-Dorg.gradle.internal.plugins.portal.url.override`, and it is the case this whole script exists to undo.
 *
 * Run it BOTH ways. Without `env.IGNORE_REPO_MIRROR` the build must FAIL on a
 * `repo-mirror-outage-test.invalid` URL - that is what proves the simulation is faithful. With it, the build
 * must pass and no `repo-mirror-outage-test.invalid` URL may appear anywhere in the log.
 *
 * Expect some flakiness while the bypass is on. Every agent then fetches from the upstream
 * repositories directly, with no caching proxy in front of them, so sporadic
 * "Could not GET ... > Read timed out" resolution failures are normal under full CI load and do
 * not mean the switch is broken. Retry; if a whole stage is failing this way, the upstream
 * repository is the bottleneck, not this script.
 */

class Helper(private val providers: ProviderFactory) {
    val originalUrls: Map<String, String> = mapOf(
        "mavencentral" to "https://repo.maven.apache.org/maven2/",
        "google" to "https://dl.google.com/dl/android/maven2/",
        "gradle" to "https://repo.gradle.org/gradle/repo",
        "gradle-prod-plugins" to "https://plugins.gradle.org/m2",
        "gradlejavascript" to "https://repo.gradle.org/gradle/javascript-public",
        "gradle-public" to "https://repo.gradle.org/gradle/public",
        "gradle-enterprise-rc" to "https://repo.gradle.org/gradle/enterprise-libs-release-candidates",
        "android-studio-installers" to "https://redirector.gvt1.com/edgedl/android/studio",
        "jetbrains-ide-installers" to "https://download.jetbrains.com",
    )

    val mirrorUrls: Map<String, String> =
        providers.environmentVariable("REPO_MIRROR_URLS").orNull
            ?.ifBlank { null }
            ?.split(',')
            ?.associate { nameToUrl ->
                val (name, url) = nameToUrl.split(':', limit = 2)
                name to url
            }
            ?: emptyMap()

    val ignoreMirrors: Boolean = providers.environmentVariable("IGNORE_REPO_MIRROR").orNull?.toBoolean() == true

    /**
     * Normalized mirror URL -> upstream URL, for the mirrors this build actually declares repositories for.
     * Used by the emergency bypass to map a repository that already points at a mirror back to upstream,
     * regardless of whether it was rewritten by this script or handed to us already mirrored (as the
     * `gradlePluginPortal()` URL is, via the `org.gradle.internal.plugins.portal.url.override` system property).
     */
    val upstreamUrlsByMirrorUrl: Map<String, String> =
        originalUrls.mapNotNull { (name, originalUrl) ->
            mirrorUrls[name]?.let { mirrorUrl -> normalizeUrl(mirrorUrl) to originalUrl }
        }.toMap()

    fun isCI() = providers.environmentVariable("CI").isPresent()

    fun withMirrors(handler: RepositoryHandler) {
        if (!isCI()) {
            return
        }
        handler.all {
            if (this is UrlArtifactRepository) {
                // see https://github.com/gradle/gradle/issues/37612
                @Suppress("USELESS_ELVIS")
                val currentUrl = this.url?.toString() ?: return@all
                if (ignoreMirrors) {
                    upstreamUrlsByMirrorUrl[normalizeUrl(currentUrl)]?.let { this.setUrl(it) }
                } else {
                    originalUrls.forEach { name, originalUrl ->
                        if (normalizeUrl(originalUrl) == normalizeUrl(currentUrl) && mirrorUrls.containsKey(name)) {
                            mirrorUrls.get(name)?.let { this.setUrl(it) }
                        }
                    }
                }
            }
        }
    }

    fun normalizeUrl(url: String): String {
        val result = url.replace("https://", "http://")
        return if (result.endsWith("/")) result else "$result/"
    }
}

with(Helper(providers)) {
    gradle.lifecycle.beforeProject {
        buildscript.configurations["classpath"].incoming.beforeResolve {
            withMirrors(buildscript.repositories)
        }
        afterEvaluate {
            withMirrors(repositories)
        }
    }

    gradle.settingsEvaluated {
        withMirrors(settings.pluginManagement.repositories)
        if (ignoreMirrors) {
            // The mirroring path deliberately leaves dependencyResolutionManagement repositories alone,
            // but the bypass has to reach them: their `gradlePluginPortal()` carries the TeamCity
            // `org.gradle.internal.plugins.portal.url.override` value, which points at repo.grdev.net.
            withMirrors(settings.dependencyResolutionManagement.repositories)
        }
    }
}
