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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

import static PropertyStateProjectUnderTest.Language
import static org.gradle.util.TextUtil.normaliseFileSeparators

class PropertyStateIntegrationTest extends AbstractIntegrationSpec {

    private final PropertyStateProjectUnderTest projectUnderTest = new PropertyStateProjectUnderTest(testDirectory)

    @Unroll
    def "can create and use property state by custom task written as #language class"() {
        given:
        projectUnderTest.writeCustomTaskTypeToBuildSrc(language)
        buildFile << """
            task myTask(type: MyTask)
        """

        when:
        succeeds('myTask')

        then:
        projectUnderTest.assertDefaultOutputFileDoesNotExist()

        when:
        buildFile << """
             myTask {
                enabled = true
                outputFiles = files("${normaliseFileSeparators(projectUnderTest.customOutputFile.canonicalPath)}")
            }
        """
        succeeds('myTask')

        then:
        projectUnderTest.assertDefaultOutputFileDoesNotExist()
        projectUnderTest.assertCustomOutputFileContent()

        where:
        language << [Language.GROOVY, Language.JAVA]
    }

    def "can lazily map extension property state to task property with convention mapping"() {
        given:
        projectUnderTest.writeCustomGroovyBasedTaskTypeToBuildSrc()
        projectUnderTest.writePluginWithExtensionMappingUsingConventionMapping()

        when:
        succeeds('myTask')

        then:
        projectUnderTest.assertDefaultOutputFileDoesNotExist()
        projectUnderTest.assertCustomOutputFileContent()
    }

    def "can lazily map extension property state to task property with property state"() {
        given:
        projectUnderTest.writeCustomGroovyBasedTaskTypeToBuildSrc()
        projectUnderTest.writePluginWithExtensionMappingUsingPropertyState()

        when:
        succeeds('myTask')

        then:
        projectUnderTest.assertDefaultOutputFileDoesNotExist()
        projectUnderTest.assertCustomOutputFileContent()
    }
}
