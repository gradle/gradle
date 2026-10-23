/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild

import org.gradle.api.tasks.testing.Test


const val REPO_MIRROR_URLS = "REPO_MIRROR_URLS"


const val IGNORE_MIRROR = "IGNORE_MIRROR"


private
val defaultRepoMirrorUrls = mapOf(
    "internal-ci-mirror" to "https://repo.grdev.net/artifactory/internal-ci-mirror",
    "jcenter" to "https://repo.grdev.net/artifactory/limited-jcenter/",
    "jenkins" to "https://repo.grdev.net/artifactory/jenkins/",
    "jitpack" to "https://repo.grdev.net/artifactory/jitpack/",
    "google" to "https://repo.grdev.net/artifactory/google",
    "gradle-dev-plugins" to "https://repo.grdev.net/artifactory/gradle-plugin-portal-dev/",
    "gradle-enterprise-rc" to "https://repo.grdev.net/artifactory/enterprise-libs-release-candidates-local/",
    "gradle-public" to "https://repo.grdev.net/artifactory/public/",
    "gradle-prod-plugins" to "https://repo.grdev.net/artifactory/gradle-plugin-portal-prod/",
    "kotlineap" to "https://repo.grdev.net/artifactory/kotlin-eap/",
    "mavencentral" to "https://repo.grdev.net/artifactory/maven-central/",
    "oss-snapshot" to "https://repo.grdev.net/artifactory/jfrog-oss-snapshot",
    "springreleases" to "https://repo.grdev.net/artifactory/springreleases",
    "springsnapshots" to "https://repo.grdev.net/artifactory/spring-snapshots",
    "jboss-public-repository" to "https://repo.grdev.net/artifactory/jboss-public-repository",
).entries.joinToString(",") { "${it.key}:${it.value}" }


fun Test.isMirrorIgnored() = environment[IGNORE_MIRROR]?.toString()?.toBoolean() == true


fun Test.useDefaultRepoMirrorsIfUnset() {
    if (!isMirrorIgnored() && environment[REPO_MIRROR_URLS]?.toString().isNullOrBlank()) {
        environment(REPO_MIRROR_URLS, defaultRepoMirrorUrls)
    }
}


fun Test.repoMirrorUrls(): Map<String, String> =
    if (isMirrorIgnored()) {
        emptyMap()
    } else {
        // expected env var format: repo1_id:repo1_url,repo2_id:repo2_url,...
        environment[REPO_MIRROR_URLS]?.toString()?.ifBlank { null }?.split(',')?.associate { nameToUrl ->
            val (name, url) = nameToUrl.split(':', limit = 2)
            name to url
        } ?: emptyMap()
    }
