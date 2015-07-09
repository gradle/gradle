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
import spock.lang.Unroll

import static org.gradle.util.Matchers.containsText

class JavaLanguageCustomVariantsDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {

    def "can depend on a component without specifying any variant dimension"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        first(CustomLibrary) {
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            sources {
                java(JavaSourceSet)
            }
        }
    }

    tasks {
        firstDefaultJar {
            doLast {
                assert compileFirstDefaultJarFirstJava.taskDependencies.getDependencies(compileFirstDefaultJarFirstJava).contains(secondDefaultJar)
                assert compileFirstDefaultJarFirstJava.classpath.files == [file("${buildDir}/jars/secondDefaultJar/second.jar")] as Set
            }
        }
    }
}
'''
        file('src/first/java/FirstApp.java') << 'public class FirstApp extends SecondApp {}'
        file('src/second/java/SecondApp.java') << 'public class SecondApp {}'

        expect:
        succeeds ':firstDefaultJar'

    }

    def "can depend on a component with explicit flavors"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        first(CustomLibrary) {
            flavors 'release', 'debug'
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            flavors 'release', 'debug'
            sources {
                java(JavaSourceSet)
            }
        }
    }

    tasks {
        firstReleaseJar {
            doLast {
                assert compileFirstReleaseJarFirstJava.taskDependencies.getDependencies(compileFirstReleaseJarFirstJava).contains(secondReleaseJar)
                assert compileFirstReleaseJarFirstJava.classpath.files == [file("${buildDir}/jars/secondReleaseJar/second.jar")] as Set
            }
        }
        firstDebugJar {
            doLast {
                assert compileFirstDebugJarFirstJava.taskDependencies.getDependencies(compileFirstDebugJarFirstJava).contains(secondDebugJar)
                assert compileFirstDebugJarFirstJava.classpath.files == [file("${buildDir}/jars/secondDebugJar/second.jar")] as Set
            }
        }
    }
}
'''
        file('src/first/java/FirstApp.java') << 'public class FirstApp extends SecondApp {}'
        file('src/second/java/SecondApp.java') << 'public class SecondApp {}'

        expect:
        succeeds ':firstReleaseJar'
        succeeds ':firstDebugJar'

    }

    @Unroll
    def "matching first flavors #firstFlavors with second flavors #secondFlavors #outcome"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        def firstFlavorsDSL = firstFlavors ? 'flavors ' + firstFlavors.collect { "'$it'" }.join(',') : ''
        def secondFlavorsDSL = secondFlavors ? 'flavors ' + secondFlavors.collect { "'$it'" }.join(',') : ''

        buildFile << """

model {
    components {
        first(CustomLibrary) {
            javaVersions 6
            $firstFlavorsDSL
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            javaVersions 6
            $secondFlavorsDSL
            sources {
                java(JavaSourceSet)
            }
        }
    }
}
"""
        file('src/first/java/FirstApp.java') << 'public class FirstApp extends SecondApp {}'
        file('src/second/java/SecondApp.java') << 'public class SecondApp {}'

        expect:
        def flavorsToTest = firstFlavors ?: ['default']
        flavorsToTest.each { flavor ->
            def taskName = "first${flavor.capitalize()}Jar"
            if (errors[flavor]) {
                fails taskName
                failure.assertHasDescription("Could not resolve all dependencies for 'Jar '$taskName'' source set 'Java source 'first:java''")
                errors[flavor].each { err ->
                    failure.assertThatCause(containsText(err))
                }
            } else {
                succeeds taskName
            }
        }

        where:
        outcome    | firstFlavors         | secondFlavors        | errors
        'succeeds' | []                   | []                   | [:]
        'succeeds' | []                   | ['release']          | [:]
        'fails'    | []                   | ['release', 'debug'] | [default: ["Multiple binaries available for library 'second' (Java SE 6) : [Jar 'secondDebugJar', Jar 'secondReleaseJar']"]]
        'succeeds' | ['release']          | ['release']          | [:]
        'succeeds' | ['release', 'debug'] | ['release', 'debug'] | [:]
        'fails'    | ['release']          | ['debug']            | [release: ["Cannot find a compatible binary for library 'second'",
                                                                              "Required platform 'java6', available: 'java6'",
                                                                              "Required flavor 'release', available: 'debug'"]]
        'fails'    | ['release']          | ['debug', 'other']   | [release: ["Cannot find a compatible binary for library 'second'",
                                                                              "Required platform 'java6', available: 'java6' on Jar 'secondDebugJar','java6' on Jar 'secondOtherJar'",
                                                                              "Required flavor 'release', available: 'debug' on Jar 'secondDebugJar','other' on Jar 'secondOtherJar'"]]
        'fails'    | ['release', 'debug'] | ['debug', 'other']   | [release: ["Cannot find a compatible binary for library 'second'",
                                                                              "Required platform 'java6', available: 'java6' on Jar 'secondDebugJar','java6' on Jar 'secondOtherJar'",
                                                                              "Required flavor 'release', available: 'debug' on Jar 'secondDebugJar','other' on Jar 'secondOtherJar'"]]
        'fails'    | ['release', 'test']  | ['debug', 'other']   | [release: ["Cannot find a compatible binary for library 'second'",
                                                                              "Required platform 'java6', available: 'java6' on Jar 'secondDebugJar','java6' on Jar 'secondOtherJar'",
                                                                              "Required flavor 'release', available: 'debug' on Jar 'secondDebugJar','other' on Jar 'secondOtherJar'"],
                                                                    test   : ["Cannot find a compatible binary for library 'second'",
                                                                              "Required platform 'java6', available: 'java6' on Jar 'secondDebugJar','java6' on Jar 'secondOtherJar'",
                                                                              "Required flavor 'test', available: 'debug' on Jar 'secondDebugJar','other' on Jar 'secondOtherJar'"]]
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
import org.gradle.platform.base.internal.DefaultPlatformRequirement

interface CustomLibrary extends LibrarySpec {
    void javaVersions(int... platforms)
    void flavors(String... flavors)

    List<Integer> getJavaVersions()

}

interface BuildType extends Named {}

interface CustomBinaryVariants {
    @Variant
    String getFlavor()

    @Variant
    BuildType getBuildType()
}

interface CustomJarSpec extends JarBinarySpec, CustomBinaryVariants {}

class CustomBinary extends DefaultJarBinarySpec implements CustomJarSpec {
    String flavor
    BuildType buildType
    // workaround for Groovy bug
    JvmBinaryTasks getTasks() { super.tasks }
}

class DefaultCustomLibrary extends BaseComponentSpec implements CustomLibrary {
    List<Integer> javaVersions = []
    List<String> flavors = []
    void javaVersions(int... platforms) { javaVersions.addAll(platforms) }
    void flavors(String... fvs) { flavors.addAll(fvs) }
}

            class ComponentTypeRules extends RuleSource {

                @ComponentType
                void registerCustomComponentType(ComponentTypeBuilder<CustomLibrary> builder) {
                    builder.defaultImplementation(DefaultCustomLibrary)
                }

                @BinaryType
                void registerJar(BinaryTypeBuilder<CustomJarSpec> builder) {
                    builder.defaultImplementation(CustomBinary)
                }

                @ComponentBinaries
                void createBinaries(ModelMap<CustomJarSpec> binaries,
                    CustomLibrary library,
                    PlatformResolvers platforms,
                    @Path("buildDir") File buildDir,
                    JavaToolChainRegistry toolChains) {

                    def binariesDir = new File(buildDir, "jars")
                    def classesDir = new File(buildDir, "classes")
                    def javaVersions = library.javaVersions ?: [JavaVersion.current().majorVersion]
                    def flavors = library.flavors?:['default']
                    def multipleTargets = javaVersions.size() > 1
                    javaVersions.each { version ->
                        flavors.each { flavor ->
                            def platform = platforms.resolve(JavaPlatform, DefaultPlatformRequirement.create("java${version}"))
                            def toolChain = toolChains.getForPlatform(platform)
                            def baseName = "${library.name}${flavor.capitalize()}"
                            String binaryName = "$baseName${javaVersions.size() > 1 ? version :''}Jar"
                            while (binaries.containsKey(binaryName)) { binaryName = "${binaryName}x" }
                            binaries.create(binaryName) { jar ->
                                jar.toolChain = toolChain
                                jar.targetPlatform = platform
                                if (library.flavors) {
                                    jar.flavor = flavor
                                }
                            }
                        }
                    }
                }

            }

            apply type: ComponentTypeRules
        '''
    }
}
