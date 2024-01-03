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

package org.gradle.integtests.tooling.r60


import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestOutputStream
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import spock.lang.Issue

@ToolingApiVersion('>=3.0')
@TargetGradleVersion('>=6.0 <6.2')
class ToolingApiPropertiesLoaderCrossVersionSpec extends AbstractToolingApiPropertiesLoaderCrossVersionSpec {

    @Override
    boolean projectPropertyAvailableInIncludedRoot() {
        false
    }

    @Override
    boolean projectPropertyAvailableInIncludedBuildSrc() {
        false
    }

    @Override
    boolean projectPropertyAvailableInBuildSrc() {
        false
    }
}

abstract class AbstractToolingApiPropertiesLoaderCrossVersionSpec extends ToolingApiSpecification {

    @Issue('https://github.com/gradle/gradle/issues/11173')
    def "System properties defined in gradle.properties are available in buildSrc and in included builds"() {
        setup:
        settingsFile << '''
            includeBuild 'includedBuild'
            println("system_property_available in settings.gradle:          ${System.getProperty('system_property_available', 'false')} ")
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
        '''
        file('includedBuild/buildSrc/build.gradle') << '''
            println("system_property_available in included buildSrc:        ${System.getProperty('system_property_available', 'false')} ")
            println("project_property_available in included buildSrc:       ${project.findProperty('project_property_available') ?: 'false'} ")
        '''
        file('gradle.properties') << '''
            systemProp.system_property_available=true
            project_property_available=true
        '''.stripIndent()

        TestOutputStream stdout = new TestOutputStream()

        when:
        withConnection { connection -> connection.newBuild().setStandardOutput(stdout).forTasks('hello').run() }
        String output = stdout.toString()

        then:
        output.contains('system_property_available in buildSrc:                 true')
        output.contains('system_property_available in buildSrc:                 true')
        output.contains("project_property_available in buildSrc:                ${projectPropertyAvailableInBuildSrc()}")
        output.contains('system_property_available in included buildSrc:        true')
        output.contains("project_property_available in included buildSrc:       ${projectPropertyAvailableInIncludedBuildSrc()}")
        output.contains('system_property_available in included root:            true')
        output.contains("project_property_available in included root:           ${projectPropertyAvailableInIncludedRoot()}")
        output.contains('system_property_available in root:                     true')
        output.contains('project_property_available in root:                    true')
        output.contains('system_property_available in settings.gradle:          true')
        output.contains('system_property_available in included settings.gradle: true')
    }

    abstract boolean projectPropertyAvailableInBuildSrc();

    abstract boolean projectPropertyAvailableInIncludedRoot();

    abstract boolean projectPropertyAvailableInIncludedBuildSrc();
}
