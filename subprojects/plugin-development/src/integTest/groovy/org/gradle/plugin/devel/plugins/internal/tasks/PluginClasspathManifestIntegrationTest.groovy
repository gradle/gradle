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

package org.gradle.plugin.devel.plugins.internal.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.maven.MavenModule

import static org.gradle.util.TextUtil.normaliseFileAndLineSeparators

class PluginClasspathManifestIntegrationTest extends AbstractIntegrationSpec {

    private static final String TASK_NAME = 'generatePluginClasspathManifest'
    private static final String TASK_PATH = ":$TASK_NAME"

    def setup() {
        buildFile << """
            apply plugin: 'java'
        """
    }

    def "can use default conventions to generate classpath manifest"() {
        given:
        buildFile << """
            task $TASK_NAME(type: ${PluginClasspathManifest.class.getName()})
        """
        MavenModule module = mavenRepo.module('org.gradle.test', 'a', '1.3').publish()
        buildFile << compileDependency('compile', module)

        when:
        ExecutionResult result = succeeds TASK_NAME

        then:
        result.executedTasks.containsAll([':compileJava', ':processResources', TASK_PATH])
        File classpathManifest = file("build/$TASK_NAME/plugin-classpath.txt")
        classpathManifest.exists() && classpathManifest.isFile()
        !classpathManifest.text.contains("\\")
        classpathManifest.text == normaliseFileAndLineSeparators("""${file('build/classes/main').absolutePath}
${file('build/resources/main').absolutePath}
${module.artifactFile.absolutePath}""")
    }

    def "can assign custom plugin classpath to generate classpath manifest"() {
        given:
        buildFile << """
            sourceSets {
                custom {
                    java {
                        srcDir 'src'
                    }
                    resources {
                        srcDir 'resources'
                    }
                }
            }

            task $TASK_NAME(type: ${PluginClasspathManifest.class.getName()}) {
                pluginClasspath = sourceSets.custom.runtimeClasspath
            }
        """
        MavenModule module = mavenRepo.module('org.gradle.test', 'a', '1.3').publish()
        buildFile << compileDependency('customCompile', module)

        when:
        ExecutionResult result = succeeds TASK_NAME

        then:
        result.executedTasks.containsAll([':compileCustomJava', ':processCustomResources', TASK_PATH])
        File classpathManifest = file("build/$TASK_NAME/plugin-classpath.txt")
        classpathManifest.exists() && classpathManifest.isFile()
        !classpathManifest.text.contains("\\")
        classpathManifest.text == normaliseFileAndLineSeparators("""${file('build/classes/custom').absolutePath}
${file('build/resources/custom').absolutePath}
${module.artifactFile.absolutePath}""")
    }

    def "fails the task for null plugin classpath"() {
        given:
        buildFile << """
            task $TASK_NAME(type: ${PluginClasspathManifest.class.getName()}) {
                pluginClasspath = null
            }
        """

        when:
        fails TASK_NAME

        then:
        failure.assertHasCause("No value has been specified for property 'pluginClasspath'.")
    }

    def "fails the task if main source set does not exist"() {
        given:
        buildFile << """
            sourceSets.remove(sourceSets.main)

            task $TASK_NAME(type: ${PluginClasspathManifest.class.getName()})
        """

        when:
        fails TASK_NAME

        then:
        failure.assertHasCause("No value has been specified for property 'pluginClasspath'.")
    }

    private String compileDependency(String configurationName, MavenModule module) {
        """
            repositories {
                maven { url "$mavenRepo.uri" }
            }

            dependencies {
                $configurationName '$module.groupId:$module.artifactId:$module.version'
            }
        """
    }
}
