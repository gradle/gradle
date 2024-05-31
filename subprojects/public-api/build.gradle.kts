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

plugins {
    id("gradlebuild.public-api-jar")
    id("gradlebuild.publish-defaults")
}

group = "org.gradle.experimental"
description = "Public API for Gradle"

dependencies {
    distribution(project(":distributions-full"))

    externalApi(libs.groovy)
}

val testRepoLocation = layout.buildDirectory.dir("repos/test")
open class SoftwareComponentFactoryProvider @Inject constructor(val factory: SoftwareComponentFactory)
val softwareComponentFactory = project.objects.newInstance(SoftwareComponentFactoryProvider::class.java).factory
val testGradleRepo = softwareComponentFactory.adhoc("testGradleRepo")
components.add(testGradleRepo)

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = moduleIdentity.baseName.get()

            from(components["gradleApi"])

            versionMapping {
                allVariants {
                    fromResolutionOf(configurations.externalRuntimeClasspath.get())
                }
            }

            pom {
                name = moduleIdentity.baseName.map { "${project.group}:$it"}
            }
        }

        create<MavenPublication>("test") {
            artifactId = moduleIdentity.baseName.get()

            from(components["testGradleRepo"])

            pom {
                name = moduleIdentity.baseName.map { "${project.group}:$it"}
            }
        }
    }
    repositories {
        maven {
            name = "test"
            url = testRepoLocation.get().asFile.toURI()
        }
    }
}

val testRepoElements = configurations.consumable("testRepoElements") {
    outgoing.artifact(testRepoLocation) {
        builtBy( "publishMavenPublicationToTestRepository")
    }
    // TODO: De-duplicate this. See publish-public-libraries
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named("gradle-local-repository"))
        attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling.EMBEDDED))
    }
}

(components["testGradleRepo"] as AdhocComponentWithVariants).addVariantsFromConfiguration(testRepoElements.get()) {
    mapToOptional() // The POM should not include dependencies of this configuration
}

// TODO De-duplicate this
/**
 * Tasks that are called by the (currently separate) promotion build running on CI.
 */
tasks.register("promotionBuild") {
    description = "Build and publish the public API jar"
    group = "publishing"
    dependsOn("publish")
}
