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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

class SingleBinaryTypeWithVariantsTest extends VariantAwareDependencyResolutionSpec {

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
        first(FlavorAndBuildTypeAwareLibrary) {

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
        second(FlavorAndBuildTypeAwareLibrary) {

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

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        expect:
        Set consumedErrors = []
        forEachFlavorAndBuildTypeBinary(buildTypesToTest, flavorsToTest, jdk1) { String taskName ->
            checkResolution(errors, consumedErrors, taskName)
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
        [6]  | ['debug']   | ['free']         | [7, 6, 5] | ['debug']            | ['free']          | [firstFreeDebugJar: 'secondFreeDebug6Jar']          | [:]
        [7]  | ['debug']   | ['free']         | [5, 6]    | ['debug']            | ['free']          | [firstFreeDebugJar: 'secondFreeDebug6Jar']          | [:]
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
        first(FlavorAndBuildTypeAwareLibrary) {

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
        forEachFlavorAndBuildTypeBinary(buildTypesToTest, flavorsToTest, jdk1) { String taskName ->
            checkResolution(errors, consumedErrors, taskName)
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
        second(FlavorAndBuildTypeAwareLibrary) {

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
            checkResolution(errors, consumedErrors, taskName)
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

    @Requires(TestPrecondition.JDK7_OR_LATER)
    @Unroll("matching custom1 {jdk #jdk1, flavors #flavors) with custom2 {jdk #jdk2, builtTypes #buildTypes} #outcome")
    def "building a custom JarBinarySpec type and resolving against a library with a different custom JarBinarySpec type"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        def flavorsDSL = flavors ? 'flavors ' + flavors.collect { "'$it'" }.join(',') : ''
        def buildTypesDSL = buildTypes ? 'buildTypes ' + buildTypes.collect { "'$it'" }.join(',') : ''
        def javaVersionsDSL1 = "javaVersions ${jdk1.join(',')}"
        def javaVersionsDSL2 = "javaVersions ${jdk2.join(',')}"

        // "selected" contains the information about the expected selected binaries
        // so we are going to build the "tasks" block which is going to make the assertions about
        // the selected component binary
        def flavorsToTest = flavors ?: ['default']
        String tasksBlock = generateCheckDependenciesDSLBlockForFlavorLibrary(selected, flavorsToTest, jdk1)

        buildFile << """

model {
    components {
        first(FlavorOnlyLibrary) {

            $javaVersionsDSL1
            $flavorsDSL

            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }

        second(BuildTypeOnlyLibrary) {

            $javaVersionsDSL2
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
        forEachFlavor(flavorsToTest, jdk1) { String taskName ->
            checkResolution(errors, consumedErrors, taskName)
        }

        and:
        // sanity check to make sure that we use all errors defined in the spec, in case the name of tasks change due
        // to internal implementation changes or variant values changes
        errors.keySet() == consumedErrors

        where:
        jdk1   | flavors          | buildTypes           | jdk2   | selected                           | errors
        [6]    | []               | []                   | [6]    | [:]                                | [:]
        [6]    | ['free']         | ['debug']            | [6, 7] | [firstFreeJar: 'secondDebug6Jar']  | [:]
        [6, 7] | ['free']         | ['debug']            | [6, 7] | [firstFree6Jar: 'secondDebug6Jar',
                                                                     firstFree7Jar: 'secondDebug7Jar'] | [:]
        [5, 6] | ['free']         | ['debug']            | [6, 7] | [firstFree6Jar: 'secondDebug6Jar'] | [firstFree5Jar: ["Cannot find a compatible binary for library 'second' (Java SE 5)",
                                                                                                                          "Required platform 'java5', available: 'java6' on Jar 'secondDebug6Jar','java7' on Jar 'secondDebug7Jar'",
                                                                                                                          "Required flavor 'free' but no compatible binary was found"]]
        [6]    | []               | ['debug', 'release'] | [6, 7] | [:]                                | [firstDefaultJar: ["Multiple binaries available for library 'second' (Java SE 6) :",
                                                                                                                            "Jar 'secondDebug6Jar'", "buildType 'debug'", "targetPlatform 'java6'",
                                                                                                                            "Jar 'secondRelease6Jar'", "buildType 'release'", "targetPlatform 'java6'"]]
        [6]    | ['free', 'paid'] | ['debug', 'release'] | [6, 7] | [:]                                | [firstFreeJar: ["Multiple binaries available for library 'second' (Java SE 6) :",
                                                                                                                         "Jar 'secondDebug6Jar'", "buildType 'debug'", "targetPlatform 'java6'",
                                                                                                                         "Jar 'secondRelease6Jar'", "buildType 'release'", "targetPlatform 'java6'"],
                                                                                                          firstPaidJar: ["Multiple binaries available for library 'second' (Java SE 6) :",
                                                                                                                         "Jar 'secondDebug6Jar'", "buildType 'debug'", "targetPlatform 'java6'",
                                                                                                                         "Jar 'secondRelease6Jar'", "buildType 'release'", "targetPlatform 'java6'"]]

        and:
        outcome = errors ? 'fails' : 'succeeds'
    }

}
