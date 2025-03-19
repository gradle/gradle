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

            repositories {
                ${mavenCentralRepository()}
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
        def resolve = new ResolveTestFixture(buildFile, "conf")
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
        """
        resolve.prepare()
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
}
