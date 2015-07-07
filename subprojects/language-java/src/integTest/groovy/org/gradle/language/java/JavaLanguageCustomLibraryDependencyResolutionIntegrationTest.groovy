/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.java
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaLanguageCustomLibraryDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {


    def "can depend on a custom component producing a JVM library"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        zdep(CustomLibrary)
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'zdep'
                    }
                }
            }
        }
    }

    tasks {
        mainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(zdepJar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        succeeds ':mainJar'

    }

    def "custom component can consume a JVM library"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        main(JvmLibrarySpec)
        zdep(CustomLibrary) {
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'main'
                    }
                }
            }
        }
    }

    tasks {
        zdepJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileZdepJarZdepJava.taskDependencies.getDependencies(compileZdepJarZdepJava).contains(mainJar)
            }
        }
    }
}
'''
        file('src/zdep/java/App.java') << 'public class App extends TestApp {}'
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        succeeds ':zdepJar'

    }

    void applyJavaPlugin(File buildFile) {
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}
'''
    }

    void addCustomLibraryType(File buildFile) {
        buildFile << '''
import org.gradle.internal.service.ServiceRegistry
import org.gradle.jvm.internal.DefaultJarBinarySpec
import org.gradle.platform.base.internal.PlatformResolvers
import org.gradle.jvm.toolchain.JavaToolChainRegistry
import org.gradle.jvm.platform.internal.DefaultJavaPlatform

interface CustomLibrary extends LibrarySpec { }

class DefaultCustomLibrary extends BaseComponentSpec implements CustomLibrary { }

            class ComponentTypeRules extends RuleSource {

                @ComponentType
                void registerCustomComponentType(ComponentTypeBuilder<CustomLibrary> builder) {
                    builder.defaultImplementation(DefaultCustomLibrary)
                }

                @ComponentBinaries
                void createBinaries(ModelMap<JarBinarySpec> binaries, CustomLibrary jvmLibrary, PlatformResolvers platforms, @Path("buildDir") File buildDir, JavaToolChainRegistry toolChains) {
                    def binariesDir = new File(buildDir, "jars")
                    def classesDir = new File(buildDir, "classes")

                    def selectedPlatforms = [new DefaultJavaPlatform(JavaVersion.current())]
                    int id = 0
                    def multipleTargets = selectedPlatforms.size()>1
                    selectedPlatforms.each { platform ->
                        def toolChain = toolChains.getForPlatform(platform)
                        def binaryName = "${jvmLibrary.name}${multipleTargets?platform.name:''}${multipleTargets?id++:''}Jar"
                        binaries.create(binaryName) { jar ->
                            jar.baseName = jvmLibrary.name
                            jar.toolChain = toolChain
                            jar.targetPlatform = platform

                            def outputDir = new File(classesDir, jar.name)
                            jar.classesDir = outputDir
                            jar.resourcesDir = outputDir
                            jar.jarFile = new File(binariesDir, "${jar.name}/${jar.baseName}.jar")
                        }
                    }
                }

            }

            apply type: ComponentTypeRules
        '''
    }
}
