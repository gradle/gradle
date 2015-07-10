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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
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

    @Requires(TestPrecondition.JDK7_OR_LATER)
    @Unroll("matching {jdk #jdk1, flavors #flavors1, builtTypes #buildTypes1} with {jdk #jdk2, flavors #flavors2, buildTypes #buildTypes2} #outcome")
    def "check resolution of a custom component onto a component of the same type (same class, same variant dimensions)"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        def firstFlavorsDSL = flavors1 ? 'flavors ' + flavors1.collect { "'$it'" }.join(',') : ''
        def secondFlavorsDSL = flavors2 ? 'flavors ' + flavors2.collect { "'$it'" }.join(',') : ''
        def firstBuildTypesDSL = buildTypes1 ? 'buildTypes ' + buildTypes1.collect { "'$it'" }.join(',') : ''
        def secondBuildTypesDSL = buildTypes2 ? 'buildTypes ' + buildTypes2.collect { "'$it'" }.join(',') : ''
        def firstJavaVersionsDSL = "javaVersions ${jdk1.join(',')}"
        def secondJavaVersionsDSL = "javaVersions ${jdk2.join(',')}"

        def flavorsToTest = flavors1 ?: ['default']
        def buildTypesToTest = buildTypes1 ?: ['default']

        // "selected" contains the information about the expected selected binaries
        // so we are going to build the "tasks" block which is going to make the assertions about
        // the selected component binary
        String tasksBlock = generateCheckDependenciesDSLBlockForCustomComponent(selected, buildTypesToTest, flavorsToTest, jdk1)

        buildFile << """

model {
    components {
        first(CustomLibrary) {

            $firstJavaVersionsDSL
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

            $secondJavaVersionsDSL
            $secondFlavorsDSL
            $secondBuildTypesDSL

            sources {
                java(JavaSourceSet)
            }
        }
    }

    $tasksBlock
}
"""
        file('src/first/java/FirstApp.java') << 'public class FirstApp extends SecondApp {}'
        file('src/second/java/SecondApp.java') << 'public class SecondApp {}'

        expect:
        Set consumedErrors = []
        forEachCustomBinary(buildTypesToTest, flavorsToTest, jdk1) { String taskName ->
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

        and:
        // sanity check to make sure that we use all errors defined in the spec, in case the name of tasks change due
        // to internal implementation changes or variant values changes
        errors.keySet() == consumedErrors

        where:
        jdk1 | buildTypes1 | flavors1         | jdk2      | buildTypes2          | flavors2          | selected                                            | errors
        [6]  | []          | []               | [6]       | []                   | []                | [:]                                                 | [:]
        [6]  | []          | []               | [6]       | []                   | []                | [firstDefaultDefaultJar: 'secondDefaultDefaultJar'] | [:]
        [6]  | []          | []               | [6]       | []                   | ['paid']          | [firstDefaultDefaultJar: 'secondPaidDefaultJar']    | [:]
        [6]  | []          | []               | [6]       | []                   | ['paid', 'free']  | [:]                                                 | [firstDefaultDefaultJar: ["Multiple binaries available for library 'second' (Java SE 6) :",
                                                                                                                                                                                       "Jar 'secondFreeDefaultJar'", "flavor 'free'",
                                                                                                                                                                                       "Jar 'secondPaidDefaultJar'", "flavor 'paid'"]]
        [6]  | ['release'] | []               | [6]       | ['debug']            | []                | [:]                                                 | [firstDefaultReleaseJar: ["Cannot find a compatible binary for library 'second' (Java SE 6).",
                                                                                                                                                                                       "Required platform 'java6', available: 'java6'",
                                                                                                                                                                                       "Required buildType 'release', available: 'debug'"]]
        [6]  | []          | []               | [6]       | ['release', 'debug'] | ['paid', 'free']  | [:]                                                 | [firstDefaultDefaultJar: ["Multiple binaries available for library 'second' (Java SE 6) :",
                                                                                                                                                                                       "Jar 'secondFreeDebugJar'", "buildType 'debug'", "flavor 'free'",
                                                                                                                                                                                       "Jar 'secondFreeReleaseJar'", "buildType 'release'", "flavor 'free'",
                                                                                                                                                                                       "Jar 'secondPaidDebugJar'", "buildType 'debug'", "flavor 'paid'",
                                                                                                                                                                                       "Jar 'secondPaidReleaseJar'", "buildType 'release'", "flavor 'paid'"]]
        [6]  | []          | ['paid']         | [6]       | []                   | ['paid']          | [firstPaidDefaultJar: 'secondPaidDefaultJar']       | [:]
        [6]  | []          | ['paid', 'free'] | [6]       | []                   | ['paid', 'free']  | [firstFreeDefaultJar: 'secondFreeDefaultJar',
                                                                                                        firstPaidDefaultJar: 'secondPaidDefaultJar']       | [:]
        [6]  | ['debug']   | ['free']         | [6]       | ['debug', 'release'] | ['free']          | [firstFreeDebugJar: 'secondFreeDebugJar']           | [:]
        [6]  | ['debug']   | ['free']         | [6]       | ['debug']            | ['free', 'paid']  | [firstFreeDebugJar: 'secondFreeDebugJar']           | [:]
        [6]  | ['debug']   | ['free']         | [5, 6, 7] | ['debug']            | ['free']          | [firstFreeDebugJar: 'secondFreeDebug6Jar']          | [:]
        [6]  | ['debug']   | ['free']         | [7]       | ['debug']            | ['free']          | [:]                                                 | [firstFreeDebugJar: ["Cannot find a compatible binary for library 'second' (Java SE 6)",
                                                                                                                                                                                  "Required platform 'java6', available: 'java7'",
                                                                                                                                                                                  "Required flavor 'free', available: 'free'",
                                                                                                                                                                                  "Required buildType 'debug', available: 'debug'"]]
        [6]  | []          | ['paid']         | [6]       | []                   | ['free']          | [:]                                                 | [firstPaidDefaultJar: ["Cannot find a compatible binary for library 'second'",
                                                                                                                                                                                    "Required platform 'java6', available: 'java6'",
                                                                                                                                                                                    "Required flavor 'paid', available: 'free'"]]
        [6]  | []          | ['paid']         | [6]       | []                   | ['free', 'other'] | [:]                                                 | [firstPaidDefaultJar: ["Cannot find a compatible binary for library 'second'",
                                                                                                                                                                                    "Required platform 'java6', available: 'java6' on Jar 'secondFreeDefaultJar','java6' on Jar 'secondOtherDefaultJar'",
                                                                                                                                                                                    "Required flavor 'paid', available: 'free' on Jar 'secondFreeDefaultJar','other' on Jar 'secondOtherDefaultJar'"]]
        [6]  | []          | ['paid', 'free'] | [6]       | []                   | ['free', 'other'] | [:]                                                 | [firstPaidDefaultJar: ["Cannot find a compatible binary for library 'second'",
                                                                                                                                                                                    "Required platform 'java6', available: 'java6' on Jar 'secondFreeDefaultJar','java6' on Jar 'secondOtherDefaultJar'",
                                                                                                                                                                                    "Required flavor 'paid', available: 'free' on Jar 'secondFreeDefaultJar','other' on Jar 'secondOtherDefaultJar'"]]
        [6]  | []          | ['paid', 'test'] | [6]       | []                   | ['free', 'other'] | [:]                                                 | [firstPaidDefaultJar: ["Cannot find a compatible binary for library 'second'",
                                                                                                                                                                                    "Required platform 'java6', available: 'java6' on Jar 'secondFreeDefaultJar','java6' on Jar 'secondOtherDefaultJar'",
                                                                                                                                                                                    "Required flavor 'paid', available: 'free' on Jar 'secondFreeDefaultJar','other' on Jar 'secondOtherDefaultJar'"],
                                                                                                                                                              firstTestDefaultJar: ["Cannot find a compatible binary for library 'second'",
                                                                                                                                                                                    "Required platform 'java6', available: 'java6' on Jar 'secondFreeDefaultJar','java6' on Jar 'secondOtherDefaultJar'",
                                                                                                                                                                                    "Required flavor 'test', available: 'free' on Jar 'secondFreeDefaultJar','other' on Jar 'secondOtherDefaultJar'"]]
        and:
        outcome = errors ? 'fails' : 'succeeds'
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    @Unroll("matching custom {jdk #jdk1, flavors #flavors1, builtTypes #buildTypes1} with Java {jdk #jdk2} #outcome")
    def "building a custom binary type and resolving against a library with standard JarBinarySpec instances"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        def firstFlavorsDSL = flavors1 ? 'flavors ' + flavors1.collect { "'$it'" }.join(',') : ''
        def firstBuildTypesDSL = buildTypes1 ? 'buildTypes ' + buildTypes1.collect { "'$it'" }.join(',') : ''
        def firstJavaVersionsDSL = "javaVersions ${jdk1.join(',')}"
        def secondJavaVersionsDSL = "${jdk2.collect { "targetPlatform 'java$it' " }.join('\n')}"

        def flavorsToTest = flavors1 ?: ['default']
        def buildTypesToTest = buildTypes1 ?: ['default']

        // "selected" contains the information about the expected selected binaries
        // so we are going to build the "tasks" block which is going to make the assertions about
        // the selected component binary
        String tasksBlock = generateCheckDependenciesDSLBlockForCustomComponent(selected, buildTypesToTest, flavorsToTest, jdk1)

        buildFile << """

model {
    components {
        first(CustomLibrary) {

            $firstJavaVersionsDSL
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
        second(JvmLibrarySpec) {
            $secondJavaVersionsDSL
        }
    }

    $tasksBlock
}
"""
        file('src/first/java/FirstApp.java') << 'public class FirstApp extends SecondApp {}'
        file('src/second/java/SecondApp.java') << 'public class SecondApp {}'

        expect:
        Set consumedErrors = []
        forEachCustomBinary(buildTypesToTest, flavorsToTest, jdk1) { String taskName ->
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

        and:
        // sanity check to make sure that we use all errors defined in the spec, in case the name of tasks change due
        // to internal implementation changes or variant values changes
        errors.keySet() == consumedErrors

        where:
        jdk1   | buildTypes1          | flavors1         | jdk2   | selected                                | errors
        [6]    | []                   | []               | [6]    | [:]                                     | [:]
        [6]    | ['debug']            | ['free']         | [6, 7] | [firstFreeDebugJar: 'java6SecondJar']   | [:]
        [6, 7] | ['debug']            | ['free']         | [6, 7] | [firstFreeDebug6Jar: 'java6SecondJar',
                                                                     firstFreeDebug7Jar: 'java7SecondJar']  | [:]
        [5, 6] | ['debug']            | ['free']         | [6, 7] | [firstFreeDebug6Jar: 'java6SecondJar']  | [firstFreeDebug5Jar: ["Cannot find a compatible binary for library 'second' (Java SE 5)",
                                                                                                                                    "Required platform 'java5', available: 'java6' on Jar 'java6SecondJar','java7' on Jar 'java7SecondJar'",
                                                                                                                                    "Required flavor 'free' but no compatible binary was found",
                                                                                                                                    "Required buildType 'debug' but no compatible binary was found"]]
        [6]    | ['debug', 'release'] | ['free', 'paid'] | [6, 7] | [firstFreeDebugJar  : 'java6SecondJar',
                                                                     firstFreeReleaseJar: 'java6SecondJar',
                                                                     firstPaidDebugJar  : 'java6SecondJar',
                                                                     firstPaidReleaseJar: 'java6SecondJar'] | [:]

        and:
        outcome = errors ? 'fails' : 'succeeds'
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    @Unroll("matching Java #jdk1 with custom {jdk #jdk2, flavors #flavors2, builtTypes #buildTypes2} #outcome")
    def "building a standard JarBinarySpec instance and resolving against a library with custom binary types. "() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        def flavorsDSL = flavors2 ? 'flavors ' + flavors2.collect { "'$it'" }.join(',') : ''
        def buildTypesDSL = buildTypes2 ? 'buildTypes ' + buildTypes2.collect { "'$it'" }.join(',') : ''
        def javaVersionsDSL = "javaVersions ${jdk2.join(',')}"
        def targetPlatformsDSL = "${jdk1.collect { "targetPlatform 'java$it' " }.join('\n')}"

        // "selected" contains the information about the expected selected binaries
        // so we are going to build the "tasks" block which is going to make the assertions about
        // the selected component binary
        String tasksBlock = generateCheckDependenciesDSLBlockForJavaLibrary(selected, jdk1)

        buildFile << """

model {
    components {
        first(JvmLibrarySpec) {
            $targetPlatformsDSL
            sources {
                java {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {

            $javaVersionsDSL
            $flavorsDSL
            $buildTypesDSL

            sources {
                java(JavaSourceSet)
            }
        }
    }

    $tasksBlock
}
"""
        file('src/first/java/FirstApp.java') << 'public class FirstApp extends SecondApp {}'
        file('src/second/java/SecondApp.java') << 'public class SecondApp {}'

        expect:
        Set consumedErrors = []
        forEachJavaBinary(jdk1) { String taskName ->
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

        and:
        // sanity check to make sure that we use all errors defined in the spec, in case the name of tasks change due
        // to internal implementation changes or variant values changes
        errors.keySet() == consumedErrors

        where:
        jdk1   | buildTypes2          | flavors2         | jdk2   | selected                               | errors
        [6]    | []                   | []               | [6]    | [:]                                    | [:]
        [6]    | ['debug']            | ['free']         | [6, 7] | [firstJar: 'secondFreeDebug6Jar']      | [:]
        [6, 7] | ['debug']            | ['free']         | [6, 7] | [java6FirstJar: 'secondFreeDebug6Jar',
                                                                     java7FirstJar: 'secondFreeDebug7Jar'] | [:]
        [5, 6] | ['debug']            | ['free']         | [6, 7] | [java6FirstJar: 'secondFreeDebug6Jar'] | [java5FirstJar: ["Cannot find a compatible binary for library 'second' (Java SE 5). Available platforms: [Java SE 6, Java SE 7]"]]
        [6]    | ['debug', 'release'] | []               | [6, 7] | [:]                                    | [firstJar: ["Multiple binaries available for library 'second' (Java SE 6) :",
                                                                                                                         "Jar 'secondDefaultDebug6Jar'", "buildType 'debug'", "targetPlatform 'java6'",
                                                                                                                         "Jar 'secondDefaultRelease6Jar'", "buildType 'release'", "targetPlatform 'java6'"]]
        [6]    | ['debug', 'release'] | ['free', 'paid'] | [6, 7] | [:]                                    | [firstJar: ["Multiple binaries available for library 'second' (Java SE 6) :",
                                                                                                                         "Jar 'secondFreeDebug6Jar'", "buildType 'debug'", "flavor 'free'", "targetPlatform 'java6'",
                                                                                                                         "Jar 'secondFreeRelease6Jar'", "buildType 'release'", "flavor 'free'", "targetPlatform 'java6'",
                                                                                                                         "Jar 'secondPaidDebug6Jar'", "buildType 'debug'", "flavor 'paid'", "targetPlatform 'java6'",
                                                                                                                         "Jar 'secondPaidRelease6Jar'", "buildType 'release'", "flavor 'paid'", "targetPlatform 'java6'"]]

        and:
        outcome = errors ? 'fails' : 'succeeds'
    }

    private static void forEachJavaBinary(List<Integer> platforms, Closure calledWithTaskName) {
        if (platforms.size() == 1) {
            calledWithTaskName 'firstJar'
        } else {
            platforms.each { platform ->
                calledWithTaskName "java${platform}FirstJar"
            }
        }
    }

    private static void forEachCustomBinary(List<String> buildTypesToTest, List<String> flavorsToTest, List<Integer> jdksToTest, Closure calledWithTaskName) {
        buildTypesToTest.each { buildType ->
            flavorsToTest.each { flavor ->
                jdksToTest.each { jdk ->
                    String javaVersion = jdksToTest.size() > 1 ? "$jdk" : ''
                    calledWithTaskName "first${flavor.capitalize()}${buildType.capitalize()}${javaVersion}Jar"
                }
            }
        }
    }

    private static String generateCheckDependenciesDSLBlock(Map<String, String> selected, Closure loop) {
        def checkTasks = [:]
        def taskNames = []

        loop { taskName ->
            if (selected[taskName]) {
                def target = selected[taskName]
                checkTasks[taskName] = """
                $taskName {
                    doLast {
                        def t = $taskName
                        while (!(t instanceof PlatformJavaCompile)) {
                            t = t.taskDependencies.getDependencies(t)[0]
                        }
                        assert t.classpath.files == [file("\${buildDir}/jars/$target/second.jar")] as Set
                    }
                }
"""
            } else {
                taskNames << taskName
            }
        }

        if (checkTasks.keySet() != selected.keySet()) {
            throw new IllegalArgumentException("The following tasks are declared in the datatable 'selected' column but not found in the generated tasks: ${selected.keySet() - checkTasks.keySet()}. Possible solutions = $taskNames")
        }

        def tasksBlock = checkTasks ? """
            tasks {
                ${checkTasks.values().join('\n')}
            }
""" : ''
        tasksBlock
    }

    private static String generateCheckDependenciesDSLBlockForCustomComponent(Map<String, String> selected, List<String> buildTypesToTest, List<String> flavorsToTest, List<Integer> jdksToTest) {
        generateCheckDependenciesDSLBlock(selected, this.&forEachCustomBinary.curry(buildTypesToTest, flavorsToTest, jdksToTest))
    }

    private static String generateCheckDependenciesDSLBlockForJavaLibrary(Map<String, String> selected, List<Integer> jdksToTest) {
        generateCheckDependenciesDSLBlock(selected, this.&forEachJavaBinary.curry(jdksToTest))
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
