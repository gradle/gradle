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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Location for complex reproducers for resolution issues, which do not have a more specific
 * home. Ideally, these tests would be more targeted, but minimizing them more is not
 * necessarily feasible or worth the time commitment. Since these tests are still valuable,
 * we can keep them here.
 */
class ResolutionIssuesRegressionIntegrationTest extends AbstractIntegrationSpec {

    def "graphs causes unattached constraint edge to exist when module goes back to pending"() {

        //region repo

        mavenRepo.module("org.foo", "uber-bom", "1.0")
            .withModuleMetadata()
            .adhocVariants()
            .variant("apiElements", [
                "org.gradle.category": "platform",
                "org.gradle.usage": "java-api"
            ])
            .publish()

        mavenRepo.module("org.bar", "client", "1.1")
            .withModuleMetadata()
            .withVariant("api") {
                dependsOn("org.foo", "uber-bom", "1.0", [
                    "org.gradle.category": "platform"
                ])
                dependsOn("org.bar", "common", "1.1")
                dependsOn("org.baz", "main", null)
                dependsOn("org.baz", "actions", null)
                dependsOn("org.baz", "feature", null)
                dependsOn("org.baz", "feature", null) {
                    requestedCapability("org.baz", "feature-test-fixtures", null)
                }
            }
            .publish()

        mavenRepo.module("org.bar", "client-testing", "1.1")
            .withModuleMetadata()
            .withVariant("api") {
                dependsOn("org.bar", "client", "1.1")
            }
            .publish()

        mavenRepo.module("org.bar", "common", "1.1")
            .withModuleMetadata()
            .withVariant("api") {
                dependsOn("org.foo", "uber-bom", "1.0", [
                    "org.gradle.category": "platform"
                ])
            }
            .publish()

        mavenRepo.module("org.baz", "actions", "1.2")
            .withModuleMetadata()
            .publish()

        mavenRepo.module("org.baz", "bom", "1.2")
            .withModuleMetadata()
            .adhocVariants()
            .variant("apiElements", [
                "org.gradle.category": "platform",
                "org.gradle.usage": "java-api"
            ]) {
                constraint("org.baz", "actions", "1.2")
            }
            .publish()

        mavenRepo.module("org.baz", "feature", "1.2")
            .withModuleMetadata()
            .publish()

        mavenRepo.module("org.junit", "junit-bom", "5.10.2")
            .withModuleMetadata()
            .adhocVariants()
            .variant("apiElements", [
                "org.gradle.category": "platform",
                "org.gradle.usage": "java-api"
            ])
            .publish()

        mavenRepo.module("org.junit", "junit-bom", "5.11.3")
            .withModuleMetadata()
            .adhocVariants()
            .variant("apiElements", [
                "org.gradle.category": "platform",
                "org.gradle.usage": "java-api"
            ])
            .publish()

        mavenRepo.module("org.junit.jupiter", "junit-jupiter-params", "5.11.3")
            .withModuleMetadata()
            .withVariant("api") {
                dependsOn("org.junit", "junit-bom", "5.11.3") {
                    attribute("org.gradle.category", "platform")
                    endorseStrictVersions = true
                }
            }
            .publish()

        //endregion

        //region main

        file("main/main/build.gradle.kts") << """
            plugins {
              id("java-library")
            }

            dependencies {
              api(enforcedPlatform(project(":bom")))
            }
        """

        file("main/actions/build.gradle.kts") << """
            plugins {
              id("java-library")
            }

            group = "org.baz"
        """

        file("main/external/build.gradle.kts") << """
            plugins {
              id("java-library")
            }

            dependencies {
              api(enforcedPlatform(project(":bom")))
            }
        """

        file("main/bom/build.gradle.kts") << """
            plugins {
              id("java-platform")
            }

            group = "org.baz"

            dependencies {
              constraints {
                api(project(":actions"))
                api(project(":feature"))
              }
            }
        """

        file("main/feature/build.gradle.kts") << """
            plugins {
              id("java-library")
              id("java-test-fixtures")
            }

            group = "org.baz"

            dependencies {
              api(enforcedPlatform(project(":bom")))
            }
        """

        file("main/settings.gradle.kts") << """
            include(":main")
            include(":actions")
            include(":external")
            include(":bom")
            include(":feature")
        """

        //endregion

        file("settings.gradle.kts") << """
            includeBuild(".")

            dependencyResolutionManagement {
              repositories {
                maven(url = "${mavenRepo.uri.toString()}") {
                  metadataSources {
                    gradleMetadata()
                  }
                }
              }
            }

            includeBuild("main") {
              dependencySubstitution {
                substitute(module("org.baz:main")).using(project(":main"))
                substitute(module("org.baz:external")).using(project(":external"))
              }
            }

            include(":uber-bom")
            include(":base")
        """

        file("uber-bom/build.gradle.kts") << """
            plugins {
              id("java-platform")
            }

            group = "org.foo"

            javaPlatform {
              allowDependencies()
            }

            dependencies {
              api(platform("org.baz:bom:1.2")) {
                (this as ModuleDependency).doNotEndorseStrictVersions()
              }
            }
        """

        file("base/build.gradle.kts") << """
            plugins {
              id("java-library")
            }

            configurations.all {
              resolutionStrategy {
                preferProjectModules()
              }
            }

            dependencies {
                implementation(platform(project(":uber-bom")))
                implementation(platform("org.junit:junit-bom:5.10.2"))
                implementation("org.bar:client:1.1")
                implementation("org.baz:external")
                implementation("org.bar:client-testing:1.1")
                implementation("org.junit.jupiter:junit-jupiter-params:5.11.3")
            }
        """

        expect:
        succeeds(":base:dependencies", "--configuration", "compileClasspath")
    }
}
