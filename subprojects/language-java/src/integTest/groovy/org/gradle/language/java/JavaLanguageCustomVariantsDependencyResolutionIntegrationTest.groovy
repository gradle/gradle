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
        firstDefaultDefaultJar {
            doLast {
                assert compileFirstDefaultDefaultJarFirstJava.taskDependencies.getDependencies(compileFirstDefaultDefaultJarFirstJava).contains(secondDefaultDefaultJar)
                assert compileFirstDefaultDefaultJarFirstJava.classpath.files == [file("${buildDir}/jars/secondDefaultDefaultJar/second.jar")] as Set
            }
        }
    }
}
'''
        file('src/first/java/FirstApp.java') << 'public class FirstApp extends SecondApp {}'
        file('src/second/java/SecondApp.java') << 'public class SecondApp {}'

        expect:
        succeeds ':firstDefaultDefaultJar'

    }

    def "can depend on a component with explicit flavors"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        first(CustomLibrary) {
            flavors 'paid', 'free'
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            flavors 'paid', 'free'
            sources {
                java(JavaSourceSet)
            }
        }
    }

    tasks {
        firstPaidDefaultJar {
            doLast {
                assert compileFirstPaidDefaultJarFirstJava.taskDependencies.getDependencies(compileFirstPaidDefaultJarFirstJava).contains(secondPaidDefaultJar)
                assert compileFirstPaidDefaultJarFirstJava.classpath.files == [file("${buildDir}/jars/secondPaidDefaultJar/second.jar")] as Set
            }
        }
        firstFreeDefaultJar {
            doLast {
                assert compileFirstFreeDefaultJarFirstJava.taskDependencies.getDependencies(compileFirstFreeDefaultJarFirstJava).contains(secondFreeDefaultJar)
                assert compileFirstFreeDefaultJarFirstJava.classpath.files == [file("${buildDir}/jars/secondFreeDefaultJar/second.jar")] as Set
            }
        }
    }
}
'''
        file('src/first/java/FirstApp.java') << 'public class FirstApp extends SecondApp {}'
        file('src/second/java/SecondApp.java') << 'public class SecondApp {}'

        expect:
        succeeds ':firstPaidDefaultJar'
        succeeds ':firstFreeDefaultJar'

    }

    @Unroll
    def "matching first {flavors #firstFlavors, builtTypes #firstBuildTypes} with second {flavors #secondFlavors, buildTypes #secondBuildTypes} #outcome"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        def firstFlavorsDSL = firstFlavors ? 'flavors ' + firstFlavors.collect { "'$it'" }.join(',') : ''
        def secondFlavorsDSL = secondFlavors ? 'flavors ' + secondFlavors.collect { "'$it'" }.join(',') : ''
        def firstBuildTypesDSL = firstBuildTypes ? 'buildTypes ' + firstBuildTypes.collect { "'$it'" }.join(',') : ''
        def secondBuildTypesDSL = secondBuildTypes ? 'buildTypes ' + secondBuildTypes.collect { "'$it'" }.join(',') : ''

        buildFile << """

model {
    components {
        first(CustomLibrary) {
            javaVersions 6
            $firstFlavorsDSL
            $firstBuildTypesDSL
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
            $secondBuildTypesDSL
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
        def buildTypesToTest = firstBuildTypes ?: ['default']
        Set consumedErrors = []
        buildTypesToTest.each { buildType ->
            flavorsToTest.each { flavor ->
                String taskName = "first${flavor.capitalize()}${buildType.capitalize()}Jar"
                if (errors[taskName]) {
                    consumedErrors << taskName
                    fails taskName
                    failure.assertHasDescription("Could not resolve all dependencies for 'Jar '$taskName'' source set 'Java source 'first:java''")
                    errors[taskName].each { err ->
                        failure.assertThatCause(containsText(err))
                    }
                } else {
                    succeeds taskName
                }
            }
        }

        and:
        // sanity check to make sure that we use all errors defined in the spec, in case the name of tasks change due
        // to internal implementation changes or variant values changes
        errors.keySet() == consumedErrors


        where:
        outcome    | firstBuildTypes | firstFlavors     | secondBuildTypes     | secondFlavors     | errors
        'succeeds' | []              | []               | []                   | []                | [:]
        'succeeds' | []              | []               | []                   | ['paid']          | [:]
        'fails'    | []              | []               | []                   | ['paid', 'free']  | [firstDefaultDefaultJar: ["Multiple binaries available for library 'second' (Java SE 6) : [Jar 'secondFreeDefaultJar', Jar 'secondPaidDefaultJar']"]]
        'fails'    | ['release']     | []               | ['debug']            | []                | [firstDefaultReleaseJar: ["Cannot find a compatible binary for library 'second' (Java SE 6).",
                                                                                                                               "Required platform 'java6', available: 'java6'",
                                                                                                                               "Required buildType 'release', available: 'debug'"]]
        'fails'    | []              | []               | ['release', 'debug'] | ['paid', 'free']  | [firstDefaultDefaultJar: ["Multiple binaries available for library 'second' (Java SE 6) : [Jar 'secondFreeDebugJar', Jar 'secondFreeReleaseJar', Jar 'secondPaidDebugJar', Jar 'secondPaidReleaseJar']"]]
        'succeeds' | []              | ['paid']         | []                   | ['paid']          | [:]
        'succeeds' | []              | ['paid', 'free'] | []                   | ['paid', 'free']  | [:]
        'succeeds' | ['debug']       | ['free']         | ['debug', 'release'] | ['free']          | [:]
        'succeeds' | ['debug']       | ['free']         | ['debug']            | ['free', 'paid']  | [:]
        'fails'    | []              | ['paid']         | []                   | ['free']          | [firstPaidDefaultJar: ["Cannot find a compatible binary for library 'second'",
                                                                                                                            "Required platform 'java6', available: 'java6'",
                                                                                                                            "Required flavor 'paid', available: 'free'"]]
        'fails'    | []              | ['paid']         | []                   | ['free', 'other'] | [firstPaidDefaultJar: ["Cannot find a compatible binary for library 'second'",
                                                                                                                            "Required platform 'java6', available: 'java6' on Jar 'secondFreeDefaultJar','java6' on Jar 'secondOtherDefaultJar'",
                                                                                                                            "Required flavor 'paid', available: 'free' on Jar 'secondFreeDefaultJar','other' on Jar 'secondOtherDefaultJar'"]]
        'fails'    | []              | ['paid', 'free'] | []                   | ['free', 'other'] | [firstPaidDefaultJar: ["Cannot find a compatible binary for library 'second'",
                                                                                                                            "Required platform 'java6', available: 'java6' on Jar 'secondFreeDefaultJar','java6' on Jar 'secondOtherDefaultJar'",
                                                                                                                            "Required flavor 'paid', available: 'free' on Jar 'secondFreeDefaultJar','other' on Jar 'secondOtherDefaultJar'"]]
        'fails'    | []              | ['paid', 'test'] | []                   | ['free', 'other'] | [firstPaidDefaultJar: ["Cannot find a compatible binary for library 'second'",
                                                                                                                            "Required platform 'java6', available: 'java6' on Jar 'secondFreeDefaultJar','java6' on Jar 'secondOtherDefaultJar'",
                                                                                                                            "Required flavor 'paid', available: 'free' on Jar 'secondFreeDefaultJar','other' on Jar 'secondOtherDefaultJar'"],
                                                                                                      firstTestDefaultJar: ["Cannot find a compatible binary for library 'second'",
                                                                                                                            "Required platform 'java6', available: 'java6' on Jar 'secondFreeDefaultJar','java6' on Jar 'secondOtherDefaultJar'",
                                                                                                                            "Required flavor 'test', available: 'free' on Jar 'secondFreeDefaultJar','other' on Jar 'secondOtherDefaultJar'"]]
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
    void buildTypes(String... buildTypes)

    List<Integer> getJavaVersions()
    List<BuildType> getBuildTypes()

}

interface BuildType extends Named {}

interface CustomBinaryVariants {
    @Variant
    String getFlavor()

    @Variant
    BuildType getBuildType()
}

interface CustomJarSpec extends JarBinarySpec, CustomBinaryVariants {}

class DefaultBuildType implements BuildType {
    String name
}

class CustomBinary extends DefaultJarBinarySpec implements CustomJarSpec {
    String flavor
    BuildType buildType
    // workaround for Groovy bug
    JvmBinaryTasks getTasks() { super.tasks }
}

class DefaultCustomLibrary extends BaseComponentSpec implements CustomLibrary {
    List<Integer> javaVersions = []
    List<String> flavors = []
    List<BuildType> buildTypes = []
    void javaVersions(int... platforms) { javaVersions.addAll(platforms) }
    void flavors(String... fvs) { flavors.addAll(fvs) }
    void buildTypes(String... bts) { buildTypes.addAll(bts.collect { new DefaultBuildType(name:it) }) }
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
                    def buildTypes = library.buildTypes?:[new DefaultBuildType(name:'default')]
                    def multipleTargets = javaVersions.size() > 1
                    javaVersions.each { version ->
                        flavors.each { flavor ->
                            buildTypes.each { buildType ->
                                def platform = platforms.resolve(JavaPlatform, DefaultPlatformRequirement.create("java${version}"))
                                def toolChain = toolChains.getForPlatform(platform)
                                def baseName = "${library.name}${flavor.capitalize()}${buildType.name.capitalize()}"
                                String binaryName = "$baseName${javaVersions.size() > 1 ? version :''}Jar"
                                while (binaries.containsKey(binaryName)) { binaryName = "${binaryName}x" }
                                binaries.create(binaryName) { jar ->
                                    jar.toolChain = toolChain
                                    jar.targetPlatform = platform
                                    if (library.flavors) {
                                        jar.flavor = flavor
                                    }
                                    if (library.buildTypes) {
                                        jar.buildType = buildType
                                    }
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
