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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import org.junit.Test

import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class StartParameterTest {
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    @Rule
    public SetSystemProperties systemProperties = new SetSystemProperties()

    @Test public void testNewInstance() {
        StartParameter testObj = new StartParameter()
        testObj.settingsFile = 'settingsfile' as File
        testObj.buildFile = 'buildfile' as File
        testObj.taskNames = ['a']
        testObj.buildProjectDependencies = true
        testObj.currentDir = new File('a')
        testObj.searchUpwards = false
        testObj.projectProperties = [a: 'a']
        testObj.systemPropertiesArgs = [b: 'b']
        testObj.gradleUserHomeDir = new File('b')
        testObj.initScripts = [new File('init script'), new File("/path/to/another init script")]
        testObj.cacheUsage = CacheUsage.ON
        testObj.logLevel = LogLevel.WARN
        testObj.colorOutput = false
        testObj.continueOnFailure = true
        testObj.rerunTasks = true;
        testObj.refreshDependencies = true;
        testObj.recompileScripts = true;

        StartParameter startParameter = testObj.newInstance()
        assertEquals(testObj, startParameter)
    }

    @Test public void testDefaultValues() {
        StartParameter parameter = new StartParameter();
        assertThat(parameter.gradleUserHomeDir, equalTo(StartParameter.DEFAULT_GRADLE_USER_HOME))
        assertThat(parameter.currentDir, equalTo(new File(System.getProperty("user.dir")).getCanonicalFile()))

        assertThat(parameter.buildFile, nullValue())
        assertThat(parameter.settingsFile, nullValue())

        assertThat(parameter.logLevel, equalTo(LogLevel.LIFECYCLE))
        assertTrue(parameter.colorOutput)
        assertThat(parameter.taskNames, isEmpty())
        assertThat(parameter.excludedTaskNames, isEmpty())
        assertThat(parameter.projectProperties, isEmptyMap())
        assertThat(parameter.systemPropertiesArgs, isEmptyMap())
        assertFalse(parameter.dryRun)
        assertFalse(parameter.continueOnFailure)
        assertThat(parameter.refreshOptions, equalTo(RefreshOptions.NONE))
        assertThat(parameter.rerunTasks, equalTo(false))
        assertThat(parameter.recompileScripts, equalTo(false))
        assertFalse(parameter.refreshDependencies)
        assertThat(parameter, isSerializable())
    }

    @Test public void testDefaultWithGradleUserHomeSystemProp() {
        File gradleUserHome = tmpDir.file("someGradleUserHomePath")
        System.setProperty(StartParameter.GRADLE_USER_HOME_PROPERTY_KEY, gradleUserHome.absolutePath)
        StartParameter parameter = new StartParameter();
        assertThat(parameter.gradleUserHomeDir, equalTo(gradleUserHome))
    }

    @Test public void testSetCurrentDir() {
        StartParameter parameter = new StartParameter()
        File dir = new File('current')
        parameter.currentDir = dir

        assertThat(parameter.currentDir, equalTo(dir.canonicalFile))
        assertThat(parameter, isSerializable())
    }

    @Test public void testSetBuildFile() {
        StartParameter parameter = new StartParameter()
        File file = new File('test/build file')
        parameter.buildFile = file

        assertThat(parameter.buildFile, equalTo(file.canonicalFile))
        assertThat(parameter.currentDir, equalTo(file.canonicalFile.parentFile))
        assertThat(parameter, isSerializable())
    }

    @Test public void testSetNullBuildFile() {
        StartParameter parameter = new StartParameter()
        parameter.buildFile = new File('test/build file')
        parameter.buildFile = null

        assertThat(parameter.buildFile, nullValue())
        assertThat(parameter.currentDir, equalTo(new File(System.getProperty("user.dir")).getCanonicalFile()))
        assertThat(parameter.initScripts, equalTo(Collections.emptyList()))
        assertThat(parameter, isSerializable())
    }

    @Test public void testSetProjectDir() {
        StartParameter parameter = new StartParameter()
        File file = new File('test/project dir')
        parameter.projectDir = file

        assertThat(parameter.currentDir, equalTo(file.canonicalFile))
        assertThat(parameter, isSerializable())
    }

    @Test public void testSetNullProjectDir() {
        StartParameter parameter = new StartParameter()
        parameter.projectDir = new File('test/project dir')
        parameter.projectDir = null

        assertThat(parameter.currentDir, equalTo(new File(System.getProperty("user.dir")).getCanonicalFile()))
        assertThat(parameter, isSerializable())
    }

    @Test public void testSetSettingsFile() {
        StartParameter parameter = new StartParameter()
        File file = new File('some dir/settings file')
        parameter.settingsFile = file

        assertThat(parameter.currentDir, equalTo(file.canonicalFile.parentFile))
        assertThat(parameter.settingsFile, equalTo(file.canonicalFile))
        assertThat(parameter, isSerializable())
    }

    @Test public void testSetNullSettingsFile() {
        StartParameter parameter = new StartParameter()
        parameter.settingsFile = null

        assertThat(parameter.settingsFile, nullValue())
        assertThat(parameter, isSerializable())
    }

    @Test public void testUseEmptySettingsScript() {
        StartParameter parameter = new StartParameter();
        parameter.useEmptySettings()
        assertThat(parameter.settingsFile, nullValue())
        assertThat(parameter.searchUpwards, equalTo(false))
        assertThat(parameter, isSerializable())
    }

    @Test public void testSetNullUserHomeDir() {
        StartParameter parameter = new StartParameter()
        parameter.gradleUserHomeDir = null
        assertThat(parameter.gradleUserHomeDir, equalTo(StartParameter.DEFAULT_GRADLE_USER_HOME))
        assertThat(parameter, isSerializable())
    }

    @Test public void testNewBuild() {
        StartParameter parameter = new StartParameter()

        // Copied properties
        parameter.gradleUserHomeDir = new File("home")
        parameter.cacheUsage = CacheUsage.REBUILD
        parameter.logLevel = LogLevel.DEBUG
        parameter.colorOutput = false

        // Non-copied
        parameter.currentDir = new File("other")
        parameter.buildFile = new File("build file")
        parameter.settingsFile = new File("settings file")
        parameter.taskNames = ['task1']
        parameter.excludedTaskNames = ['excluded1']
        parameter.dryRun = true
        parameter.continueOnFailure = true
        parameter.recompileScripts = true
        parameter.rerunTasks = true
        parameter.refreshDependencies = true

        assertThat(parameter, isSerializable())

        StartParameter newParameter = parameter.newBuild();

        assertThat(newParameter, not(equalTo(parameter)));

        assertThat(newParameter.gradleUserHomeDir, equalTo(parameter.gradleUserHomeDir));
        assertThat(newParameter.cacheUsage, equalTo(parameter.cacheUsage));
        assertThat(newParameter.logLevel, equalTo(parameter.logLevel));
        assertThat(newParameter.colorOutput, equalTo(parameter.colorOutput));
        assertThat(newParameter.continueOnFailure, equalTo(parameter.continueOnFailure))
        assertThat(newParameter.refreshDependencies, equalTo(parameter.refreshDependencies))
        assertThat(newParameter.rerunTasks, equalTo(parameter.rerunTasks))
        assertThat(newParameter.recompileScripts, equalTo(parameter.recompileScripts))

        assertThat(newParameter.buildFile, nullValue())
        assertThat(newParameter.taskNames, isEmpty())
        assertThat(newParameter.excludedTaskNames, isEmpty())
        assertThat(newParameter.currentDir, equalTo(new File(System.getProperty("user.dir")).getCanonicalFile()))
        assertFalse(newParameter.dryRun)
        assertThat(newParameter, isSerializable())
    }
    
    void testMergingSystemProperties() {
        def p = { args = null ->
            def sp = new StartParameter()
            if (args) {
                sp.systemPropertyArgs = args
            }
            sp.mergedSystemProperties.sort()
        }
        
        System.properties.clear()
        System.properties.a = "1"
    
        assert p(b: "2") == [a: "1", b: "2"]
        assert p(a: "2", b: "3") == [a: "2", b: "3"]
        assert p() == [a: "1"]
    }
}
