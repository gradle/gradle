/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.constraints

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

class DependencyConstraintsBugsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def resolve = new ResolveTestFixture(testDirectory)

    // Ideally this should be a reproducer using generated dependencies but I wasn't able
    // to figure out a reproducer
    @Issue("https://github.com/gradle/gradle/issues/13960")
    def "should resolve dependency which version is provided by an upgraded transitive platform"() {
        given:
        // io.ktor:ktor-bom:1.3.2 is not available in mavenCentral() and the original issue this test covers
        // is only reproducible with io.micronaut:micronaut-bom:2.0.1 which depends on io.ktor:ktor-bom:1.3.2
        // The original issue can still be reproduced using the following placeholder pom for this missing dependency
        // with Gradle 6.5.1 where the issue was originally reported
        file('ktor-repo/io/ktor/ktor-bom/1.3.2/ktor-bom-1.3.2.pom') << """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>io.ktor</groupId>
              <artifactId>ktor-bom</artifactId>
              <version>1.3.2</version>
              <packaging>pom</packaging>
              <name>ktor-bom</name>
            </project>
        """
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            repositories {
                maven {
                   url = file("./ktor-repo/")
                }
            }

            dependencies {
                implementation(platform("io.micronaut:micronaut-bom:2.0.1"))
                implementation("io.micronaut.kafka:micronaut-kafka")
                testImplementation("io.micronaut.test:micronaut-test-spock")
            }

            task resolve {
                def files = configurations.testRuntimeClasspath
                inputs.files(files)
                doLast {
                    files*.name.each {
                        println(it)
                    }
                }
            }
        """

        when:
        succeeds 'resolve'

        then:
        noExceptionThrown()
        outputContains "micronaut-messaging-2.0.1.jar"
    }

    def "can use a provider to declare a dependency constraint"() {
        settingsFile << """
            rootProject.name = 'test'
        """

        buildFile << """
            repositories {
                maven { url = "${mavenHttpRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                constraints {
                    conf providers.provider { "org:foo:1.1" }
                }
                conf "org:foo"
            }
            ${resolve.configureProject("conf")}
        """
        def module = mavenHttpRepo.module("org", "foo", "1.1").publish()

        when:
        module.pom.expectGet()
        module.artifact.expectGet()
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo", "org:foo:1.1") {
                    byConstraint()
                }
                constraint("org:foo:1.1")
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/36961")
    def "can defer selection of component controlled by platform, where older versioned edge targeting component would not successfully resolve in newer version"() {
        def second1 = mavenRepo.module("org", "second", "1.0")
            .withModuleMetadata()
            .withoutDefaultVariants()
            .variant("foo", ["attr": "val"]) {
                capability("org", "other", "1.0")
            }
            .publish()
        mavenRepo.module("org", "first", "1.0")
            .withModuleMetadata()
            .withVariant("runtime") {
                dependsOn(second1) {
                    requestedCapability("org", "other", "1.0")
                }
            }
            .publish()

        def second2 = mavenRepo.module("org", "second", "2.0")
            .publish()
        def first2 = mavenRepo.module("org", "first", "2.0")
            .dependsOn(second2)
            .publish()

        def bom2 = mavenRepo.module("org", "bom", "2.0")
            .hasPackaging('pom')
            .dependsOn(["optional": true], first2)
            .dependsOn(["optional": true], second2)
            .publish()

        mavenRepo.module("org", "transitive", "2.0")
            .withModuleMetadata()
            .withVariant("runtime") {
                dependsOn(second2)
                dependsOn(bom2) {
                    attribute("org.gradle.category", "platform")
                }
            }
            .publish()

        settingsFile << """
            rootProject.name = 'test'
        """

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("org:first:1.0")
                implementation("org:transitive:2.0")
            }
            ${resolve.configureProject("runtimeClasspath")}
        """

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:first:1.0", "org:first:2.0") {
                    byConstraint()
                    byConflictResolution("between versions 2.0 and 1.0")
                    module("org:second:2.0")
                }
                module("org:transitive:2.0") {
                    module("org:second:2.0") {
                        byConstraint()
                        byConflictResolution("between versions 2.0 and 1.0")
                    }
                    module("org:bom:2.0") {
                        noArtifacts()
                        constraint("org:first:2.0")
                        constraint("org:second:2.0")
                    }
                }
            }
        }
    }

    def "throwing eachDependency rule on a coordinate carrying only a phantom constraint surfaces the rule's exception"() {
        // Reproduces this scenario:
        //   producer: declares a constraint on org:foo:1.1, but does not depend on org:foo
        //   app: depends on producer and on org:foo:1.0; registers an eachDependency rule that throws
        //        whenever it sees a non-1.1 version of org:foo
        //
        // The throwing rule fires on org:foo:1.0. Historically the rule's exception
        // was swallowed and the build failed with a confusing internal error referencing the
        // binary store and an EdgeState with no target component. The rule's own exception should
        // be surfaced as the resolution failure cause.
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "1.1").publish()

        createDirs("producer", "app")
        settingsFile << """
            rootProject.name = 'test'
            include 'producer', 'app'
        """

        file("producer/build.gradle") << """
            plugins { id 'java-library' }
            dependencies {
                constraints {
                    api 'org:foo:1.1'
                }
            }
        """

        file("app/build.gradle") << """
            plugins { id 'java' }
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            dependencies {
                implementation project(':producer')
                implementation 'org:foo:1.0'
            }
            configurations.all {
                resolutionStrategy.eachDependency { details ->
                    if (details.requested.group == 'org' && details.requested.name == 'foo'
                        && !(details.requested.version ?: '').startsWith('1.1')) {
                        throw new org.gradle.api.GradleException("enforced version check failed for foo:\${details.requested.version}")
                    }
                }
            }
            tasks.register('resolveRuntime') {
                def files = configurations.runtimeClasspath
                doLast { files.files }
            }
        """

        when:
        fails ':app:resolveRuntime'

        then:
        failure.assertHasCause("enforced version check failed for foo:1.0")
        failure.assertHasNoCause("Problems writing to Binary store")
        failure.assertHasNoCause("No target component for edge")
    }

    def "selects a module whose pending constraint comes from a node cut off from the root"() {
        given:
        def pending1 = module("pending", "1").publish()
        def pending3 = module("pending", "3").publish()
        def upgraded2 = module("upgraded", "2").dependsOn(pending1).publish()
        // constrainer:3 upgrades upgraded to 2, and has a pending constraint on pending until upgraded:2 depends on it
        def constrainer3 = module("constrainer", "3").dependencyConstraint(upgraded2).dependencyConstraint(pending3).publish()
        // cycle-a:1 <-> cycle-b:2 is a cycle, only reached through upgraded:1, which the upgrade of upgraded evicts. The
        // cycle keeps constrainer:3 in the graph, cut off from the root
        def cycleA1 = module("cycle-a", "1")
        def cycleB2 = module("cycle-b", "2").dependsOn(cycleA1).publish()
        cycleA1.dependsOn(cycleB2).dependsOn(constrainer3).publish()
        module("upgraded", "1").dependsOn(cycleA1).publish()

        settingsFile << """
            rootProject.name = "test"
            // Otherwise the nodes cut off from the root also deliver their artifacts
            enableFeaturePreview("ENHANCED_GRAPH_ORDERING")
        """
        buildFile << """
            ${mavenTestRepository()}
            configurations {
                create("conf")
            }
            dependencies {
                conf("org:upgraded:1")
            }
            ${resolve.configureProject("conf")}
        """

        when:
        succeeds(":checkDeps")

        then:
        // upgraded:2 is only selected because of the constraint of constrainer:3, which is cut off from the root. The
        // cycle keeps constrainer:3 in the graph, but its constraint on pending is never applied.
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:upgraded:1", "org:upgraded:2") {
                    byConstraint()
                    byConflictResolution("between versions 2 and 1")
                    module("org:pending:1")
                }
            }
        }
    }

    def "resolution ends when a node with a deferred edge is cut off from the root"() {
        given:
        module("upgraded", "3").publish()
        module("other", "2").publish()
        module("other", "3").publish()
        module("endorsed", "2").publish()
        // org:substituted is substituted by project :substituted below
        def substituted3 = module("substituted", "3").publish()
        def endorsed3 = module("endorsed", "3").dependsOn(substituted3).publish()
        def upgraded2 = module("upgraded", "2").dependsOn(endorsed3, strictly: "3", endorseStrictVersions: true).publish()
        module("lib", "1").dependsOn(upgraded2).publish()

        settingsFile << """
            rootProject.name = "test"
            include("substituted")
            // Otherwise the nodes cut off from the root also deliver their artifacts
            enableFeaturePreview("ENHANCED_GRAPH_ORDERING")
        """
        // The project upgrades `upgraded` to 3, which evicts upgraded:2, the only path to endorsed:3 and the project.
        file("substituted/build.gradle") << """
            ${mavenTestRepository()}
            group = "org"
            version = "3"
            // Constraints come first, as the dependencies of a project are ordered by the name of their configuration.
            def constraintsScope = configurations.dependencyScope("aConstraints")
            def dependenciesScope = configurations.dependencyScope("bDependencies")
            configurations.consumable("runtimeElements") {
                extendsFrom(constraintsScope.get(), dependenciesScope.get())
                attributes.attribute(Attribute.of("custom", String), "value")
            }
            dependencies {
                constraints {
                    aConstraints("org:upgraded:3")
                    aConstraints("org:other:3")
                }
                bDependencies("org:other:2")
                bDependencies("org:endorsed:2")
            }
        """
        buildFile << """
            ${mavenTestRepository()}
            configurations {
                create("conf") {
                    attributes.attribute(Attribute.of("custom", String), "value")
                    resolutionStrategy.dependencySubstitution {
                        substitute(module("org:substituted")).using(project(":substituted"))
                    }
                }
            }
            dependencies {
                conf("org:lib:1")
            }
            ${resolve.configureProject("conf")}
        """

        when:
        succeeds(":checkDeps")

        then:
        // upgraded:3 is only selected because of the constraint of the project, which is cut off from the root
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:lib:1") {
                    edge("org:upgraded:2", "org:upgraded:3") {
                        byConstraint()
                        byConflictResolution("between versions 3 and 2")
                    }
                }
            }
        }
    }

    private def module(String name, String version) {
        return mavenRepo.module("org", name, version).withModuleMetadata()
    }

}
