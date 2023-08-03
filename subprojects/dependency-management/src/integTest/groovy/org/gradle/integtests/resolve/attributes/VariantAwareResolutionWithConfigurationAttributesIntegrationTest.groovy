/*
 * Copyright 2018 the original author or authors.
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


package org.gradle.integtests.resolve.attributes

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import org.gradle.test.fixtures.archive.JarTestFixture

@FluidDependenciesResolveTest
class VariantAwareResolutionWithConfigurationAttributesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("buildSrc/src/main/groovy/VariantsPlugin.groovy") << '''
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.attributes.Attribute
            import org.gradle.api.tasks.compile.JavaCompile
            import org.gradle.api.tasks.bundling.Jar
            import org.gradle.api.tasks.bundling.Zip
            import org.gradle.language.jvm.tasks.ProcessResources

            class VariantsPlugin implements Plugin<Project> {
                void apply(Project p) {
                        def buildType = Attribute.of('buildType', String)
                        def usage = Attribute.of('usage', String)
                        def flavor = Attribute.of('flavor', String)
                        p.dependencies.attributesSchema {
                           attribute(buildType)
                           attribute(usage)
                           attribute(flavor)
                        }
                        def buildTypes = ['debug', 'release']
                        def flavors = ['free', 'paid']
                        def processResources = p.tasks.processResources
                        buildTypes.each { bt ->
                            flavors.each { f ->
                                String baseName = "${f.capitalize()}${bt.capitalize()}"
                                def implementationConfig = p.configurations.create("implementation$baseName") {
                                    extendsFrom p.configurations.implementation
                                    canBeConsumed = false
                                    canBeResolved = false
                                }
                                def compileConfig = p.configurations.create("compile$baseName") {
                                    extendsFrom p.configurations.implementation
                                    assert canBeConsumed
                                    canBeResolved = false
                                    attributes.attribute(buildType, bt)
                                    attributes.attribute(flavor, f)
                                    attributes.attribute(usage, 'compile')
                                }
                                def _compileConfig = p.configurations.create("_compile$baseName") {
                                    extendsFrom implementationConfig
                                    canBeConsumed = false
                                    assert canBeResolved
                                    attributes.attribute(buildType, bt)
                                    attributes.attribute(flavor, f)
                                    attributes.attribute(usage, 'compile')
                                }
                                def mergedResourcesConf = p.configurations.create("resources${f.capitalize()}${bt.capitalize()}") {
                                    extendsFrom p.configurations.implementation

                                    attributes.attribute(buildType, bt)
                                    attributes.attribute(flavor, f)
                                    attributes.attribute(usage, 'resources')
                                }
                                p.dependencies.add(mergedResourcesConf.name, processResources.outputs.files)
                                def compileTask = p.tasks.create("compileJava${f.capitalize()}${bt.capitalize()}", JavaCompile) { task ->
                                    def taskName = task.name
                                    task.source(p.tasks.compileJava.source)
                                    task.destinationDirectory = project.file("${p.buildDir}/classes/$taskName")
                                    task.classpath = _compileConfig
                                    task.doFirst {
                                       // this is only for assertions in tests
                                       println "Compile classpath for ${p.path}:$taskName : ${task.classpath.files*.name}"
                                    }
                                }
                                def mergeResourcesTask = p.tasks.create("merge${f.capitalize()}${bt.capitalize()}Resources", Zip) { task ->
                                    task.archiveBaseName = "resources-${p.name}-${f}${bt}"
                                    task.from mergedResourcesConf
                                }
                                def aarTask = p.tasks.create("${f}${bt.capitalize()}Aar", Jar) { task ->
                                    // it's called AAR to reflect something that bundles everything
                                    task.dependsOn compileTask
                                    task.dependsOn mergeResourcesTask
                                    task.archiveBaseName = "${p.name}-${f}${bt}"
                                    task.archiveExtension = 'aar'
                                    task.from compileTask.destinationDirectory
                                    task.from p.zipTree(mergeResourcesTask.outputs.files.singleFile)
                                }
                                def jarTask = p.tasks.create("${f}${bt.capitalize()}Jar", Jar) { task ->
                                    task.dependsOn compileTask
                                    task.archiveBaseName = "${p.name}-${f}${bt}"
                                    task.from compileTask.destinationDirectory
                                }
                                p.artifacts.add("compile$baseName", jarTask)
                                p.artifacts.add("_compile$baseName", aarTask)
                                //p.artifacts.add(mergedResourcesConf.name, mergeResourcesTask)
                            }
                        }
                }
            }
        '''
    }

    @ToBeFixedForConfigurationCache(because = "task uses the Configuration API")
    def "configurations are wired properly"() {
        withVariants(buildFile)

        given:
        file("build.gradle") << '''
            task checkConfigurations {
                doLast {
                    ['compileFreeDebug', 'compileFreeRelease', 'compilePaidDebug', 'compilePaidRelease'].each {
                        assert !configurations.getByName(it).canBeResolved
                        assert configurations.getByName(it).canBeConsumed
                        assert configurations.getByName("_$it").canBeResolved
                        assert !configurations.getByName("_$it").canBeConsumed
                    }
                }
            }
        '''

        when:
        run 'checkConfigurations'

        then:
        noExceptionThrown()
    }

    @ToBeFixedForConfigurationCache
    def "compiling project variant doesn't imply execution of other variants build tasks"() {
        testDirectory.mkdirs()
        def projectDir = new FileTreeBuilder(testDirectory)
        given:
        projectDir {
            VariantAwareResolutionWithConfigurationAttributesIntegrationTest.withVariants(buildFile)
            VariantAwareResolutionWithConfigurationAttributesIntegrationTest.withExternalDependencies(buildFile, '''
                implementationFreeDebug 'org.apache.commons:commons-lang3:3.5'
            ''')
            src {
                main {
                    java {
                        'Hello.java'('''import org.apache.commons.lang3.StringUtils;

                            public class Hello {
                                public static void main(String... args) {
                                    System.out.println("Hello " + StringUtils.capitalize(args[0]));
                                }
                            }
                        ''')
                    }
                }
            }
        }

        when:
        run 'compileJavaFreeDebug'

        then:
        executedAndNotSkipped ':compileJavaFreeDebug'
        notExecuted ':compileJavaFreeRelease'
        notExecuted ':compileJavaPaidDebug'
        notExecuted ':compileJavaPaidRelease'
    }

    @ToBeFixedForConfigurationCache
    def "consuming subproject variant builds the project with the appropriate tasks"() {
        given:
        subproject('core') {
            def buildDotGradle = file('build.gradle')
            VariantAwareResolutionWithConfigurationAttributesIntegrationTest.withVariants(buildDotGradle)
            VariantAwareResolutionWithConfigurationAttributesIntegrationTest.withExternalDependencies(buildDotGradle, '''
                implementationFreeDebug 'org.apache.commons:commons-lang3:3.5'
            ''')
            src {
                main {
                    resources {
                        'core.txt'('core')
                    }
                    java {
                        com {
                            acme {
                                core {
                                    'Hello.java'('''package com.acme.core;

                            import org.apache.commons.lang3.StringUtils;

                            public class Hello {
                                public void greet(String name) {
                                    System.out.println("Hello " + StringUtils.capitalize(name));
                                }
                            }
                        ''')
                                }
                            }
                        }

                    }
                }
            }
        }
        subproject('client') {
            def buildDotGradle = file('build.gradle')
            VariantAwareResolutionWithConfigurationAttributesIntegrationTest.withVariants(buildDotGradle)
            VariantAwareResolutionWithConfigurationAttributesIntegrationTest.withDependencies(buildDotGradle, 'implementation project(":core")')
            src {
                main {
                    resources {
                        'client.txt'('client')
                    }
                    java {
                        'Main.java'('''import com.acme.core.Hello;

                            public class Main {
                                public static void main(String... args) {
                                    Hello hello = new Hello();
                                    hello.greet(args[0]);
                                }
                            }
                        ''')
                    }
                }
            }
        }

        when:
        run ':client:freeDebugJar'

        then:
        executedAndNotSkipped ':core:compileJavaFreeDebug', ':core:freeDebugJar'
        notExecuted ':core:compileJavaFreeRelease'
        notExecuted ':core:processResources'
        notExecuted ':core:mergeFreeDebugResources'

        and: "compile classpath for core includes external dependency"
        outputContains 'Compile classpath for :core:compileJavaFreeDebug : [commons-lang3-3.5.jar]'

        and: "compile classpath for client excludes external dependency"
        outputContains 'Compile classpath for :client:compileJavaFreeDebug : [core-freedebug.jar]'

        when:
        run 'clean', ':client:freeDebugAar'

        then:
        executedAndNotSkipped ':core:processResources'
        executedAndNotSkipped ':client:processResources'
        executedAndNotSkipped ':client:mergeFreeDebugResources'
        executedAndNotSkipped ':client:freeDebugAar'

        and:
        def aar = new JarTestFixture(file('client/build/libs/client-freedebug.aar'))
        aar.hasDescendants('Main.class', 'client.txt', 'core.txt')
    }

    private static File withVariants(File buildFile) {
        buildFile << 'apply plugin: "java"\n'
        buildFile << 'apply plugin: VariantsPlugin\n'
        buildFile
    }

    private static File withExternalDependencies(File buildFile, String dependenciesBlock) {
        buildFile << """
            ${mavenCentralRepository()}
            dependencies {
                $dependenciesBlock
            }
        """
        buildFile
    }

    private static File withDependencies(File buildFile, String dependenciesBlock) {
        buildFile << """
            dependencies {
                $dependenciesBlock
            }
        """
        buildFile
    }

    private File subproject(String name, Closure structure) {
        file("settings.gradle") << "include '$name'\n"
        def pdir = new File(testDirectory, name)
        pdir.mkdirs()
        new FileTreeBuilder(pdir).call(structure)
        pdir
    }

}
