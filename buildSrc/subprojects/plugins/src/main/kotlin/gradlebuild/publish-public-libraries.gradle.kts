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
import org.gradle.gradlebuild.packaging.ShadedJarPlugin
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
    }
    configurePublishingTasks()
}

fun Project.configurePublishingTasks() {
    tasks.named("publishGradleDistributionPublicationToRemoteRepository") {
        onlyIf { !project.hasProperty("noUpload") }
        failEarlyIfCredentialsAreNotSet(this)
    }

    plugins.withType<ShadedJarPlugin> {
        publishNormalizedToLocalRepository()
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

fun Project.publishNormalizedToLocalRepository() {
    val localRepository = layout.buildDirectory.dir("repo")

    publishing {
        repositories {
            maven {
                name = "local"
                url = uri(localRepository)
            }
        }
        publications {
            create<MavenPublication>("local") {
                from(project.components["java"])
                artifactId = project.base.archivesBaseName
                version = project.rootProject.buildVersion.baseVersion
            }
        }
    }
    project.tasks.named("publishLocalPublicationToRemoteRepository") {
        enabled = false // don't publish normalized local version to remote repository when using 'publish' lifecycle task
    }
    project.tasks.named("publishGradleDistributionPublicationToLocalRepository") {
        enabled = false // this should not be used so we disable it to avoid confusion when using 'publish' lifecycle task
    }
    val localPublish = project.tasks.named("publishLocalPublicationToLocalRepository") {
        val archivesBaseName = project.base.archivesBaseName
        doFirst {
            val moduleBaseDir = localRepository.get().dir("org/gradle/$archivesBaseName").asFile
            if (moduleBaseDir.exists()) {
                // Make sure artifacts do not pile up locally
                moduleBaseDir.deleteRecursively()
            }
        }

        doLast {
            localRepository.get().file("org/gradle/$archivesBaseName/maven-metadata.xml").asFile.apply {
                writeText(readText().replace("\\Q<lastUpdated>\\E\\d+\\Q</lastUpdated>\\E".toRegex(), "<lastUpdated>${Year.now().value}0101000000</lastUpdated>"))
            }
            localRepository.get().asFileTree.matching { include("**/*.module") }.forEach {
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

    // For local consumption by tests
    project.configurations.create("localLibsRepositoryElements") {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named("gradle-local-repository"))
            attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling.EMBEDDED))
        }
        isCanBeResolved = false
        isCanBeConsumed = true
        isVisible = false
        outgoing.artifact(localRepository) {
            builtBy(localPublish)
        }
    }
}
