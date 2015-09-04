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

package org.gradle.language

import org.apache.commons.lang.StringUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.language.base.LanguageSourceSet
import org.gradle.model.ModelMap
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.type.ModelTypes
import org.gradle.nativeplatform.NativeBinary
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.NativeExecutableSpec
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.platform.base.ComponentSpec
import org.gradle.util.GFileUtils
import org.gradle.util.TestUtil
import spock.lang.Specification

abstract class AbstractNativeComponentPluginTest extends Specification {
    final def project = TestUtil.createRootProject()

    abstract Class<? extends Plugin> getPluginClass();

    abstract Class<? extends LanguageSourceSet> getSourceSetClass();

    abstract Class<? extends Task> getCompileTaskClass();

    abstract String getPluginName();

    ModelMap<ComponentSpec> realizeComponents() {
        project.modelRegistry.realize(ModelPath.path("components"), ModelTypes.modelMap(ComponentSpec))
    }

    def "creates source set with conventional locations for components"() {
        when:
        dsl {
            pluginManager.apply pluginClass

            model {
                components {
                    exe(NativeExecutableSpec)
                    lib(NativeLibrarySpec)
                }
            }
        }

        then:
        def components = realizeComponents()
        components.size() == 2
        components.values()*.name == ["exe", "lib"]

        and:
        def exe = components.exe
        exe.sources instanceof ModelMap
        sourceSetClass.isInstance(exe.sources."$pluginName")
        exe.sources."$pluginName".source.srcDirs == [project.file("src/exe/$pluginName")] as Set
        exe.sources."$pluginName".exportedHeaders.srcDirs == [project.file("src/exe/headers")] as Set

        and:
        def lib = components.lib
        lib.sources instanceof ModelMap
        sourceSetClass.isInstance(lib.sources."$pluginName")
        lib.sources."$pluginName".source.srcDirs == [project.file("src/lib/$pluginName")] as Set
        lib.sources."$pluginName".exportedHeaders.srcDirs == [project.file("src/lib/headers")] as Set

        and:
        project.sources as Set == (lib.sources as Set) + (exe.sources as Set)
    }

    def "can configure source set locations"() {
        given:
        dsl {
            pluginManager.apply pluginClass

            model {
                components {
                    lib(NativeLibrarySpec) {
                        sources {
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
                    exe(NativeExecutableSpec) {
                        sources {
                            "$pluginName" {
                                source {
                                    srcDirs "d1", "d2"
                                }
                                exportedHeaders {
                                    srcDirs "h1", "h2"
                                }
                            }
                        }
                    }
                }
            }
        }


        expect:
        def components = realizeComponents()
        def exe = components.exe
        with(exe.sources."$pluginName") {
            source.srcDirs*.name == ["d1", "d2"]
            exportedHeaders.srcDirs*.name == ["h1", "h2"]
        }

        def lib = components.lib
        with(lib.sources."$pluginName") {
            source.srcDirs*.name == ["d3"]
            exportedHeaders.srcDirs*.name == ["h3"]
        }
    }

    def "creates compile tasks for each non-empty executable source set"() {
        when:
        touch("src/test/$pluginName/file.o")
        touch("src/test/anotherOne/file.o")
        dsl {
            pluginManager.apply pluginClass
            model {
                components {
                    test(NativeExecutableSpec) {
                        binaries.all { NativeBinary binary ->
                            binary."${pluginName}Compiler".define "NDEBUG"
                            binary."${pluginName}Compiler".define "LEVEL", "1"
                            binary."${pluginName}Compiler".args "ARG1", "ARG2"
                        }
                        sources {
                            anotherOne(sourceSetClass) {}
                            emptyOne(sourceSetClass) {}
                        }
                    }
                }
            }
        }

        then:
        NativeExecutableBinarySpec binary = project.binaries.testExecutable
        binary.tasks.withType(compileTaskClass)*.name as Set == ["compileTestExecutableTestAnotherOne", "compileTestExecutableTest${StringUtils.capitalize(pluginName)}"] as Set

        and:
        binary.tasks.withType(compileTaskClass).each { compile ->
            compile.toolChain == binary.toolChain
            compile.macros == [NDEBUG: null, LEVEL: "1"]
            compile.compilerArgs == ["ARG1", "ARG2"]
        }

        and:
        def linkTask = binary.tasks.link
        linkTask TaskDependencyMatchers.dependsOn("compileTestExecutableTestAnotherOne", "compileTestExecutableTest${StringUtils.capitalize(pluginName)}")
    }


    def touch(String filePath) {
        GFileUtils.touch(project.file(filePath))
    }

    def dsl(@DelegatesTo(Project) Closure closure) {
        closure.delegate = project
        closure()
        project.tasks.realize()
        project.bindAllModelRules()
    }
}
