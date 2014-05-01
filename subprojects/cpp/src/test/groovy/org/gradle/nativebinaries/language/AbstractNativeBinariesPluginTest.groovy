/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.language

import org.apache.commons.lang.StringUtils
import org.gradle.api.Plugin
import org.gradle.api.Task
import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.nativebinaries.NativeExecutableBinary
import org.gradle.nativebinaries.NativeBinary
import org.gradle.util.GFileUtils
import org.gradle.util.TestUtil
import spock.lang.Specification

abstract class AbstractNativeBinariesPluginTest extends Specification {
    final def project = TestUtil.createRootProject()

    abstract Class<? extends Plugin> getPluginClass();
    abstract Class<? extends LanguageSourceSet> getSourceSetClass();
    abstract Class<? extends Task> getCompileTaskClass();
    abstract String getPluginName();

    def "creates source set with conventional locations for components"() {
        when:
        dsl {
            apply plugin: pluginClass
            executables {
                exe {}
            }
            libraries {
                lib {}
            }
        }

        then:
        def sourceSets = project.sources
        sourceSets.size() == 2
        sourceSets*.name == ["exe", "lib"]

        and:
        sourceSets.exe instanceof FunctionalSourceSet
        sourceSetClass.isInstance(sourceSets.exe."$pluginName")
        sourceSets.exe."$pluginName".source.srcDirs == [project.file("src/exe/$pluginName")] as Set
        sourceSets.exe."$pluginName".exportedHeaders.srcDirs == [project.file("src/exe/headers")] as Set
        project.executables.exe.source == [sourceSets.exe."$pluginName"] as Set

        and:
        sourceSets.lib instanceof FunctionalSourceSet
        sourceSetClass.isInstance(sourceSets.lib."$pluginName")
        sourceSets.lib."$pluginName".source.srcDirs == [project.file("src/lib/$pluginName")] as Set
        sourceSets.lib."$pluginName".exportedHeaders.srcDirs == [project.file("src/lib/headers")] as Set
        project.libraries.lib.source == [sourceSets.lib."$pluginName"] as Set
    }

    def "can configure source set locations"() {
        given:
        dsl {
            apply plugin: pluginClass
            sources {
                exe {
                    "$pluginName" {
                        source {
                            srcDirs "d1", "d2"
                        }
                        exportedHeaders {
                            srcDirs "h1", "h2"
                        }
                    }
                }
                lib {
                    "$pluginName" {
                        source {
                            srcDirs "d3"
                        }
                        exportedHeaders {
                            srcDirs "h3"
                        }
                    }
                }
            }
        }

        expect:
        def sourceSets = project.sources
        with (sourceSets.exe."$pluginName") {
            source.srcDirs*.name == ["d1", "d2"]
            exportedHeaders.srcDirs*.name == ["h1", "h2"]
        }

        with (sourceSets.lib."$pluginName") {
            source.srcDirs*.name == ["d3"]
            exportedHeaders.srcDirs*.name == ["h3"]
        }
    }

    def "creates compile tasks for each non-empty executable source set"() {
        when:
        touch("src/test/$pluginName/file.o")
        touch("src/test/anotherOne/file.o")
        dsl {
            apply plugin: pluginClass
            sources {
                test {
                    anotherOne(sourceSetClass) {}
                    emptyOne(sourceSetClass) {}
                }
            }
            executables {
                test {
                    binaries.all { NativeBinary binary ->
                        binary."${pluginName}Compiler".define "NDEBUG"
                        binary."${pluginName}Compiler".define "LEVEL", "1"
                        binary."${pluginName}Compiler".args "ARG1", "ARG2"
                    }
                }
            }
        }

        then:
        NativeExecutableBinary binary = project.binaries.testExecutable
        binary.tasks.withType(compileTaskClass)*.name == ["compileTestExecutableTestAnotherOne", "compileTestExecutableTest${StringUtils.capitalize(pluginName)}"]

        and:
        binary.tasks.withType(compileTaskClass).each { compile ->
            compile.toolChain == binary.toolChain
            compile.macros == [NDEBUG:null, LEVEL:"1"]
            compile.compilerArgs == ["ARG1", "ARG2"]
        }

        and:
        def linkTask = binary.tasks.link
        linkTask TaskDependencyMatchers.dependsOn("compileTestExecutableTestAnotherOne", "compileTestExecutableTest${StringUtils.capitalize(pluginName)}")
    }


    def touch(String filePath) {
        GFileUtils.touch(project.file(filePath))
    }

    def dsl(Closure closure) {
        closure.delegate = project
        closure()
        project.evaluate()
    }
}