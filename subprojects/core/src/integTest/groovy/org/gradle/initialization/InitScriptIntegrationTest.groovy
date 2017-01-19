/*
 * Copyright 2016 the original author or authors.
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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.plugin.PluginBuilder
import spock.lang.Issue

@LeaksFileHandles
class InitScriptIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildScript """
            task hello() {
                doLast {
                    println "Hello from main project"
                }
            }
        """

        file("buildSrc/build.gradle") << """
            task helloFromBuildSrc {
                doLast {
                    println "Hello from buildSrc"
                }
            }

            build.dependsOn(helloFromBuildSrc)
        """
    }

    @NotYetImplemented
    @Issue(['GRADLE-1457', 'GRADLE-3197'])
    def 'init scripts passed on the command line are applied to buildSrc'() {
        given:
        file("init.gradle") << initScript()

        executer.usingInitScript(file('init.gradle'))

        when:
        succeeds 'hello'

        then:
        output.contains("Task hello executed")
        output.contains("Task helloFromBuildSrc executed")
    }

    def 'init scripts passed in the Gradle user home are applied to buildSrc'() {
        given:
        executer.requireOwnGradleUserHomeDir()
        new TestFile(executer.gradleUserHomeDir, "init.gradle") << initScript()

        when:
        succeeds 'hello'

        then:
        output.contains("Task hello executed")
        output.contains("Task helloFromBuildSrc executed")
    }

    def 'init script can specify plugin repositories'() {
        executer.requireOwnGradleUserHomeDir()
        new TestFile(executer.gradleUserHomeDir, "init.gradle") << "gradle.pluginRepositories { maven { it.url '${getMavenRepo().getRootDir().absolutePath}'} }"

        def pluginBuilder = new PluginBuilder(new TestFile(executer.testDirectoryProvider.testDirectory, 'plugin-repo'))
        pluginBuilder.addPlugin("", 'custom')
        pluginBuilder.publishAs("custom:custom.gradle.plugin:1.0", mavenRepo, executer)

        buildScript """
        plugins { 
            id 'custom' version '1.0'
        }
        """

        when:
        succeeds 'help'

        then:
        noExceptionThrown()
    }

    def 'when plugins come from multiple repos, it will pick the first'() {
        executer.requireOwnGradleUserHomeDir()
        new TestFile(executer.gradleUserHomeDir, "init.gradle") << "gradle.pluginRepositories { maven { it.url '${getMavenRepo().getRootDir().absolutePath}'} }"
        new TestFile(executer.testDirectoryProvider.testDirectory, "settings.gradle") << "pluginRepositories { ivy { it.url '${getIvyRepo().getRootDir().absolutePath}'} }"

        def pluginBuilder1 = new PluginBuilder(new TestFile(executer.testDirectoryProvider.testDirectory, 'plugin1-repo'))
        pluginBuilder1.addPlugin("", 'custom')
        pluginBuilder1.publishAs("custom:custom.gradle.plugin:1.0", mavenRepo, executer)

        def pluginBuilder2 = new PluginBuilder(new TestFile(executer.testDirectoryProvider.testDirectory, 'plugin2-repo'))
        pluginBuilder2.addNonConstructablePlugin('custom', 'TestPlugin1')
        pluginBuilder2.publishAs("custom:custom.gradle.plugin:1.0", ivyRepo, executer)

        def pluginBuilder3 = new PluginBuilder(new TestFile(executer.testDirectoryProvider.testDirectory, 'plugin3-repo'))
        pluginBuilder3.addPlugin('', 'custom2', 'TestPlugin2')
        pluginBuilder3.publishAs("custom2:custom2.gradle.plugin:1.0", ivyRepo, executer)

        buildScript """
        plugins { 
            id 'custom' version '1.0'
            id 'custom2' version '1.0'
        }
        """

        when:
        succeeds 'help'

        then:
        noExceptionThrown()
    }

    private static String initScript() {
        """
            gradle.addListener(new TaskExecutionAdapter() {
                public void afterExecute(Task task, TaskState state) {
                    println "Task \${task.name} executed"
                }
            })
        """
    }
}
