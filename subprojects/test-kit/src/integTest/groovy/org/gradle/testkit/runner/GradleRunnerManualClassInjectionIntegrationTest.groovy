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

package org.gradle.testkit.runner

import org.gradle.integtests.fixtures.executer.ForkingGradleExecuter
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.TestFile

class GradleRunnerManualClassInjectionIntegrationTest extends GradleRunnerIntegrationTest {

    def "can manually inject classes under test"() {
        given:
        compilePluginProjectSources()
        buildFile << """
            buildscript {
                dependencies {
                    classpath files(${pluginClasspath.collect { "'${it.absolutePath.replace("\\", "\\\\")}'" }.join(", ")})
                }
            }

            apply plugin: 'com.company.helloworld'
        """

        when:
        runner('helloWorld').build()

        then:
        file("out.txt").text == "out"
    }

    private void compilePluginProjectSources() {
        createPluginProjectSourceFiles()
        new ForkingGradleExecuter(new UnderDevelopmentGradleDistribution(), testDirectoryProvider)
            .usingProjectDirectory(file("plugin"))
            .withArguments('classes', "--no-daemon")
            .run()
    }

    private void createPluginProjectSourceFiles() {
        pluginProjectFile("src/main/groovy/org/gradle/test/HelloWorldPlugin.groovy") << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class HelloWorldPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.task('helloWorld') << {
                        project.file("out.txt").with {
                            createNewFile()
                            text = "out"
                        }
                    }
                }
            }
        """

        pluginProjectFile("src/main/resources/META-INF/gradle-plugins/com.company.helloworld.properties") << """
            implementation-class=org.gradle.test.HelloWorldPlugin
        """

        pluginProjectFile('build.gradle') << """
            apply plugin: 'groovy'

            dependencies {
                compile gradleApi()
                compile localGroovy()
            }
        """
    }

    private TestFile pluginProjectFile(String path) {
        file("plugin").file(path)
    }

    private List<File> getPluginClasspath() {
        [pluginProjectFile("build/classes/main"), pluginProjectFile('build/resources/main')]
    }


}
