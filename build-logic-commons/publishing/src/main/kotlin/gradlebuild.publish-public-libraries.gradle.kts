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

import java.time.Year

plugins {
    id("gradlebuild.module-identity")
    id("gradlebuild.publish-defaults")
    id("signing")
    `maven-publish`
}

configureJavadocVariant()

publishing {
    publications {
        create<MavenPublication>("gradleDistribution") {
            configureGradleModulePublication()
        }
    }

    plugins.withId("gradlebuild.shaded-jar") {
        publishNormalizedToLocalRepository()
    }
}

val pgpSigningKey: Provider<String> = providers.environmentVariable("PGP_SIGNING_KEY")
val signArtifacts: Boolean = !pgpSigningKey.orNull.isNullOrEmpty()

tasks.withType<Sign>().configureEach { isEnabled = signArtifacts }

signing {
    useInMemoryPgpKeys(
        project.providers.environmentVariable("PGP_SIGNING_KEY").orNull,
        project.providers.environmentVariable("PGP_SIGNING_KEY_PASSPHRASE").orNull
    )
    publishing.publications.configureEach {
        if (signArtifacts) {
            signing.sign(this)
        }
    }
}

fun configureJavadocVariant() {
    java {
        withJavadocJar()
    }
}

fun MavenPublication.configureGradleModulePublication() {
    from(components["java"])
    artifactId = moduleIdentity.baseName.get()
    versionMapping {
        usage("java-api") {
            fromResolutionResult()
        }
        usage("java-runtime") {
            fromResolutionResult()
        }
    }

    pom {
        packaging = "jar"
        name = moduleIdentity.baseName.map { "${project.group}:$it" }
    }
}

fun publishNormalizedToLocalRepository() {
    val localRepository = layout.buildDirectory.dir("repo")
    val baseName = moduleIdentity.baseName

    publishing {
        repositories {
            maven {
                name = "local"
                url = uri(localRepository)
            }
        }
        publications {
            create<MavenPublication>("local") {
                configureGradleModulePublication()
                version = moduleIdentity.version.get().baseVersion.version
            }
        }
    }
    tasks.named("publishLocalPublicationToRemoteRepository") {
        enabled = false // don't publish normalized local version to remote repository when using 'publish' lifecycle task
    }
    tasks.named("publishGradleDistributionPublicationToLocalRepository") {
        enabled = false // this should not be used so we disable it to avoid confusion when using 'publish' lifecycle task
    }
    val localPublish = project.tasks.named("publishLocalPublicationToLocalRepository") {
        doFirst {
            val moduleBaseDir = localRepository.get().dir("org/gradle/${baseName.get()}").asFile
            if (moduleBaseDir.exists()) {
                // Make sure artifacts do not pile up locally
                moduleBaseDir.deleteRecursively()
            }
        }

        doLast {
            localRepository.get().file("org/gradle/${baseName.get()}/maven-metadata.xml").asFile.apply {
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
    configurations.create("localLibsRepositoryElements") {
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

    if (signArtifacts) {
        // Otherwise we get
        // ask ':tooling-api:publishGradleDistributionPublicationToRemoteRepository' uses this output of task ':tooling-api:signLocalPublication'
        // without declaring an explicit or implicit dependency. This can lead to incorrect results being produced, depending on what order the tasks are executed.
        tasks.named("publishGradleDistributionPublicationToRemoteRepository") {
            dependsOn("signLocalPublication")
        }
        tasks.named("publishLocalPublicationToLocalRepository") {
            dependsOn("signGradleDistributionPublication")
        }
    }
}

/**
 * Tasks that are called by the (currently separate) promotion build running on CI.
 */
tasks.register("promotionBuild") {
    description = "Build production distros, smoke test them and publish"
    group = "publishing"
    dependsOn(":packageBuild", ":publishBuildLogic", "publish")
}
