/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

class DistributionPropertiesLoaderIntegrationTest extends AbstractIntegrationSpec {

    @Issue('https://github.com/gradle/gradle/issues/11173')
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "System properties defined in gradle.properties are available in buildSrc and in included builds"() {
        given:
        settingsFile << '''
            includeBuild 'includedBuild'
            println("system_property_available in settings.gradle:          ${System.getProperty('system_property_available', 'false')} ")
            try {
                println("project_property_available in settings.gradle:         ${project_property_available} ")
            } catch (MissingPropertyException e) {
                println("project_property_available in settings.gradle:         false ")
            }
        '''
        buildFile << '''
            println("system_property_available in root:                     ${System.getProperty('system_property_available', 'false')} ")
            println("project_property_available in root:                    ${project.findProperty('project_property_available') ?: 'false'} ")
            task hello { }
        '''
        file('buildSrc/build.gradle') << '''
            println("system_property_available in buildSrc:                 ${System.getProperty('system_property_available', 'false')} ")
            println("project_property_available in buildSrc:                ${project.findProperty('project_property_available') ?: 'false'} ")
        '''
        file('includedBuild/build.gradle') << '''
            println("system_property_available in included root:            ${System.getProperty('system_property_available', 'false')} ")
            println("project_property_available in included root:           ${project.findProperty('project_property_available') ?: 'false'} ")
        '''
        file('includedBuild/settings.gradle') << '''
            println("system_property_available in included settings.gradle: ${System.getProperty('system_property_available', 'false')} ")
            try {
                println("project_property_available in included settings.gradle:${project_property_available} ")
            } catch (MissingPropertyException e) {
                println("project_property_available in included settings.gradle:false ")
            }
        '''
        file('includedBuild/buildSrc/build.gradle') << '''
            println("system_property_available in included buildSrc:        ${System.getProperty('system_property_available', 'false')} ")
            println("project_property_available in included buildSrc:       ${project.findProperty('project_property_available') ?: 'false'} ")
        '''
        file('gradle.properties') << '''
            systemProp.system_property_available=true
            project_property_available=true
        '''.stripIndent()

        when:
        succeeds 'hello'

        then:
        outputContains('system_property_available in buildSrc:                 true')
        outputContains('system_property_available in buildSrc:                 true')
        outputContains('project_property_available in buildSrc:                false')
        outputContains('system_property_available in included buildSrc:        true')
        outputContains('project_property_available in included buildSrc:       false')
        outputContains('system_property_available in included root:            true')
        outputContains('project_property_available in included root:           false')
        outputContains('system_property_available in root:                     true')
        outputContains('project_property_available in root:                    true')
        outputContains('system_property_available in settings.gradle:          true')
        outputContains('project_property_available in settings.gradle:         true')
        outputContains('system_property_available in included settings.gradle: true')
        outputContains('project_property_available in included settings.gradle:false')
    }
}
