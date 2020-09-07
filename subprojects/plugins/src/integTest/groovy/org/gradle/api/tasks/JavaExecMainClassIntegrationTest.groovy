/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

class JavaExecMainClassIntegrationTest extends AbstractIntegrationSpec {

    def "can add JavaExec mainClass convention to automatically find class at execution time"() {
        given:
        def configurationCache = GradleContextualExecuter.configCache ? newConfigurationCacheFixture() : null
        buildFile << """

            plugins {
                id 'java'
            }

            abstract class ComputeMain extends DefaultTask {

                @Classpath
                @InputFiles
                abstract ConfigurableFileCollection getClasspath()

                @OutputFile
                abstract RegularFileProperty getMainClassFile()

                @TaskAction
                def computeMainClass() {
                    // automagically discover main class name from classpath here
                    mainClassFile.get().asFile.text = 'Main'
                }
            }

            def computeMainClass = tasks.register('computeMainClass', ComputeMain) {
                classpath.from(compileJava)
                mainClassFile = layout.buildDirectory.file('mainClass.txt')
            }

            tasks.register('run', JavaExec) {
                classpath = layout.files(compileJava)
                mainClass.convention(
                    computeMainClass.flatMap { it.mainClassFile }.map { it.asFile.text }
                )
            }
        """
        file("src/main/java/Main.java") << """
            class Main { public static void main(String[] args) {
                System.out.println("it works!");
            } }
        """

        when:
        succeeds 'run'

        then:
        outputContains 'it works!'
        configurationCache?.assertStateStored() ?: true

        when:
        succeeds 'run'

        then:
        outputContains 'it works!'
        configurationCache?.assertStateLoaded() ?: true
    }
}
