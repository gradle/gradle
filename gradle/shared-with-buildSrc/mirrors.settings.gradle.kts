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

import org.gradle.api.internal.artifacts.BaseRepositoryFactory.PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY
import org.gradle.api.internal.GradleInternal
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.internal.nativeintegration.network.HostnameLookup
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener


class Helper(private val providers: ProviderFactory) {
    val originalUrls: Map<String, String> = mapOf(
        "mavencentral" to "https://repo.maven.apache.org/maven2/",
        "google" to "https://dl.google.com/dl/android/maven2/",
        "gradle" to "https://repo.gradle.org/gradle/repo",
        "gradle-prod-plugins" to "https://plugins.gradle.org/m2",
        "gradlejavascript" to "https://repo.gradle.org/gradle/javascript-public",
        "gradle-public" to "https://repo.gradle.org/gradle/public",
        "gradle-enterprise-rc" to "https://repo.gradle.org/gradle/enterprise-libs-release-candidates"
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

    fun ignoreMirrors() = providers.environmentVariable("IGNORE_MIRROR").orNull?.toBoolean() == true

    fun isCI() = providers.environmentVariable("CI").isPresent()

    fun withMirrors(handler: RepositoryHandler) {
        if (!isCI()) {
            return
        }
        handler.all {
            if (this is MavenArtifactRepository) {
                originalUrls.forEach { name, originalUrl ->
                    if (normalizeUrl(originalUrl) == normalizeUrl(this.url.toString()) && mirrorUrls.containsKey(name)) {
                        mirrorUrls.get(name)?.let { this.setUrl(it) }
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
    }
}
