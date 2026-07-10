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
}
