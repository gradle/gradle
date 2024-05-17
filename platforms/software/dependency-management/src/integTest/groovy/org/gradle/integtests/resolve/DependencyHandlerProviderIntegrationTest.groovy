/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

/**
 * Tests covering the use of {@link org.gradle.api.provider.Provider} as an argument in the
 * {@link org.gradle.api.artifacts.dsl.DependencyHandler} block.
 */
class DependencyHandlerProviderIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def resolve = new ResolveTestFixture(buildFile, "conf")

    def setup() {
        settingsFile << """
            rootProject.name = 'provider'
        """
        buildFile << """
            ${mavenCentralRepository()}
            group = 'org'
            version = '1.0'
        """
        resolve.prepare()
    }

    def "mutating the provider value before it's added resolves to the correct dependency"() {
        buildFile << """
        configurations { conf }

        def lazyDep = objects.property(String).convention("org.mockito:mockito-core:1.8")

        dependencies {
            conf lazyDep
        }

        lazyDep.set("junit:junit:4.12")

        """

        when:
        succeeds'checkDeps'

        then:
        resolve.expectGraph {
            root(":", "org:provider:1.0") {
                module("junit:junit:4.12") {
                    module('org.hamcrest:hamcrest-core:1.3')
                }
            }
        }
    }

    def "works correctly with up-to-date checking"() {
        given:
        mavenHttpRepo.module("group", "projectA", "1.1").publish()
        mavenHttpRepo.module("group", "projectA", "1.2").publish()

        buildFile << """
        repositories {
            maven {
                url = "${mavenRepo.uri}"
            }
        }
        configurations { conf }

        dependencies {
            conf provider { "group:projectA:\${property('project.version')}" }
        }

        task resolve {
            inputs.files(configurations.conf)
            def outFile = file("out.txt")
            outputs.file(outFile)
            doLast {
               outFile << 'Hello'
            }
        }

        checkDeps.dependsOn resolve

        """

        when:
        args '-Pproject.version=1.1'
        succeeds 'checkDeps'

        then:
        executedAndNotSkipped ":checkDeps", ":resolve"
        resolve.expectGraph {
            root(":", "org:provider:1.0") {
                module('group:projectA:1.1')
            }
        }

        when:
        args('-Pproject.version=1.1')
        succeeds"checkDeps"

        then:
        executedAndNotSkipped ":checkDeps"
        skipped ":resolve"
        resolve.expectGraph {
            root(":", "org:provider:1.0") {
                module('group:projectA:1.1')
            }
        }

        when:
        args('-Pproject.version=1.2')
        succeeds 'checkDeps'

        then:
        executedAndNotSkipped ':checkDeps'
        resolve.expectGraph {
            root(":", "org:provider:1.0") {
                module('group:projectA:1.2')
            }
        }
    }

    @ToBeImplemented("https://github.com/gradle/gradle/issues/12972")
    def "property has no value"() {
        buildFile << """
        configurations { conf }

        def emptyDep = objects.property(String)

        dependencies {
            conf emptyDep
        }

        task resolve {
            def files = configurations.conf
            doLast {
                 files*.name
            }
        }
        """

        expect:
        succeeds("resolve")

        // TODO: the test should indeed fail, as specified above

        // when:
        // fails("resolve")
        // then:
        // failure.assertHasCause("No value has been specified for this property.")
    }

    def "provider throws an exception"() {
        buildFile << """
        configurations { conf }

        def lazyDep = provider {
            throw new GradleException("Boom!")
        }

        dependencies {
            conf lazyDep
        }
        // Do the resolve
        configurations.conf.incoming.dependencies
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Boom!")
    }

    def "reasonable error message when the provider doesn't provide a supported dependency notation"() {
        buildFile << """
        configurations { conf }

        def lazyDep = provider {
            42
        }

        dependencies {
            conf lazyDep
        }
        """

        when:
        fails ":checkDeps"

        then:
        failure.assertHasCause """Cannot convert the provided notation to an object of type Dependency: 42.
The following types/formats are supported:"""
    }

    @Issue("https://github.com/gradle/gradle/issues/13977")
    def "fails with reasonable error message if a Configuration is provided"() {
        buildFile << """
        configurations {
           conf
           other
        }

        def lazyDep = provider {
            configurations.other
        }

        dependencies {
            conf lazyDep
        }
        """

        when:
        fails ":checkDeps"

        then:
        failure.assertHasCause "Adding a configuration as a dependency using a provider isn't supported. You should call conf.extendsFrom(other) instead"
    }
}
