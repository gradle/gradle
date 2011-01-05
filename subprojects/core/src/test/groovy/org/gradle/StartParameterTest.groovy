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

import static org.gradle.util.Matchers.*

import org.gradle.api.internal.artifacts.ProjectDependenciesBuildInstruction
import org.gradle.api.logging.LogLevel
import org.gradle.execution.BuildExecuter
import org.gradle.execution.DefaultBuildExecuter
import org.gradle.execution.DryRunBuildExecuter
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.groovy.scripts.UriScriptSource
import org.gradle.initialization.BuildFileProjectSpec
import org.gradle.initialization.DefaultProjectSpec
import org.gradle.initialization.ProjectDirectoryProjectSpec
import org.gradle.initialization.ProjectSpec
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import org.junit.Test
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.util.SetSystemProperties

/**
 * @author Hans Dockter
 */
class StartParameterTest {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();
    @Rule
    public SetSystemProperties systemProperties = new SetSystemProperties()

    @Test public void testNewInstance() {
        StartParameter testObj = new StartParameter()
        testObj.settingsFile = 'settingsfile' as File
        testObj.buildFile = 'buildfile' as File
        testObj.taskNames = ['a']
        testObj.projectDependenciesBuildInstruction = new ProjectDependenciesBuildInstruction([])
        testObj.currentDir = new File('a')
        testObj.searchUpwards = false
        testObj.projectProperties = [a: 'a']
        testObj.systemPropertiesArgs = [b: 'b']
        testObj.gradleUserHomeDir = new File('b')
        testObj.initScripts = [new File('init script'), new File("/path/to/another init script")]
        testObj.cacheUsage = CacheUsage.ON
        testObj.logLevel = LogLevel.WARN
        testObj.colorOutput = false

        StartParameter startParameter = testObj.newInstance()
        assertEquals(testObj, startParameter)
    }

    @Test public void testDefaultValues() {
        StartParameter parameter = new StartParameter();
        assertThat(parameter.gradleUserHomeDir, equalTo(StartParameter.DEFAULT_GRADLE_USER_HOME))
        assertThat(parameter.currentDir, equalTo(new File(System.getProperty("user.dir")).getCanonicalFile()))

        assertThat(parameter.buildFile, nullValue())
        assertThat(parameter.settingsScriptSource, nullValue())

        assertThat(parameter.logLevel, equalTo(LogLevel.LIFECYCLE))
        assertTrue(parameter.colorOutput)
        assertThat(parameter.taskNames, isEmpty())
        assertThat(parameter.excludedTaskNames, isEmpty())
        assertThat(parameter.projectProperties, isEmptyMap())
        assertThat(parameter.systemPropertiesArgs, isEmptyMap())
        assertThat(parameter.buildExecuter, instanceOf(DefaultBuildExecuter))
        assertThat(parameter.defaultProjectSelector, reflectionEquals(new DefaultProjectSpec(parameter.currentDir)))
        assertFalse(parameter.dryRun)
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
        assertThat(parameter.defaultProjectSelector, reflectionEquals(new DefaultProjectSpec(dir.canonicalFile)))
    }

    @Test public void testSetBuildFile() {
        StartParameter parameter = new StartParameter()
        File file = new File('test/build file')
        parameter.buildFile = file

        assertThat(parameter.buildFile, equalTo(file.canonicalFile))
        assertThat(parameter.currentDir, equalTo(file.canonicalFile.parentFile))
        assertThat(parameter.defaultProjectSelector, reflectionEquals(new BuildFileProjectSpec(file.canonicalFile)))
    }

    @Test public void testSetNullBuildFile() {
        StartParameter parameter = new StartParameter()
        parameter.buildFile = new File('test/build file')
        parameter.buildFile = null

        assertThat(parameter.buildFile, nullValue())
        assertThat(parameter.currentDir, equalTo(new File(System.getProperty("user.dir")).getCanonicalFile()))
        assertThat(parameter.defaultProjectSelector, reflectionEquals(new DefaultProjectSpec(parameter.currentDir)))
        assertThat(parameter.initScripts, equalTo(Collections.emptyList()))
    }

    @Test public void testSetProjectDir() {
        StartParameter parameter = new StartParameter()
        File file = new File('test/project dir')
        parameter.projectDir = file

        assertThat(parameter.currentDir, equalTo(file.canonicalFile))
        assertThat(parameter.defaultProjectSelector, reflectionEquals(new ProjectDirectoryProjectSpec(file.canonicalFile)))
    }

    @Test public void testSetNullProjectDir() {
        StartParameter parameter = new StartParameter()
        parameter.projectDir = new File('test/project dir')
        parameter.projectDir = null

        assertThat(parameter.currentDir, equalTo(new File(System.getProperty("user.dir")).getCanonicalFile()))
        assertThat(parameter.defaultProjectSelector, reflectionEquals(new DefaultProjectSpec(parameter.currentDir)))
    }

    @Test public void testSetSettingsFile() {
        StartParameter parameter = new StartParameter()
        File file = new File('some dir/settings file')
        parameter.settingsFile = file

        assertThat(parameter.currentDir, equalTo(file.canonicalFile.parentFile))
        assertThat(parameter.settingsScriptSource, instanceOf(UriScriptSource.class))
        assertThat(parameter.settingsScriptSource.resource.file, equalTo(file.canonicalFile))
    }

    @Test public void testSetNullSettingsFile() {
        StartParameter parameter = new StartParameter()
        parameter.settingsFile = null

        assertThat(parameter.settingsScriptSource, nullValue())
    }

    @Test public void testSetSettingsScriptSource() {
        StartParameter parameter = new StartParameter()
        parameter.settingsFile = new File('settings file')

        ScriptSource scriptSource = {} as ScriptSource

        parameter.settingsScriptSource = scriptSource

        assertThat(parameter.settingsScriptSource, sameInstance(scriptSource))
    }

    @Test public void testSetTaskNames() {
        StartParameter parameter = new StartParameter()
        parameter.taskNames = ['a', 'b']
        assertThat(parameter.buildExecuter, instanceOf(DefaultBuildExecuter))
        assertThat(parameter.buildExecuter.delegate.names, equalTo(['a', 'b']))
    }

    @Test public void testSetTaskNamesUsesDefaultExecuter() {
        StartParameter parameter = new StartParameter()

        parameter.setBuildExecuter({} as BuildExecuter)
        parameter.taskNames = []
        assertThat(parameter.buildExecuter, instanceOf(DefaultBuildExecuter))
    }

    @Test public void testSetExcludedTaskNames() {
        StartParameter parameter = new StartParameter()
        parameter.excludedTaskNames = ['a', 'b']
        assertThat(parameter.buildExecuter, instanceOf(DefaultBuildExecuter))
        assertThat(parameter.buildExecuter.excludedTaskNames, equalTo(['a', 'b'] as Set))
    }

    @Test public void testUseEmbeddedBuildFile() {
        StartParameter parameter = new StartParameter();
        parameter.useEmbeddedBuildFile("<content>")
        assertThat(parameter.buildScriptSource, instanceOf(StringScriptSource.class))
        assertThat(parameter.buildScriptSource.resource.text, equalTo("<content>"))
        assertThat(parameter.settingsScriptSource, instanceOf(StringScriptSource.class))
        assertThat(parameter.settingsScriptSource.resource.text, equalTo(""))
        assertThat(parameter.searchUpwards, equalTo(false))
    }

    @Test public void testSetNullUserHomeDir() {
        StartParameter parameter = new StartParameter()
        parameter.gradleUserHomeDir = null
        assertThat(parameter.gradleUserHomeDir, equalTo(StartParameter.DEFAULT_GRADLE_USER_HOME))
    }

    @Test public void testWrapsExecuterWhenDryRunIsTrue() {
        StartParameter parameter = new StartParameter()
        def originalExecuter = [:] as BuildExecuter
        parameter.buildExecuter = originalExecuter
        parameter.dryRun = true
        assertThat(parameter.buildExecuter, instanceOf(DryRunBuildExecuter))
        assertThat(parameter.buildExecuter.delegate, sameInstance(originalExecuter))
        parameter.dryRun = false
        assertThat(parameter.buildExecuter, sameInstance(originalExecuter))
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
        parameter.defaultProjectSelector = [:] as ProjectSpec
        parameter.dryRun = true

        StartParameter newParameter = parameter.newBuild();

        assertThat(newParameter, not(equalTo(parameter)));

        assertThat(newParameter.gradleUserHomeDir, equalTo(parameter.gradleUserHomeDir));
        assertThat(newParameter.cacheUsage, equalTo(parameter.cacheUsage));
        assertThat(newParameter.logLevel, equalTo(parameter.logLevel));
        assertThat(newParameter.colorOutput, equalTo(parameter.colorOutput));

        assertThat(newParameter.buildFile, nullValue())
        assertThat(newParameter.taskNames, isEmpty())
        assertThat(newParameter.excludedTaskNames, isEmpty())
        assertThat(newParameter.currentDir, equalTo(new File(System.getProperty("user.dir")).getCanonicalFile()))
        assertThat(newParameter.defaultProjectSelector, reflectionEquals(new DefaultProjectSpec(newParameter.currentDir)))
        assertFalse(newParameter.dryRun)
    }
}
