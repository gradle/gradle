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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

// TODO - report on the configuration that was actually changed
// TODO - warn about configurations resolved via a project dependency
// TODO - verify line number is included in deprecation message
// TODO - warn about changes to artifacts
class UnsupportedConfigurationMutationTest extends AbstractIntegrationSpec {

    def "does not allow adding dependencies to a configuration that has been resolved"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            dependencies { a files("some.jar") }
        """
        when: fails()
        then: failure.assertHasCause("Cannot change configuration ':a' after it has been resolved.")
    }

    @Issue("GRADLE-3155")
    def "warns about adding dependencies to hierarchy after resolution"() {
        buildFile << """
            configurations {
                a
                b.extendsFrom a
            }
            configurations.b.resolve()
            dependencies { a files("some.jar") }
        """
        executer.withDeprecationChecksDisabled()

        when: succeeds()
        then: output.contains("Attempting to change configuration ':b' after it has been resolved. This behaviour has been deprecated and is scheduled to be removed in Gradle 3.0")
    }

    @Issue("GRADLE-3155")
    def "allows changing a configuration when the change does not affect resolved child configuration"() {
        buildFile << """
            configurations {
                a
                b.extendsFrom a
                b.resolve()
                a.description = 'some conf'
            }
        """
        expect: succeeds()
    }
}
