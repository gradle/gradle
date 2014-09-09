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

class UnsupportedConfigurationMutationTest extends AbstractIntegrationSpec {

    def "does not allow adding dependencies after resolution"() {
        buildFile << """
            configurations { a }
            configurations.a.resolve()
            dependencies { a files("some.jar") }
        """
        when: fails()
        then: failure.assertHasCause("You can't change configuration 'a' because it is already resolved!")
    }

    @Issue("GRADLE-3155")
    def "does not allow adding dependencies to hierarchy after resolution"() {
        buildFile << """
            configurations {
                a
                b.extendsFrom a
            }
            configurations.b.resolve()
            dependencies { a files("some.jar") }
        """
        when: fails()
        then: failure.assertHasCause("You can't change configuration 'b' because it is already resolved!")
    }

    @Issue("GRADLE-3155")
    def "allows changing ancestor configuration if it does not affect resolved child"() {
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
