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

@ToolingApiVersion('>=6.0')
@TargetGradleVersion('>=6.0')
class ToolingApiPropertiesLoaderCrossVersionSpec extends ToolingApiSpecification{

    @Issue('https://github.com/gradle/gradle/issues/11173')
    def "System properties defined in gradle.properties are available in buildSrc and in included builds"() {
        setup:
        settingsFile << 'includeBuild "includedBuild"'
        buildFile << '''
            println("system_property_available in root:                    ${System.getProperty('system_property_available', 'false')} ")
            println("project_property_available in root:                   ${project.findProperty('project_property_available') ?: 'false'} ")
            task hello { }
        '''
        file('buildSrc/build.gradle') << '''
            println("system_property_available in buildSrc:                ${System.getProperty('system_property_available', 'false')} ")
            println("project_property_available in buildSrc:               ${project.findProperty('project_property_available') ?: 'false'} ")
        '''
        file('includedBuild/build.gradle') << '''
            println("system_property_available in includedBuild root:      ${System.getProperty('system_property_available', 'false')} ")
            println("project_property_available in includedBuild root:     ${project.findProperty('project_property_available') ?: 'false'} ")
        '''
        file('includedBuild/buildSrc/build.gradle') << '''
            println("system_property_available in includedBuild buildSrc:  ${System.getProperty('system_property_available', 'false')} ")
            println("project_property_available in includedBuild buildSrc: ${project.findProperty('project_property_available') ?: 'false'} ")
        '''
        file('gradle.properties') << '''
            systemProp.system_property_available=true
            project_property_available=true
        '''.stripIndent()

        TestOutputStream stdout = new TestOutputStream()

        when:
        withConnection { connection -> connection.newBuild().setStandardOutput(stdout).forTasks('hello').run() }
        String result = stdout.toString()

        then:
        assert result.contains('system_property_available in buildSrc:                true')
        assert result.contains('system_property_available in buildSrc:                true')
        assert result.contains('project_property_available in buildSrc:               false')
        assert result.contains('system_property_available in includedBuild buildSrc:  true')
        assert result.contains('project_property_available in includedBuild buildSrc: false')
        assert result.contains('system_property_available in includedBuild root:      true')
        assert result.contains('project_property_available in includedBuild root:     false')
        assert result.contains('system_property_available in root:                    true')
        assert result.contains('project_property_available in root:                   true')
    }
}
