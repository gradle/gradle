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


package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.gradle.test.fixtures.archive.JarTestFixture
import org.junit.runner.RunWith

@RunWith(FluidDependenciesResolveRunner)
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
                           attribute(buildType).compatibilityRules.assumeCompatibleWhenMissing()
                           attribute(usage).compatibilityRules.assumeCompatibleWhenMissing()
                           attribute(flavor).compatibilityRules.assumeCompatibleWhenMissing()
                        }
                        def buildTypes = ['debug', 'release']
                        def flavors = ['free', 'paid']
                        def processResources = p.tasks.processResources
                        p.configurations.compile.canBeResolved = false
                        p.configurations.compile.canBeConsumed = false
                        buildTypes.each { bt ->
                            flavors.each { f ->
                                String baseName = "compile${f.capitalize()}${bt.capitalize()}"
                                def compileConfig = p.configurations.create(baseName) {
                                    extendsFrom p.configurations.compile
                                    canBeResolved = false
                                    attributes.attribute(buildType, bt)
                                    attributes.attribute(flavor, f)
                                    attributes.attribute(usage, 'compile')
                                }
                                def _compileConfig = p.configurations.create("_$baseName") {
                                    extendsFrom p.configurations.compile
                                    canBeConsumed = false
                                    attributes.attribute(buildType, bt)
                                    attributes.attribute(flavor, f)
                                    attributes.attribute(usage, 'compile')
                                }
                                def mergedResourcesConf = p.configurations.create("resources${f.capitalize()}${bt.capitalize()}") {
                                    extendsFrom p.configurations.compile
                                    
                                    attributes.attribute(buildType, bt)
                                    attributes.attribute(flavor, f)
                                    attributes.attribute(usage, 'resources')
                                }
                                p.dependencies.add(mergedResourcesConf.name, processResources.outputs.files)
                                def compileTask = p.tasks.create("compileJava${f.capitalize()}${bt.capitalize()}", JavaCompile) { task ->
                                    def taskName = task.name
                                    task.source(p.tasks.compileJava.source)
                                    task.destinationDir = project.file("${p.buildDir}/classes/$taskName")
                                    task.classpath = _compileConfig
                                    task.doFirst {
                                       // this is only for assertions in tests
                                       println "Compile classpath for ${p.path}:$taskName : ${task.classpath.files*.name}"
                                    }
                                }
                                def mergeResourcesTask = p.tasks.create("merge${f.capitalize()}${bt.capitalize()}Resources", Zip) { task ->
                                    task.baseName = "resources-${p.name}-${f}${bt}"
                                    task.from mergedResourcesConf
                                }
                                def aarTask = p.tasks.create("${f}${bt.capitalize()}Aar", Jar) { task ->
                                    // it's called AAR to reflect something that bundles everything
                                    task.dependsOn mergeResourcesTask
                                    task.baseName = "${p.name}-${f}${bt}"
                                    task.extension = 'aar'
                                    task.from compileTask.outputs.files
                                    task.from p.zipTree(mergeResourcesTask.outputs.files.singleFile)
                                }
                                def jarTask = p.tasks.create("${f}${bt.capitalize()}Jar", Jar) { task ->
                                    task.baseName = "${p.name}-${f}${bt}"
                                    task.from compileTask.outputs.files
                                }
                                p.artifacts.add(baseName, jarTask)
                                p.artifacts.add("_$baseName", aarTask)
                                //p.artifacts.add(mergedResourcesConf.name, mergeResourcesTask)
                            }
                        }
                }
            }
        '''
    }

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

    def "compiling project variant doesn't imply execution of other variants build tasks"() {
        def projectDir = new FileTreeBuilder(testDirectory)
        given:
        projectDir {
            withVariants(buildFile)
            withExternalDependencies(buildFile, '''
                _compileFreeDebug 'org.apache.commons:commons-lang3:3.5'
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

    def "consuming subproject variant builds the project with the appropriate tasks"() {
        given:
        subproject('core') {
            def buildDotGradle = file('build.gradle')
            withVariants(buildDotGradle)
            withExternalDependencies(buildDotGradle, '''
                _compileFreeDebug 'org.apache.commons:commons-lang3:3.5'
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
            withVariants(buildDotGradle)
            withDependencies(buildDotGradle, 'compile project(":core")')
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
            repositories {
                jcenter()
            }
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
