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

package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests dependency mapping when used with normal java libraries. Specifically, the
 * {@link org.gradle.api.publish.internal.component.ConfigurationVariantDetailsInternal.DependencyMappingDetails} API.
 */
class MavenPublishJavaDependencyMappingIntegrationTest extends AbstractIntegrationSpec {

    def "publishes included build using publication coordinates"() {
        settingsFile << """
            includeBuild("other")
        """
        file("other/build.gradle") << """
            plugins {
                id("java-library")
                id("maven-publish")
            }

            group = "lo"
            version = "cal"

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java

                        groupId = "ex"
                        artifactId = "ter"
                        version = "nal"
                    }
                }
            }
        """

        buildFile << """
            plugins {
                id("java-library")
                id("maven-publish")
            }

            [configurations.apiElements, configurations.runtimeElements].each {
                components.java.withVariantsFromConfiguration(it) {
                    dependencyMapping {
                        publishResolvedCoordinates = true
                    }
                }
            }

            dependencies {
                implementation("lo:other:cal")
            }

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java

                        groupId = "root"
                        artifactId = "root"
                        version = "root"
                    }
                }
                ${mavenTestRepository()}
            }
        """

        when:
        succeeds(":publish")


        then:
        def root = mavenRepo.module("root", "root", "root")
        def pomDeps = root.parsedPom.scopes.runtime.dependencies.values()

        pomDeps.size() == 1
        pomDeps[0].groupId == "ex"
        pomDeps[0].artifactId == "ter"
        pomDeps[0].version == "nal"

        def gmmDeps = root.parsedModuleMetadata.variant("runtimeElements").dependencies
        gmmDeps.size() == 1
        gmmDeps[0].group == "ex"
        gmmDeps[0].module == "ter"
        gmmDeps[0].version == "nal"
    }
}
