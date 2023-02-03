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

package org.gradle.jvm

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GeneratedSourcesDirectorySetContributorIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = 'mylib'
        """
        buildFile << """
            plugins {
                id 'jvm-ecosystem'
            }
            def jvm = project.services.get(org.gradle.api.plugins.jvm.internal.JvmEcosystemUtilities)

            group = 'com.acme'
            version = '1.4'

            // We're intentionally restricting the plugin to 'jvm-ecosystem'
            // so neither the main source set (used below) or the "classes" task exist
            sourceSets.create('main')
            tasks.register("classes")

            class SourceGenerator extends DefaultTask {
                @OutputDirectory
                final DirectoryProperty outputDir = project.objects.directoryProperty()

                @TaskAction
                void generateSources() {
                    def outdir = outputDir.get().asFile
                    outdir.mkdirs()
                    new File(outdir, "Hello.java") << "public class Hello {}"
                }
            }
        """
    }

    def "can register a source directory set from a source generating task"() {
        buildFile << """
            def sourceGen = tasks.register("sourceGen", SourceGenerator) {
                outputDir.set(project.layout.buildDirectory.dir("generated-sources"))
            }

            jvm.registerJvmLanguageGeneratedSourceDirectory(sourceSets.main) {
                forSourceGeneratingTask(sourceGen) { it.outputDir }
                compiledWithJava {
                    sourceCompatibility = '8'
                    targetCompatibility = '8'
                    classpath = files()
                }
            }
        """

        when:
        succeeds 'classes'

        then:
        executedAndNotSkipped ':sourceGen', ':compileSourceGen', ':classes'
    }

    def "reasonable error message if user didn't say how to compile the generated sources"() {
        buildFile << """
            def sourceGen = tasks.register("sourceGen", SourceGenerator) {
                outputDir.set(project.layout.buildDirectory.dir("generated-sources"))
            }

            jvm.registerJvmLanguageGeneratedSourceDirectory(sourceSets.main) {
                forSourceGeneratingTask(sourceGen) { it.outputDir }
            }
        """

        when:
        fails 'classes'

        then:
        failure.assertHasCause "You must specify how sources will be compiled either by calling compiledWithJava or compiledBy"
    }

    def "can register a source directory set from a source generating task compiled by a custom task"() {
        buildFile << """
            def sourceGen = tasks.register("sourceGen", SourceGenerator) {
                outputDir.set(project.layout.buildDirectory.dir("generated-sources"))
            }

            jvm.registerJvmLanguageGeneratedSourceDirectory(sourceSets.main) {
                forSourceGeneratingTask(sourceGen) { it.outputDir }
                compiledBy { sourceDirectory ->
                    tasks.register("compileSourceGen") {
                        inputs.files(sourceDirectory)
                        doLast {
                            println "Compiling \${sourceDirectory.get().asFile.name}"
                        }
                    }
                }
            }
        """

        when:
        succeeds 'classes'

        then:
        executedAndNotSkipped ':sourceGen', ':compileSourceGen', ':classes'
        outputContains 'Compiling generated-sources'
    }

}
