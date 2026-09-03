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
 * Set the TeamCity parameter `env.IGNORE_MIRROR` = `true` on the root `Gradle` project. It takes effect on the
 * next build; no code change and no `.teamcity` configuration regeneration is needed. Remove the parameter once
 * the mirror is healthy again.
 *
 * `env.IGNORE_MIRROR` is deliberately NOT declared in the `.teamcity` Kotlin DSL: a build-configuration-level
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

    val ignoreMirrors: Boolean = providers.environmentVariable("IGNORE_MIRROR").orNull?.toBoolean() == true

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
