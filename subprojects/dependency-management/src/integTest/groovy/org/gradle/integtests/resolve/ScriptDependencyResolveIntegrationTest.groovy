/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.file.LeaksFileHandles
import spock.lang.Issue

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE

class ScriptDependencyResolveIntegrationTest extends AbstractDependencyResolutionTest {


    @LeaksFileHandles("Puts gradle user home in integration test dir")
    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "root component identifier has the correct type when resolving a script classpath"() {
        given:
        def module = mavenRepo().module("org.gradle", "test", "1.45")
        module.dependsOn("org.gradle", "other", "preview-1")
        module.artifact(classifier: 'classifier')
        module.publish()
        mavenRepo().module("org.gradle", "other", "preview-1").publish()

        and:
        settingsFile << """
rootProject.name = 'testproject'
"""

        buildFile << """
group = 'org.gradle'
version = '1.0'

buildscript {
    repositories { maven { url "${mavenRepo().uri}" } }
    dependencies {
        classpath "org.gradle:test:1.45"
    }
}

task check {
    doLast {
        assert buildscript.configurations.classpath.collect { it.name } == ['test-1.45.jar', 'other-preview-1.jar']
        def result = buildscript.configurations.classpath.incoming.resolutionResult

        // Check root component
        def rootId = result.root.id
        assert rootId instanceof ProjectComponentIdentifier
    }
}
"""

        expect:
        succeeds "check"
    }

    @Issue("gradle/gradle#15378")
    def "strict resolution strategy can be used when resolving a script classpath from settings"() {
        given:
        mavenRepo().module("org.gradle", "test", "1.45").publish()
        mavenRepo().module("org.gradle", "test", "1.46").publish()

        and:
        settingsFile << """
buildscript {
    repositories { maven { url "${mavenRepo().uri}" } }
    configurations.classpath {
        resolutionStrategy {
            failOnVersionConflict()
        }
    }
    dependencies {
        classpath "org.gradle:test:1.45"
        classpath "org.gradle:test:1.46"
    }
}

rootProject.name = 'testproject'
"""
        expect:
        fails "help"
        failureHasCause("Conflict found for the following module:")
        failure.assertHasResolutions("Run with :dependencyInsight --configuration classpath " +
            "--dependency org.gradle:test to get more insight on how to solve the conflict.",
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            SCAN,
            GET_HELP)
    }

    @Issue("gradle/gradle#19300")
    def 'carries implicit constraint for log4j-core'() {
        given:
        mavenRepo().module('org.apache.logging.log4j', 'log4j-core', '2.17.1').publish()

        and:
        settingsFile << """
            buildscript {
                repositories { maven { url "${mavenRepo().uri}" } }
                dependencies {
                    classpath "org.apache.logging.log4j:log4j-core"
                }
            }

            rootProject.name = 'testproject'
        """

        buildFile << """
            buildscript {
                repositories { maven { url "${mavenRepo().uri}" } }
                dependencies {
                    classpath "org.apache.logging.log4j:log4j-core"
                }
            }
        """

        expect:
        succeeds 'buildEnvironment'
        outputContains('org.apache.logging.log4j:log4j-core:{require 2.17.1; reject [2.0, 2.17.1)} -> 2.17.1 (c)')
    }

    @Issue("gradle/gradle#19300")
    def 'fails if build attempts to force vulnerable log4j-core'() {
        given:
        settingsFile << """
            rootProject.name = 'testproject'
        """

        buildFile << """
            buildscript {
                repositories { maven { url "${mavenRepo().uri}" } }
                dependencies {
                    classpath "org.apache.logging.log4j:log4j-core:2.14.1!!"
                }
            }
        """

        expect:
        fails 'help'
        failureCauseContains('Cannot find a version of \'org.apache.logging.log4j:log4j-core\' that satisfies the version constraints')
    }

    @Issue("gradle/gradle#19300")
    def 'allows to upgrade log4j to 3.x one day'() {
        given:
        mavenRepo().module('org.apache.logging.log4j', 'log4j-core', '3.1.0').publish()
        buildFile << """
            buildscript {
                repositories { maven { url "${mavenRepo().uri}" } }
                dependencies {
                    classpath "org.apache.logging.log4j:log4j-core:3.1.0"
                }
            }
        """

        expect:
        succeeds 'buildEnvironment'
        outputContains('org.apache.logging.log4j:log4j-core:{require 2.17.1; reject [2.0, 2.17.1)} -> 3.1.0 (c)')
    }
}
