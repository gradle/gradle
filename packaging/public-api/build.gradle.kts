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
    id("signing")
}

group = "org.gradle.experimental"
description = "Public API for Gradle"

dependencies {
    distribution(projects.distributionsFull)

    // Groovy is part of our API
    externalApi(libs.groovy)
    // Required to inject services into tasks and other objects
    externalApi(libs.inject)
    // Moslty used for nullability annotations
    externalApi(libs.jsr305)
    externalApi(libs.jspecify)
    // We use this to annotate type parameters as @Nullable
    externalApi(libs.jetbrainsAnnotations)
    // SLF4J logging is part of our public API
    externalApi(libs.slf4jApi)
    // We only need this because of AntTarget :o
    externalApi(libs.ant)
}

val testRepoLocation = layout.buildDirectory.dir("repos/test")

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = gradleModule.identity.baseName.get()

            from(components["gradleApi"])

            versionMapping {
                allVariants {
                    fromResolutionOf(configurations.externalRuntimeClasspath.get())
                }
            }

            pom {
                name = gradleModule.identity.baseName.map { "${project.group}:$it"}
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

// Temporary solution as we cannot simply apply publish-public-libraries for now
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

configurations {
    gradleApiElements {
        outgoing {
            capability(gradleModule.identity.baseName.map { "$group:$it-internal:$version" })
        }
    }
}

val testRepoElements = configurations.consumable("testRepoElements") {
    outgoing.artifact(testRepoLocation) {
        builtBy( "publishMavenPublicationToTestRepository")
    }
    // TODO: De-duplicate this. See publish-public-libraries
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named("gradle-local-repository"))
    }
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
