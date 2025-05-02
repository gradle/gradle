/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString

class BuildScriptErrorIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        settingsFile << "rootProject.name = 'ProjectError'"
    }

    def "produces reasonable error message when buildFile evaluation fails with Groovy Exception"() {
        buildFile << """
    createTakk('do-stuff')
"""
        when:
        fails()

        then:
        failure.assertHasDescription("A problem occurred evaluating root project 'ProjectError'.")
                .assertHasCause("Could not find method createTakk() for arguments [do-stuff] on root project 'ProjectError")
                .assertHasFileName("Build file '$buildFile'")
                .assertHasLineNumber(2)
    }

    def "produces reasonable error message when buildFile evaluation fails on script compilation"() {
        buildFile << """
    // a comment
    import org.gradle.unknown.Unknown
    new Unknown()
"""

        when:
        fails()

        then:
        failure.assertHasDescription("Could not compile build file '${buildFile}'.")
                .assertThatCause(containsString("build file '$buildFile': 3: unable to resolve class org.gradle.unknown.Unknown"))
                .assertHasFileName("Build file '$buildFile'")
                .assertHasLineNumber(3)
    }

    def "produces reasonable error message when buildFile evaluation fails with exception"() {
        when:
        buildFile << """
    throw new RuntimeException("script failure")
    task test
"""
        then:
        fails('test')
        failure.assertHasDescription("A problem occurred evaluating root project 'ProjectError'.")
                .assertHasCause("script failure")
                .assertHasFileName("Build file '${buildFile.path}'")
                .assertHasLineNumber(2)
    }

    @Issue("https://github.com/gradle/gradle/issues/29159")
    @ToBeFixedForIsolatedProjects(because = "evaluationDependsOn is not IP compatible")
    def "produces reasonable error message when nested buildFile evaluation fails"() {
        createDirs("child")
        settingsFile << """
include 'child'
"""
        buildFile << """
    evaluationDependsOn 'child'
    task t
"""
        final childBuildFile = file("child/build.gradle")
        childBuildFile << """
    def broken = { ->
        throw new RuntimeException('failure')
    }
    broken()
"""

        when:
        fails()

        then:
        failure.assertHasDescription("A problem occurred evaluating project ':child'.")
                .assertHasCause("failure")
                .assertHasFileName("Build file '$childBuildFile'")
                .assertHasLineNumber(3)
    }

    @Requires(
        value = IntegTestPreconditions.NotIsolatedProjects,
        reason = "Exercises IP incompatible behavior: Groovy method inheritance"
    )
    def "produces reasonable error message from a method inherited from a script containing only methods"() {
        createDirs("child")
        settingsFile << """
include 'child'
"""
        buildFile << """
// Build script contains only methods
def broken() {
    throw new RuntimeException('failure')
}

def doSomething() {
    broken()
}
"""
        final childBuildFile = file("child/build.gradle")
        childBuildFile << """
    doSomething()
"""

        when:
        fails()

        then:
        failure.assertHasDescription("A problem occurred evaluating project ':child'.")
                .assertHasCause("failure")
                .assertHasFileName("Build file '$buildFile'")
                .assertHasLineNumber(4)
    }

    @Issue("https://github.com/gradle/gradle/issues/14984")
    def "referencing a non-existing closure-taking method yields a helpful error message"() {
        buildFile << """
            plugins {
                id("java")
            }

            java {
                toolchains { // should be toolchain
                }
            }
        """

        expect:
        fails()
        failure.assertThatCause(containsNormalizedString("Could not find method toolchains() for arguments"))
        failure.assertThatCause(containsNormalizedString(" on extension 'java' of type org.gradle.api.plugins.internal.DefaultJavaPluginExtension."))
    }

    @Issue("https://github.com/gradle/gradle/issues/19282")
    def "referencing non-existing method within a closure yields a helpful error message"() {
        buildFile << """
            plugins {
                id("java")
            }
            testing {
                suites {
                    test {
                        dependencies {
                            implementation(iDontExist('foo-bar'))
                        }
                    }
                }
            }
        """

        expect:
        fails()
        failure.assertHasCause("Could not find method iDontExist() for arguments [foo-bar] on property 'dependencies' of type java.lang.Object.")
    }
}
