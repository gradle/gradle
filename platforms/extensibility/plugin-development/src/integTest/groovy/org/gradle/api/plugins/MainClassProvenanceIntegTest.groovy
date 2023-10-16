/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class MainClassProvenanceIntegTest extends AbstractIntegrationSpec {

    def 'it works for local Groovy plugin'() {
        given:
        groovyFile file('buildSrc/build.gradle'), '''
            plugins {
                id 'groovy-gradle-plugin'
            }
        '''
        groovyFile file('buildSrc/src/main/groovy/my-application.gradle'), """
            plugins {
                id 'application'
            }

            application {
                $assignment
            }
        """

        buildFile """
            plugins {
                id 'my-application'
            }

            println application.mainClass.provenance
        """

        when:
        run 'build'

        then:
        outputContains "my-application.gradle:$position"

        where:
        assignment           | position
        "mainClass = 'Foo'"  | "7:29"
        "mainClass =  'Foo'" | "7:30"
    }

    def 'it works for direct assignment'() {
        given:
        buildFile """
            plugins {
                id 'application'
            }

            application {
                $assignment
            }

            println application.mainClass.provenance
        """

        when:
        run 'build'

        then:
        outputContains "build.gradle:$position"

        where:
        assignment           | position
        "mainClass = 'Foo'"  | "7:29"
        "mainClass =  'Foo'" | "7:30"
    }

    def 'it works for chained assignments'() {
        given:
        buildFile """
            plugins {
                id 'application'
            }

            application {
                $assignment
            }

            def chained = objects.property(String)
            chained.set(application.mainClass)

            println chained.provenance
        """

        when:
        run 'build'

        then:
        outputContains "build.gradle:$position"

        where:
        assignment           | position
        "mainClass = 'Foo'"  | "7:29"
        "mainClass =  'Foo'" | "7:30"
    }

    def 'it works for direct assignment in Kotlin'() {
        given:
        buildKotlinFile << """
            plugins {
                id("application")
            }

            application {
                $assignment
            }

            println((application.mainClass as $DefaultProperty.name<*>).provenance)
        """

        when:
        run 'build'

        then:
        outputContains "build.gradle.kts:$position"

        where:
        assignment           | position
        'mainClass = "Foo"'  | "7:29"
        'mainClass =  "Foo"' | "7:30"
    }

    def 'it works for chained assignment in Kotlin'() {
        given:
        buildKotlinFile << """
            plugins {
                id("application")
            }

            application {
                $assignment
            }

            val chained = objects.property<String>()
            chained = application.mainClass

            println((chained as $DefaultProperty.name<*>).provenance)
        """

        when:
        run 'build'

        then:
        outputContains "build.gradle.kts:$position"

        where:
        assignment           | position
        'mainClass = "Foo"'  | "7:29"
        'mainClass =  "Foo"' | "7:30"
    }

}
