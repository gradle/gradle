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
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

class JavaExecMainClassIntegrationTest extends AbstractIntegrationSpec {

    @Requires(IntegTestPreconditions.NotConfigCached)
    def "can add JavaExec mainClass convention to automatically find class at execution time"() {
        given:
        def configurationCache = new ConfigurationCacheFixture(this)
        buildFile << """

            plugins {
                id 'java'
            }

            interface BootExtension {
                Property<String> getMainClassName()
            }

            abstract class ResolveMainClassName extends DefaultTask {

                @Classpath
                @InputFiles
                abstract ConfigurableFileCollection getClasspath()

                @Internal
                abstract Property<String> getMainClassFromBootExtension()

                @Internal
                abstract Property<String> getMainClassFromJavaApplication()

                @OutputFile
                abstract RegularFileProperty getMainClassFile()

                @TaskAction
                def computeMainClass() {
                    mainClassFile.get().asFile.text = mainClassFromBootExtension.orNull
                        ?: mainClassFromJavaApplication.orNull
                        ?: resolveMainClassName()
                }

                def resolveMainClassName() {
                    // Find first root package class with a name that ends with `Main`
                    def rootPackageFiles = classpath.files.collectMany { it.isDirectory() ? it.listFiles().toList() : [] }
                    def mainClassFile = rootPackageFiles.find { it.name.endsWith 'Main.class' }
                    mainClassFile.name.with {
                        take(length() - '.class'.length())
                    }
                }
            }

            def resolveMainClassName = tasks.register('resolveMainClassName', ResolveMainClassName) {
                classpath.from(compileJava)
                mainClassFromBootExtension.set(
                    project.extensions.findByType(BootExtension.class)?.mainClassName
                )
                mainClassFromJavaApplication.set(
                    project.extensions.findByType(JavaApplication.class)?.mainClass
                )
                mainClassFile = layout.buildDirectory.file('mainClass.txt')
            }

            tasks.register('run', JavaExec) {
                classpath = layout.files(compileJava)
                mainClass.set(
                    resolveMainClassName.flatMap { it.mainClassFile }.map { it.asFile.text }
                )
            }
        """
        def originalMain = writeMainClass 'Main', 'it works!'

        when:
        succeeds 'run', '--configuration-cache'

        then:
        outputContains 'it works!'
        configurationCache.assertStateStored()

        when:
        originalMain.delete()
        writeMainClass 'AppMain', 'it certainly does!'
        succeeds 'run', '--configuration-cache'

        then:
        outputContains 'it certainly does!'
        configurationCache.assertStateLoaded()
    }

    private TestFile writeMainClass(className, String message) {
        file("src/main/java/${className}.java") << """
            class $className { public static void main(String[] args) {
                System.out.println("$message");
            } }
        """
    }
}
