/*
 * Copyright 2010 the original author or authors.
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

package org.gradle

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.internal.DefaultTaskExecutionRequest
import org.gradle.internal.RunDefaultTasksExecutionRequest
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.Matchers.isSerializable
import static org.hamcrest.MatcherAssert.assertThat

class StartParameterTest extends Specification {
    @Rule private TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    @Rule private SetSystemProperties systemProperties = new SetSystemProperties()

    void "new instance has correct state"() {
        def parameter = new StartParameter()
        parameter.settingsFile = 'settingsfile' as File
        parameter.buildFile = 'buildfile' as File
        parameter.taskNames = ['a']
        parameter.buildProjectDependencies = true
        parameter.currentDir = new File('a')
        parameter.projectProperties = [a: 'a']
        parameter.systemPropertiesArgs = [b: 'b']
        parameter.gradleUserHomeDir = new File('b')
        parameter.initScripts = [new File('init script'), new File("/path/to/another init script")]
        parameter.logLevel = LogLevel.WARN
        parameter.consoleOutput = ConsoleOutput.Auto
        parameter.continueOnFailure = true
        parameter.rerunTasks = true
        parameter.refreshDependencies = true
        parameter.configureOnDemand = true
        parameter.parallelProjectExecutionEnabled = true
        parameter.buildCacheEnabled = true
        parameter.writeDependencyLocks = true
        parameter.lockedDependenciesToUpdate = ['foo']
        parameter.includeBuild(new File('participant'))

        when:
        def newInstance = parameter.newInstance()

        then:
        parameter == newInstance

        when:
        newInstance.continueOnFailure = false

        then:
        parameter != newInstance
    }

    void "mutable collections are not shared"() {
        def parameter = new StartParameter()
        parameter.taskNames = ['a']
        parameter.excludedTaskNames = ['foo']
        parameter.projectProperties = [a: 'a']
        parameter.systemPropertiesArgs = [b: 'b']
        parameter.initScripts = [new File('init script'), new File("/path/to/another init script")]
        parameter.includedBuilds = [new File('participant'), new File("/path/to/another/participant")]

        when:
        def newInstance = parameter.newInstance()

        then:
        !parameter.initScripts.is(newInstance.initScripts)
        !parameter.taskNames.is(newInstance.taskNames)
        !parameter.excludedTaskNames.is(newInstance.excludedTaskNames)
        !parameter.projectProperties.is(newInstance.projectProperties)
        !parameter.systemPropertiesArgs.is(newInstance.systemPropertiesArgs)
        !parameter.includedBuilds.is(newInstance.includedBuilds)

        and:
        parameter.initScripts == newInstance.initScripts
        parameter.taskNames == newInstance.taskNames
        parameter.excludedTaskNames == newInstance.excludedTaskNames
        parameter.projectProperties == newInstance.projectProperties
        parameter.systemPropertiesArgs == newInstance.systemPropertiesArgs
        parameter.includedBuilds == newInstance.includedBuilds
    }

    void "default values"() {
        def parameter = new StartParameter()

        expect:
        parameter.gradleUserHomeDir == StartParameter.DEFAULT_GRADLE_USER_HOME
        parameter.currentDir == new File(System.getProperty("user.dir")).getCanonicalFile()

        parameter.buildFile == null
        parameter.settingsFile == null

        parameter.logLevel == LogLevel.LIFECYCLE
        parameter.consoleOutput == ConsoleOutput.Auto
        assertRunsDefaultTasks(parameter)
        parameter.excludedTaskNames.empty
        parameter.projectProperties.isEmpty()
        parameter.systemPropertiesArgs.isEmpty()
        !parameter.dryRun
        !parameter.continueOnFailure
        !parameter.rerunTasks
        !parameter.refreshDependencies
        !parameter.parallelProjectExecutionEnabled
        !parameter.buildCacheEnabled
        !parameter.writeDependencyLocks
        parameter.lockedDependenciesToUpdate.isEmpty()

        assertThat(parameter, isSerializable())
    }

    void "uses gradle user home system property"() {
        def gradleUserHome = tmpDir.file("someGradleUserHomePath")
        System.setProperty(StartParameter.GRADLE_USER_HOME_PROPERTY_KEY, gradleUserHome.absolutePath)

        when:
        def parameter = new StartParameter()
        then:
        parameter.gradleUserHomeDir == gradleUserHome
    }

    void "canonicalizes current dir"() {
        StartParameter parameter = new StartParameter()
        File dir = new File('current')

        when:
        parameter.currentDir = dir

        then:
        parameter.currentDir == dir.canonicalFile
        assertThat(parameter, isSerializable())
    }

    void "can configure build file"() {
        StartParameter parameter = new StartParameter()
        File file = new File('test/build file')

        when:
        parameter.buildFile = file

        then:
        parameter.buildFile == file.canonicalFile
        parameter.currentDir == file.canonicalFile.parentFile
        assertThat(parameter, isSerializable())
    }

    void "can configure null build file"() {
        StartParameter parameter = new StartParameter()
        parameter.buildFile = new File('test/build file')

        when:
        parameter.buildFile = null

        then:
        parameter.buildFile == null
        parameter.currentDir == new File(System.getProperty("user.dir")).getCanonicalFile()
        parameter.initScripts.empty
        assertThat(parameter, isSerializable())
    }

    void "can configure project dir"() {
        StartParameter parameter = new StartParameter()
        File file = new File('test/project dir')

        when:
        parameter.projectDir = file

        then:
        parameter.currentDir == file.canonicalFile
        assertThat(parameter, isSerializable())
    }

    void "can configure null project dir"() {
        StartParameter parameter = new StartParameter()
        parameter.projectDir = new File('test/project dir')

        when:
        parameter.projectDir = null

        then:
        parameter.currentDir == new File(System.getProperty("user.dir")).getCanonicalFile()
        assertThat(parameter, isSerializable())
    }

    void "can configure settings file"() {
        StartParameter parameter = new StartParameter()
        File file = new File('some dir/settings file')

        when:
        parameter.settingsFile = file

        then:
        parameter.currentDir == file.canonicalFile.parentFile
        parameter.settingsFile == file.canonicalFile
        assertThat(parameter, isSerializable())
    }

    void "can configure null settings file"() {
        StartParameter parameter = new StartParameter()

        when:
        parameter.settingsFile = null

        then:
        parameter.settingsFile == null
        assertThat(parameter, isSerializable())
    }

    void "can configure null user home dir"() {
        StartParameter parameter = new StartParameter()

        when:
        parameter.gradleUserHomeDir = null

        then:
        parameter.gradleUserHomeDir == StartParameter.DEFAULT_GRADLE_USER_HOME
        assertThat(parameter, isSerializable())
    }

    void "considers system properties for null user home dir"() {
        def gradleUserHome = tmpDir.file("someGradleUserHomePath")
        System.setProperty(StartParameter.GRADLE_USER_HOME_PROPERTY_KEY, gradleUserHome.absolutePath)

        given:
        StartParameter parameter = new StartParameter()
        parameter.gradleUserHomeDir = tmpDir.file("ignore-me")

        when:
        parameter.gradleUserHomeDir = null

        then:
        parameter.gradleUserHomeDir == gradleUserHome
        assertThat(parameter, isSerializable())
    }

    void "creates parameter for new build"() {
        StartParameter parameter = new StartParameter()

        // Copied properties
        parameter.gradleUserHomeDir = new File("home")
        parameter.logLevel = LogLevel.DEBUG
        parameter.consoleOutput = ConsoleOutput.Plain
        parameter.configureOnDemand = true
        parameter.systemPropertiesArgs.put("testprop", "foo")

        // Non-copied
        parameter.currentDir = new File("other")
        parameter.buildFile = new File("build file")
        parameter.settingsFile = new File("settings file")
        parameter.taskNames = ['task1']
        parameter.excludedTaskNames = ['excluded1']
        parameter.dryRun = true
        parameter.continueOnFailure = true
        parameter.rerunTasks = true
        parameter.refreshDependencies = true
        parameter.parallelProjectExecutionEnabled = true
        parameter.buildCacheEnabled = true
        parameter.writeDependencyLocks = true
        parameter.lockedDependenciesToUpdate = ['foo']

        assertThat(parameter, isSerializable())

        when:
        StartParameter newParameter = parameter.newBuild()

        then:
        newParameter != parameter

        newParameter.configureOnDemand == parameter.configureOnDemand
        newParameter.gradleUserHomeDir == parameter.gradleUserHomeDir
        newParameter.logLevel == parameter.logLevel
        newParameter.consoleOutput == parameter.consoleOutput
        newParameter.continueOnFailure == parameter.continueOnFailure
        newParameter.refreshDependencies == parameter.refreshDependencies
        newParameter.rerunTasks == parameter.rerunTasks
        newParameter.systemPropertiesArgs == parameter.systemPropertiesArgs
        newParameter.parallelProjectExecutionEnabled == parameter.parallelProjectExecutionEnabled
        newParameter.buildCacheEnabled == parameter.buildCacheEnabled
        newParameter.writeDependencyLocks == parameter.writeDependencyLocks
        newParameter.lockedDependenciesToUpdate == parameter.lockedDependenciesToUpdate

        newParameter.buildFile == null
        assertRunsDefaultTasks(newParameter)
        newParameter.excludedTaskNames.empty
        newParameter.currentDir == new File(System.getProperty("user.dir")).getCanonicalFile()
        !newParameter.dryRun
        assertThat(newParameter, isSerializable())
    }

    void "gets all init scripts"() {
        def gradleUserHomeDir = tmpDir.testDirectory.createDir("gradleUserHomeDie")
        def gradleHomeDir = tmpDir.testDirectory.createDir("gradleHomeDir")
        StartParameter parameter = new StartParameter()

        when:
        parameter.gradleUserHomeDir = gradleUserHomeDir
        parameter.gradleHomeDir = gradleHomeDir

        then:
        parameter.allInitScripts.empty

        when:
        def userMainInit = gradleUserHomeDir.createFile("init.gradle")
        then:
        parameter.allInitScripts == [userMainInit]

        when:
        def userInit1 = gradleUserHomeDir.createFile("init.d/1.gradle")
        def userInit2 = gradleUserHomeDir.createFile("init.d/2.gradle")

        then:
        parameter.allInitScripts == [userMainInit, userInit1, userInit2]

        when:
        def distroInit1 = gradleHomeDir.createFile("init.d/1.gradle")
        def distroInit2 = gradleHomeDir.createFile("init.d/2.gradle")

        then:
        parameter.allInitScripts == [userMainInit, userInit1, userInit2, distroInit1, distroInit2]
    }

    def 'taskNames getter defaults to taskParameters'() {
        def parameter = new StartParameter()
        def requests = [new DefaultTaskExecutionRequest(['a']), new DefaultTaskExecutionRequest(['b'])]

        when:
        parameter.taskRequests = requests

        then:
        parameter.taskNames == [ 'a', 'b' ]
        parameter.taskRequests == requests
    }

    def 'taskNames setter defaults to taskParameters'() {
        StartParameter parameter = new StartParameter()

        when:
        parameter.taskNames = [ 'a', 'b' ]

        then:
        parameter.taskNames == [ 'a', 'b' ]
        parameter.taskRequests == [ new DefaultTaskExecutionRequest(['a', 'b']) ]

        when:
        parameter.taskNames = null

        then:
        assertRunsDefaultTasks(parameter)
    }

    private void assertRunsDefaultTasks(StartParameter parameter) {
        assert parameter.taskNames.empty
        assert parameter.taskRequests.size() == 1 && parameter.taskRequests[0] instanceof RunDefaultTasksExecutionRequest
    }
}
