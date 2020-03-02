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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.isInstant() })
class CompositeBuildPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def "included build properties take precedence over root build properties"() {
        given:
        createDir("included") {
            file("gradle.properties") << """
                theProperty=included
            """
            file("settings.gradle") << """
                println("included settings script: " + theProperty)
            """
            file("build.gradle") << """
                println("included build script: " + theProperty)
            """
        }
        file("gradle.properties") << """
            theProperty=root
        """
        settingsFile << """
            includeBuild 'included'
            println("root settings script: " + theProperty)
        """
        buildFile << """
            println("root build script: " + theProperty)
        """

        when:
        run('help')

        then:
        outputContains("root settings script: root")
        outputContains("root build script: root")
        outputContains("included settings script: included")
        outputContains("included build script: included")
    }
}
