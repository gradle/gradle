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
import org.gradle.api.execution.TaskExecutionAdapter
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ArtifactBuilder
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenModule
import spock.lang.IgnoreRest
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

    def 'init script can apply custom plugin'() {
        executer.requireOwnGradleUserHomeDir()
        new TestFile(executer.gradleUserHomeDir, "init.gradle") << "gradle.pluginRepositories { maven { it.url '${getMavenRepo().getRootDir().absolutePath}'} }"

        buildPluginJar()

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

    private void buildPluginJar() {
        ArtifactBuilder builder = artifactBuilder()
        builder.sourceFile('CustomPlugin.java') << """
import org.gradle.api.*;
import org.gradle.api.initialization.*;
public class CustomPlugin implements Plugin<Project> {
    public void apply(Project t) {
        
    }
}
"""
        builder.resourceFile('META-INF/gradle-plugins/custom.properties') << '''
implementation-class=CustomPlugin
'''
        def module = mavenRepo.module("custom", "custom.gradle.plugin").publish()
        module.artifactFile.delete()
        builder.buildJar(module.artifactFile)
    }

    private String initScript() {
        """
            gradle.addListener(new TaskExecutionAdapter() {
                public void afterExecute(Task task, TaskState state) {
                    println "Task \${task.name} executed"
                }
            })
        """
    }
}
