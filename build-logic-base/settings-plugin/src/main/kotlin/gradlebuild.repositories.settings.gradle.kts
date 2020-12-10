/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.artifacts.BaseRepositoryFactory
import org.gradle.api.internal.GradleInternal
import org.gradle.internal.nativeintegration.network.HostnameLookup
import java.lang.IllegalStateException

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
        maven { url = uri("https://repo.gradle.org/gradle/enterprise-libs-release-candidates-local") }
    }
    if (this is org.gradle.plugin.management.internal.PluginManagementSpecInternal) {
        includeBuild(location("build-logic-commons"))
        includeBuild(location("build-logic"))
    }
}

fun location(path: String): String = when {
    path.startsWith("../../../../") -> {
        throw IllegalStateException("Cannot fine build $path")
    }
    File(rootDir, path).isDirectory -> {
        path
    }
    else -> {
        location("../$path")
    }
}

dependencyResolutionManagement {
    repositories {
        // Cannot use 'FAIL_ON_PROJECT_REPOS' because
        // - the 'gradle-guides-plugin' adds a repo (which it should not do)
        // - the 'kotlin-gradle-plugin' adds a repo (and removes it afterwards) to download NodeJS
        repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

        listOf("distributions", "distributions-snapshots").forEach { distUrl ->
            ivy {
                name = "Gradle distributions"
                url = uri("https://services.gradle.org")
                patternLayout {
                    artifact("/${distUrl}/[module]-[revision]-bin(.[ext])")
                }
                metadataSources {
                    artifact()
                }
                content {
                    includeModule("gradle", "gradle")
                }
            }
        }
        ivy {
            name = "googleApisJs"
            url = uri("https://ajax.googleapis.com/ajax/libs")
            patternLayout {
                artifact("[organization]/[revision]/[module].[ext]")
                ivy("[organization]/[revision]/[module].xml")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("jquery", "jquery.min")
                includeModule("com.drewwilson.code", "jquery.tipTip")
                includeModule("flot", "flot")
                includeModule("org.nodejs", "node")
            }
        }
        ivy {
            // See: https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin/src/main/kotlin/org/jetbrains/kotlin/gradle/targets/js/nodejs/NodeJsSetupTask.kt
            name = "NodeJS"
            url = uri("https://nodejs.org/dist")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
                ivy("v[revision]/ivy.xml")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }

        maven {
            name = "Gradle libs"
            url = uri("https://repo.gradle.org/gradle/libs")
            mavenContent {
                // This repository contains an older version which has been overwritten in Central
                excludeModule("com.google.j2objc", "j2objc-annotations")
            }
        }
        gradlePluginPortal()
        maven {
            name = "Gradle snapshot libs"
            url = uri("https://repo.gradle.org/gradle/libs-snapshots")
            mavenContent {
                // This repository contains an older version which has been overwritten in Central
                excludeModule("com.google.j2objc", "j2objc-annotations")
            }
        }
        maven {
            name = "kotlin-dev"
            url = uri("https://dl.bintray.com/kotlin/kotlin-dev")
        }
        maven {
            name = "kotlin-eap"
            url = uri("https://dl.bintray.com/kotlin/kotlin-eap")
        }
        maven {
            name = "kotlinx"
            url = uri("https://dl.bintray.com/kotlin/kotlinx")
        }
        maven {
            name = "ge-release-candidates"
            url = uri("https://repo.gradle.org/gradle/enterprise-libs-release-candidates-local")
        }
        google()
    }
}

val originalUrls: Map<String, String> = mapOf(
    "jcenter" to "https://jcenter.bintray.com/",
    "mavencentral" to "https://repo.maven.apache.org/maven2/",
    "google" to "https://dl.google.com/dl/android/maven2/",
    "gradle" to "https://repo.gradle.org/gradle/repo",
    "gradleplugins" to "https://plugins.gradle.org/m2",
    "gradlejavascript" to "https://repo.gradle.org/gradle/javascript-public",
    "gradle-libs" to "https://repo.gradle.org/gradle/libs",
    "gradle-releases" to "https://repo.gradle.org/gradle/libs-releases",
    "gradle-snapshots" to "https://repo.gradle.org/gradle/libs-snapshots",
    "gradle-enterprise-plugin-rc" to "https://repo.gradle.org/gradle/enterprise-libs-release-candidates-local",
    "kotlinx" to "https://kotlin.bintray.com/kotlinx/",
    "kotlineap" to "https://dl.bintray.com/kotlin/kotlin-eap/",
    "kotlindev" to "https://dl.bintray.com/kotlin/kotlin-dev/"
)

val mirrorUrls: Map<String, String> =
    System.getenv("REPO_MIRROR_URLS")?.ifBlank { null }?.split(',')?.associate { nameToUrl ->
        val (name, url) = nameToUrl.split(':', limit = 2)
        name to url
    } ?: emptyMap()


fun isEc2Agent() = (gradle as GradleInternal).services.get(HostnameLookup::class.java).hostname.startsWith("ip-")

fun isMacAgent() = System.getProperty("os.name").toLowerCase().contains("mac")

fun ignoreMirrors() = System.getenv("IGNORE_MIRROR")?.toBoolean() ?: false

fun withMirrors(handler: RepositoryHandler) {
    if ("CI" !in System.getenv() || isEc2Agent()) {
        return
    }
    handler.all {
        if (this is MavenArtifactRepository) {
            originalUrls.forEach { (name, originalUrl) ->
                if (normalizeUrl(originalUrl) == normalizeUrl(this.url.toString()) && mirrorUrls.containsKey(name)) {
                    this.url = uri(mirrorUrls.getValue(name))
                }
            }
        }
    }
}

fun normalizeUrl(url: String): String {
    val result = url.replace("https://", "http://")
    return if (result.endsWith("/")) result else "$result/"
}

if (System.getProperty(BaseRepositoryFactory.PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY) == null && !isEc2Agent() && !isMacAgent() && !ignoreMirrors()) {
    // https://github.com/gradle/gradle-private/issues/2725
    // https://github.com/gradle/gradle-private/issues/2951
    System.setProperty(BaseRepositoryFactory.PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY, "https://dev12.gradle.org/artifactory/gradle-plugins/")
    gradle.buildFinished {
        System.clearProperty(BaseRepositoryFactory.PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY)
    }
}

gradle.settingsEvaluated {
    dependencyResolutionManagement {
        repositories { withMirrors(this) }
    }
    withMirrors(settings.pluginManagement.repositories)
}

FeaturePreviews.Feature.values().forEach { feature ->
    if (feature.isActive) {
        enableFeaturePreview(feature.name)
    }
}
