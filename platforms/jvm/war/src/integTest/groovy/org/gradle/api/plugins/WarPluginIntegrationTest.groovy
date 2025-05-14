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

class WarPluginIntegrationTest extends AbstractIntegrationSpec {

    def "creates a war"() {
        given:
        file("settings.gradle") << "rootProject.name = 'test'"
        buildFile << """
            plugins {
                id 'war'
            }
        """

        file("src/main/webapp/index.jsp") << "<p>hi</p>"

        when:
        succeeds "assemble"

        then:
        executed(":war")
        file("build/libs/test.war").exists();
    }

    def "can customize archive names using convention properties"() {
        given:
        file("settings.gradle") << "rootProject.name = 'test'"
        buildFile << """
            plugins {
                id 'war'
            }
            buildDir = 'output'
            base {
                archivesName = 'test'
                libsDirectory = layout.buildDirectory.dir('archives')
            }
            version = '0.5-RC2'
        """

        file("src/main/resources/org/gradle/resource.file") << "some resource"

        when:
        succeeds "assemble"

        then:
        file("output/archives/test-0.5-RC2.war").exists();
    }

    def "generates artifacts when version is empty"() {
        given:
        file("settings.gradle") << "rootProject.name = 'empty'"
        buildFile << """
            plugins {
                id 'war'
            }
            version = ''
        """
        file("src/main/resources/org/gradle/resource.file") << "some resource"

        when:
        succeeds "assemble"

        then:
        file("build/libs/empty.war").exists();
    }

}
