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

import gradlebuild.basics.capitalize
import gradlebuild.packaging.transforms.CopyPublicApiClassesTransform

plugins {
    id("gradlebuild.distribution.packaging")
    id("gradlebuild.module-identity")
    id("signing")
    `maven-publish`
}

enum class Filtering {
    PUBLIC_API, ALL
}

val filteredAttribute: Attribute<Filtering> = Attribute.of("org.gradle.apijar.filtered", Filtering::class.java)

dependencies {
    coreRuntimeOnly(platform(project(":core-platform")))
    pluginsRuntimeOnly(platform(project(":distributions-full")))

    artifactTypes.getByName("jar") {
        attributes.attribute(filteredAttribute, Filtering.ALL)
    }

    // Filters out classes that are not exposed by our API.
    // TODO Use actual filtering not copying
    registerTransform(CopyPublicApiClassesTransform::class.java) {
        from.attribute(filteredAttribute, Filtering.ALL)
            .attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, LibraryElements.JAR))
        to.attribute(filteredAttribute, Filtering.PUBLIC_API)
    }
}

fun registerApiJarTask(artifactName: String, dependencies: NamedDomainObjectProvider<Configuration>) {
    val task = tasks.register<Jar>("jar${artifactName.split("-").joinToString("") { it.capitalize() }}") {
        from(dependencies.map { classpath ->
            classpath.incoming.artifactView {
                attributes {
                    attribute(filteredAttribute, Filtering.PUBLIC_API)
                }
                componentFilter { component ->
                    component is ProjectComponentIdentifier &&
                        // FIXME Why is this dependency present here? Can we exclude it better?
                        component.buildTreePath != ":build-logic:kotlin-dsl-shared-runtime"
                }
            }.files
        })
        destinationDirectory = layout.buildDirectory.dir("public-api/${artifactName}")
        // This is needed because of the duplicate package-info.class files
        duplicatesStrategy = DuplicatesStrategy.WARN
    }

    publishing {
        publications {
            create<MavenPublication>(artifactName) {
                groupId = "org.gradle.experimental"
                artifactId = artifactName
                version = moduleIdentity.snapshot
                    .map { moduleIdentity.version.get().baseVersion.version + "-SNAPSHOT" }
                    .orElse(moduleIdentity.version.map { it.version!! })
                    .get()
                artifact(task)

                pom {
                    name = "org.gradle.experimental:${artifactName}"
                    // TODO Add artifact type in description
                    description = "Public API for Gradle"

                    // TODO Can we do this in a CC-compatible way?
                    withXml {
                        val dependenciesNode = asNode().appendNode("dependencies")
                        // TODO Handle this via resolved dependencies
                        dependenciesNode.appendNode("dependency").apply {
                            appendNode("groupId", libs.groovyGroup)
                            appendNode("artifactId", "groovy")
                            appendNode("version", libs.groovyVersion)
                        }
                    }

                    // TODO Reuse these from gradlebuild.publish-public-libraries.gradle
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
    }
}

registerApiJarTask("gradle-api", configurations.runtimeClasspath)
