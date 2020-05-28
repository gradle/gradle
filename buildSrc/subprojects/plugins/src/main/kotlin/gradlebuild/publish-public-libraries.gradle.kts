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
package gradlebuild

import accessors.base
import org.gradle.gradlebuild.versioning.buildVersion
import java.time.Year

plugins {
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("gradleDistribution") {
            from(components["java"])
            artifactId = base.archivesBaseName
        }
    }
    repositories {
        maven {
            name = "remote"
            val libsType = if (rootProject.buildVersion.isSnapshot) "snapshots" else "releases"
            url = uri("https://repo.gradle.org/gradle/libs-$libsType-local")
            credentials {
                username = artifactoryUserName
                password = artifactoryUserPassword
            }
        }
        maven {
            name = "local"
            url = uri(rootProject.file("build/repo"))
        }
    }
    configurePublishingTasks()
}

fun Project.configurePublishingTasks() {
    tasks.named("publishGradleDistributionPublicationToRemoteRepository") {
        onlyIf { !project.hasProperty("noUpload") }
        failEarlyIfCredentialsAreNotSet(this)
    }
    tasks.named("publishGradleDistributionPublicationToLocalRepository") {
        doFirst {
            val moduleBaseDir = rootProject.file("build/repo/org/gradle/${base.archivesBaseName}")
            if (moduleBaseDir.exists()) {
                // Make sure artifacts do not pile up locally
                moduleBaseDir.deleteRecursively()
            }
        }

        doLast {
            val mavenMetadataXml = rootProject.file("build/repo/org/gradle/${base.archivesBaseName}/maven-metadata.xml")
            val content = mavenMetadataXml.readText()
            mavenMetadataXml.writeText(content.replace("\\Q<lastUpdated>\\E\\d+\\Q</lastUpdated>\\E".toRegex(), "<lastUpdated>${Year.now().value}0101000000</lastUpdated>"))
        }
    }
}

fun Project.failEarlyIfCredentialsAreNotSet(publish: Task) {
    gradle.taskGraph.whenReady {
        if (hasTask(publish)) {
            if (artifactoryUserName.isNullOrEmpty()) {
                throw GradleException("artifactoryUserName is not set!")
            }
            if (artifactoryUserPassword.isNullOrEmpty()) {
                throw GradleException("artifactoryUserPassword is not set!")
            }
        }
    }
}

val Project.artifactoryUserName
    get() = findProperty("artifactoryUserName") as String?

val Project.artifactoryUserPassword
    get() = findProperty("artifactoryUserPassword") as String?


