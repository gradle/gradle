/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.CacheUsage
import org.gradle.api.Project
import org.gradle.execution.TaskExecuter
import org.gradle.execution.NameResolvingTaskExecuter
import org.gradle.execution.ProjectDefaultsTaskExecuter
import org.gradle.groovy.scripts.StringScriptSource
import static org.gradle.util.ReflectionEqualsMatcher.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.gradle.api.initialization.Settings

/**
 * @author Hans Dockter
 */
class StartParameterTest {
    StartParameter testObj

    @Before public void setUp() {
        testObj = new StartParameter(
                settingsFileName: 'settingsfile',
                buildFileName: 'buildfile',
                taskNames: ['a'],
                currentDir: new File('a'),
                searchUpwards: true,
                projectProperties: [a: 'a'],
                systemPropertiesArgs: [b: 'b'],
                gradleUserHomeDir: new File('b'),
                defaultImportsFile: new File('imports'),
                pluginPropertiesFile: new File('plugin'),
                cacheUsage: CacheUsage.ON
        )
    }


    @Test public void testNewInstance() {
        StartParameter startParameter = StartParameter.newInstance(testObj)
        assert startParameter.equals(testObj)
    }

    @Test public void testDefaultValues() {
        StartParameter parameter = new StartParameter();
        assertThat(parameter.buildFileName, equalTo(Project.DEFAULT_BUILD_FILE))
        assertThat(parameter.settingsFileName, equalTo(Settings.DEFAULT_SETTINGS_FILE))
        assertThat(parameter.taskNames, notNullValue())
        assertThat(parameter.projectProperties, notNullValue())
        assertThat(parameter.systemPropertiesArgs, notNullValue())
        assertThat(parameter.taskExecuter, instanceOf(ProjectDefaultsTaskExecuter))
    }

    @Test public void testSetTaskNames() {
        StartParameter parameter = new StartParameter()
        parameter.setTaskNames(Arrays.asList("a", "b"))
        assertThat(parameter.taskExecuter, reflectionEquals(new NameResolvingTaskExecuter(Arrays.asList("a", "b"))))
    }

    @Test public void testSetTaskNamesToEmptyOrNullListUsesProjectDefaultTasks() {
        StartParameter parameter = new StartParameter()

        parameter.setTaskExecuter({} as TaskExecuter)
        parameter.setTaskNames(Collections.emptyList())
        assertThat(parameter.taskExecuter, instanceOf(ProjectDefaultsTaskExecuter))

        parameter.setTaskExecuter({} as TaskExecuter)
        parameter.setTaskNames(null)
        assertThat(parameter.taskExecuter, instanceOf(ProjectDefaultsTaskExecuter))
    }

    @Test public void testUseEmbeddedBuildFile() {
        StartParameter parameter = new StartParameter();
        parameter.useEmbeddedBuildFile("<content>")
        assertThat(parameter.buildScriptSource, reflectionEquals(new StringScriptSource("embedded build file", "<content>")))
        assertThat(parameter.buildFileName, equalTo(Project.EMBEDDED_SCRIPT_ID))
        assertThat(parameter.settingsScriptSource, reflectionEquals(new StringScriptSource("empty settings file", "")))
        assertThat(parameter.searchUpwards, equalTo(false))
    }
    
    @Test public void testNewBuild() {
        StartParameter parameter = new StartParameter();

        // Copied properties
        parameter.setGradleUserHomeDir(new File("home"));
        parameter.setCacheUsage(CacheUsage.OFF);
        parameter.setPluginPropertiesFile(new File("plugins"));
        parameter.setDefaultImportsFile(new File("imports"));

        // Non-copied
        parameter.setBuildFileName("b");
        parameter.getTaskNames().add("t1");

        StartParameter newParameter = parameter.newBuild();

        assertThat(newParameter, not(equalTo(parameter)));

        assertThat(newParameter.getGradleUserHomeDir(), equalTo(parameter.getGradleUserHomeDir()));
        assertThat(newParameter.getCacheUsage(), equalTo(parameter.getCacheUsage()));
        assertThat(newParameter.getPluginPropertiesFile(), equalTo(parameter.getPluginPropertiesFile()));
        assertThat(newParameter.getDefaultImportsFile(), equalTo(parameter.getDefaultImportsFile()));
    }
}
