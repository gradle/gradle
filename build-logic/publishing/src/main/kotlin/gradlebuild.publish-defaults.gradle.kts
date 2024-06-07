/*
 * Copyright 2024 the original author or authors.
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

import gradle.kotlin.dsl.accessors._25bd7e7076749e7e243da5bad7112e92.moduleIdentity
import gradlebuild.basics.gradleProperty
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("publishing")
}

val artifactoryUrl
    get() = System.getenv("GRADLE_INTERNAL_REPO_URL") ?: ""

val artifactoryUserName
    get() = findProperty("artifactoryUserName") as String?

val artifactoryUserPassword
    get() = findProperty("artifactoryUserPassword") as String?

tasks.withType<AbstractPublishToMaven>().configureEach {
    val noUpload = project.gradleProperty("noUpload")
    this.onlyIf { !noUpload.map { it == "true" }.orElse(false).get() }
    if (name.endsWith("ToRemoteRepository")) {
        failEarlyIfUrlOrCredentialsAreNotSet(this)
    }
}

fun Project.failEarlyIfUrlOrCredentialsAreNotSet(publish: Task) {
    gradle.taskGraph.whenReady {
        if (hasTask(publish)) {
            if (artifactoryUrl.isEmpty()) {
                throw GradleException("artifactoryUrl is not set!")
            }
            if (artifactoryUserName.isNullOrEmpty()) {
                throw GradleException("artifactoryUserName is not set!")
            }
            if (artifactoryUserPassword.isNullOrEmpty()) {
                throw GradleException("artifactoryUserPassword is not set!")
            }
        }
    }
}

publishing {
    repositories {
        maven {
            name = "remote"
            val libsType = moduleIdentity.snapshot.map { if (it) "snapshots" else "releases" }
            url = uri("$artifactoryUrl/libs-${libsType.get()}-local")
            credentials {
                username = artifactoryUserName
                password = artifactoryUserPassword
            }
        }
    }

    publications.withType<MavenPublication>().configureEach {
        pom {
            description = provider {
                require(project.description != null) { "You must set the description of published project ${project.name}" }
                project.description
            }
            url = "https://gradle.org"
            licenses {
                license {
                    name = "Apache-2.0"
                    url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                }
            }
            developers {
                developer {
                    name = "The Gradle team"
                    organization = "Gradle Inc."
                    organizationUrl = "https://gradle.org"
                }
            }
            scm {
                connection = "scm:git:git://github.com/gradle/gradle.git"
                developerConnection = "scm:git:ssh://github.com:gradle/gradle.git"
                url = "https://github.com/gradle/gradle"
            }
        }
    }
}
