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
package org.gradle.integtests


import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.util.GradleVersion
import org.junit.Assume
import spock.lang.Issue

@SuppressWarnings("IntegrationTestFixtures")
@TargetVersions("6.2+")
class WrapperPropertiesLoaderCrossVersionTest extends CrossVersionIntegrationSpec {

    @Issue('https://github.com/gradle/gradle/issues/11173')
    void "System properties defined in gradle.properties are available in buildSrc and in included builds"() {
        given:
        GradleDistribution wrapperVersion = previous
        GradleDistribution executionVersion = current
        Assume.assumeTrue("skipping $wrapperVersion as its wrapper cannot execute version ${executionVersion.version.version}", wrapperVersion.wrapperCanExecute(executionVersion.version))
        Assume.assumeTrue("skipping execute version ${executionVersion.version.version} as it is <6.0", executionVersion.version >= GradleVersion.version("6.0"))

        requireOwnGradleUserHomeDir()

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

        version(wrapperVersion).withTasks('wrapper').run()

        when:
        GradleExecuter executer = version(wrapperVersion).requireIsolatedDaemons()
        String output =  executer.usingExecutable('gradlew').withTasks('hello').run().output

        then:
        output.contains('system_property_available in buildSrc:                 true')
        output.contains('system_property_available in buildSrc:                 true')
        output.contains('project_property_available in buildSrc:                true')
        output.contains('system_property_available in included buildSrc:        true')
        output.contains('project_property_available in included buildSrc:       true')
        output.contains('system_property_available in included root:            true')
        output.contains('project_property_available in included root:           true')
        output.contains('system_property_available in root:                     true')
        output.contains('project_property_available in root:                    true')
        output.contains('system_property_available in settings.gradle:          true')
        output.contains('system_property_available in included settings.gradle: true')

        cleanup:
        cleanupDaemons(executer, executionVersion)
    }

    static void cleanupDaemons(GradleExecuter executer, GradleDistribution executionVersion) {
        new DaemonLogsAnalyzer(executer.daemonBaseDir, executionVersion.version.version).killAll()
    }
}

