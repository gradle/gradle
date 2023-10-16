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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class MainClassProvenanceIntegTest extends AbstractIntegrationSpec {

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

            abstract class QueryMainClassProvenance extends DefaultTask {
                @Input abstract Property<String> getMainClass()
                @TaskAction def printIt() {
                    println mainClass.provenance
                }
            }

            tasks.register('prov', QueryMainClassProvenance) {
                mainClass = application.mainClass
            }
        """

        when:
        run 'prov'

        then:
        outputContains "build.gradle:$position"

        where:
        assignment           | position
        "mainClass = 'Foo'"  | "7:29"
        "mainClass =  'Foo'" | "7:30"
    }
}
