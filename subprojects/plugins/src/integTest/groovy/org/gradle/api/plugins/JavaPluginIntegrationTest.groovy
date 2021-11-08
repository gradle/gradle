/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.internal.component.BuildableJavaComponent
import org.gradle.api.internal.component.ComponentRegistry
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaPluginIntegrationTest extends AbstractIntegrationSpec {

    def appliesBasePluginsAndAddsConventionObject() {
        given:
        buildFile << """
            apply plugin: 'java'

            task expect {

                def component = project.services.get(${ComponentRegistry.canonicalName}).mainComponent
                assert component instanceof ${BuildableJavaComponent.canonicalName}
                assert component.runtimeClasspath != null
                assert component.compileDependencies == project.configurations.compileClasspath

                def buildTasks = component.buildTasks as List
                doLast {
                    assert buildTasks == [ JavaBasePlugin.BUILD_TASK_NAME ]
                }
            }
        """
        expect:
        succeeds "expect"
    }

    def "Java plugin adds outgoing variant for main source set"() {
        buildFile << """
            plugins {
                id 'java'
            }
            """

        expect:
        succeeds "outgoingVariants"

        outputContains("""
            --------------------------------------------------
            Variant mainSourceElements
            --------------------------------------------------
            Capabilities
                - :${getTestDirectory().getName()}:unspecified (default capability)
            Attributes
                - org.gradle.category = sources
                - org.gradle.sources  = all-source-directories
                - org.gradle.usage    = verification

            Artifacts
                - src${File.separator}main${File.separator}java (artifactType = directory)
                - src${File.separator}main${File.separator}resources (artifactType = directory)
            """.stripIndent())
    }

    def "Java plugin adds outgoing variant for main source set containing additional directories"() {
        buildFile << """
            plugins {
                id 'java'
            }

            sourceSets.main.java.srcDir 'src/more/java'
            """
        file("src/more/java").createDir()

        expect:
        succeeds "outgoingVariants"

        outputContains("""
            --------------------------------------------------
            Variant mainSourceElements
            --------------------------------------------------
            Capabilities
                - :${getTestDirectory().getName()}:unspecified (default capability)
            Attributes
                - org.gradle.category = sources
                - org.gradle.sources  = all-source-directories
                - org.gradle.usage    = verification

            Artifacts
                - src${File.separator}main${File.separator}java (artifactType = directory)
                - src${File.separator}more${File.separator}java (artifactType = directory)
                - src${File.separator}main${File.separator}resources (artifactType = directory)
            """.stripIndent())
    }

    def "mainSourceElements can be consumed by another task via Dependency Management"() {
        buildFile << """
            plugins {
                id 'java'
            }

            // A resolvable configuration to collect source data
            def sourceElementsConfig = configurations.create("sourceElements") {
                visible = true
                canBeResolved = true
                canBeConsumed = false
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.VERIFICATION))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.SOURCES))
                    attribute(Sources.SOURCES_ATTRIBUTE, objects.named(Sources, Sources.ALL_SOURCE_DIRS))
                }
            }

            dependencies {
                sourceElements project
            }

            def testResolve = tasks.register('testResolve') {
                doLast {
                    assert sourceElementsConfig.getResolvedConfiguration().getFiles().containsAll([project.file("${getTestDirectory().getPath()}/src/main/resources"),
                                                                                                   project.file("${getTestDirectory().getPath()}/src/main/java")])
                }
            }
            """.stripIndent()

        file("src/main/java/com/example/MyClass.java") << """
            package com.example;

            public class MyClass {
                public void hello() {
                    System.out.println("Hello");
                }
            }
            """.stripIndent()

        expect:
        succeeds('testResolve')
    }

    def "mainSourceElements can be consumed by another task in a different project via Dependency Management"() {
        def subADir = createDir("subA")
        def buildFileA = subADir.file("build.gradle") << """
            plugins {
                id 'java'
            }
            """.stripIndent()

        subADir.file("src/test/java/com/exampleA/MyClassA.java") << """
            package com.exampleA;

            public class MyClassA {
                public void hello() {
                    System.out.println("Hello");
                }
            }
            """.stripIndent()

        def subBDir = createDir("subB")
        def buildFileB = subBDir.file("build.gradle") << """
            plugins {
                id 'java'
            }
            """.stripIndent()

        subBDir.file("src/test/java/com/exampleB/MyClassB.java") << """
            package com.exampleB;

            public class MyClassB {
                public void hello() {
                    System.out.println("Hello");
                }
            }
            """.stripIndent()

        settingsFile << """
            include ':subA'
            include ':subB'
            """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
            }

            dependencies {
                implementation project(':subA')
                implementation project(':subB')
            }

            // A resolvable configuration to collect JaCoCo coverage data
            def sourceElementsConfig = configurations.create("sourceElements") {
                visible = true
                canBeResolved = true
                canBeConsumed = false
                extendsFrom(configurations.implementation)
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.VERIFICATION))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.SOURCES))
                    attribute(Sources.SOURCES_ATTRIBUTE, objects.named(Sources, Sources.ALL_SOURCE_DIRS))
                }
            }

            def testResolve = tasks.register('testResolve') {
                doLast {
                    assert sourceElementsConfig.getResolvedConfiguration().getFiles().containsAll([project.file("${subADir.getPath()}/src/main/resources"),
                                                                                                   project.file("${subADir.getPath()}/src/main/java"),
                                                                                                   project.file("${subBDir.getPath()}/src/main/resources"),
                                                                                                   project.file("${subBDir.getPath()}/src/main/java")])
                }
            }
            """.stripIndent()

        expect:
        succeeds('testResolve')
    }
}
