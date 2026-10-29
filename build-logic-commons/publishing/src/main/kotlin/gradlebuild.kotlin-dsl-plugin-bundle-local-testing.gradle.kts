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

import java.time.Year

plugins {
    `maven-publish`
    `java-gradle-plugin`
}

// Tests apply the Kotlin DSL plugins built from sources, from this local test repository.
// The repository has them at their own version, the next version to publish. The tests make Gradle expect that version
// (see KOTLIN_DSL_PLUGINS_VERSION_OVERRIDE_PROPERTY in the kotlin-dsl module).
val testRepository = layout.buildDirectory.dir("repository")

publishing {
    repositories {
        maven {
            name = "test"
            url = uri(testRepository)
        }
    }
}

val publishPluginsToTestRepository = tasks.register("publishPluginsToTestRepository") {
    dependsOn(tasks.withType<PublishToMavenRepository>().matching { it.repository.name == "test" })
    val repoDir = testRepository // Prevent capturing the Gradle script instance for configuration cache compatibility
    // This should be unified with publish-public-libraries if possible
    doLast {
        repoDir.get().asFileTree.matching { include("**/maven-metadata.xml") }.forEach {
            it.writeText(it.readText().replace("\\Q<lastUpdated>\\E\\d+\\Q</lastUpdated>\\E".toRegex(), "<lastUpdated>${Year.now().value}0101000000</lastUpdated>"))
        }
        repoDir.get().asFileTree.matching { include("**/*.module") }.forEach {
            val content = it.readText()
                .replace("\"buildId\":\\s+\"\\w+\"".toRegex(), "\"buildId\": \"\"")
                .replace("\"size\":\\s+\\d+".toRegex(), "\"size\": 0")
                .replace("\"sha512\":\\s+\"\\w+\"".toRegex(), "\"sha512\": \"\"")
                .replace("\"sha1\":\\s+\"\\w+\"".toRegex(), "\"sha1\": \"\"")
                .replace("\"sha256\":\\s+\"\\w+\"".toRegex(), "\"sha256\": \"\"")
                .replace("\"md5\":\\s+\"\\w+\"".toRegex(), "\"md5\": \"\"")
            it.writeText(content)
        }
    }
}

// For local consumption by tests - this should be unified with publish-public-libraries if possible
configurations.create("localLibsRepositoryElements") {
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("gradle-local-repository"))
    }
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(testRepository) {
        builtBy(publishPluginsToTestRepository)
    }
}
