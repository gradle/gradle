/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugin.use

import org.gradle.api.Plugin
import org.gradle.integtests.fixtures.AbstractIntegrationSpec


class ScriptPluginUseClassLoadingIntegrationSpec extends AbstractIntegrationSpec {

    def "applied script can see classes defined in buildSrc"() {

        given:
        file("other.gradle") << userTypeUsage

        and:
        file("buildSrc/src/main/groovy/${userTypeName}.groovy") << userTypeDefinition

        and:
        buildFile << """

            plugins {
                script("other.gradle")
            }

        """.stripIndent()

        expect:
        succeeds "help"
    }

    def "applied script can see classes from the Gradle API"() {

        given:
        file("other.gradle") << gradleTypeUsage

        and:
        buildFile << """

            plugins {
                script("other.gradle")
            }

        """.stripIndent()

        expect:
        succeeds "help"
    }

    def "applied script cannot see classes defined in the applying script"() {

        given:
        file("other.gradle") << userTypeUsage

        and:
        buildFile << """

            plugins {
                script("other.gradle")
            }
            
            $userTypeDefinition

        """.stripIndent()

        when:
        fails "help"

        then:
        failureCauseContains("script plugin 'other.gradle': 3: unable to resolve class $userTypeName")
    }

    def "applied script cannot see classes defined in parent project build script"() {

        given:
        file("other.gradle") << userTypeUsage

        and:
        settingsFile << """

            include("sub")

        """.stripIndent()

        and:
        buildFile << userTypeDefinition
        file("sub/build.gradle") << """

            plugins {
                script("../other.gradle")
            }

        """.stripIndent()

        when:
        fails "help"

        then:
        failureCauseContains("script plugin 'other.gradle': 3: unable to resolve class $userTypeName")
    }

    def "applying script cannot see classes defined in the applied script plugin"() {

        given:
        file("other.gradle") << userTypeDefinition

        and:
        buildFile << """

            plugins {
                script("other.gradle")
            }
            
            $userTypeUsage
            
        """.stripIndent()

        when:
        fails "help"

        then:
        failureCauseContains("${File.separatorChar}build.gradle': 9: unable to resolve class $userTypeName")
    }

    def "child project build script cannot see classes defined in script plugin applied to a parent project"() {

        given:
        file("other.gradle") << userTypeDefinition

        and:
        settingsFile << """

            include("sub")

        """.stripIndent()

        and:
        buildFile << """

            plugins {
                script("other.gradle")
            }

        """.stripIndent()
        file("sub/build.gradle") << userTypeUsage

        when:
        fails "help"

        then:
        failureCauseContains("${File.separatorChar}sub${File.separatorChar}build.gradle': 3: unable to resolve class $userTypeName")
    }

    def userTypeName = "UserType"

    def userTypeDefinition = """

        class $userTypeName {}

    """.stripIndent()

    def userTypeUsage = """

        new $userTypeName()

    """.stripIndent()

    def gradleTypeUsage = """

        class $userTypeName implements ${Plugin.class.name} {
            @Override
            public void apply(Object target) {}
        }

    """.stripIndent()

}
