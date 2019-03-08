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

import org.gradle.api.JavaVersion
import org.junit.Assume
import spock.lang.Unroll

import static org.gradle.language.java.JavaIntegrationTesting.applyJavaPlugin

class SingleBinaryTypeWithVariantsTest extends VariantAwareDependencyResolutionSpec {

    @Unroll("matching {jdk #jdk1, flavors #flavors1, builtTypes #buildTypes1} with {jdk #jdk2, flavors #flavors2, buildTypes #buildTypes2} #outcome")
    def "check resolution of a custom component onto a component of the same type (same class, same variant dimensions)"() {
        given:
        Assume.assumeTrue(jdk1.max() <= Integer.valueOf(JavaVersion.current().majorVersion))
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
        jdk1 | buildTypes1 | flavors1         | jdk2      | buildTypes2          | flavors2          | selected                                             | errors
        [7]  | []          | []               | [7]       | []                   | []                | [:]                                                  | [:]
        [7]  | []          | []               | [7]       | []                   | []                | [firstDefaultDefaultJar: 'second/defaultDefaultJar'] | [:]
        [7]  | []          | []               | [7]       | []                   | ['paid']          | [firstDefaultDefaultJar: 'second/paidDefaultJar']    | [:]
        [7]  | []          | []               | [7]       | []                   | ['paid', 'free']  | [:]                                                  | [firstDefaultDefaultJar: ["Multiple compatible variants found for library 'second':",
                                                                                                                                                                                        "Jar 'second:freeDefaultJar' [flavor:'free', platform:'java7']",
                                                                                                                                                                                        "Jar 'second:paidDefaultJar' [flavor:'paid', platform:'java7']"]]
        [7]  | ['release'] | []               | [7]       | ['debug']            | []                | [:]                                                  | [firstDefaultReleaseJar: ["Cannot find a compatible variant for library 'second'.",
                                                                                                                                                                                        "Required platform 'java7', available: 'java7'",
                                                                                                                                                                                        "Required buildType 'release', available: 'debug'"]]
        [7]  | []          | []               | [7]       | ['release', 'debug'] | ['paid', 'free']  | [:]                                                  | [firstDefaultDefaultJar: ["Multiple compatible variants found for library 'second':",
                                                                                                                                                                                        "Jar 'second:freeDebugJar' [buildType:'debug', flavor:'free', platform:'java7']",
                                                                                                                                                                                        "Jar 'second:freeReleaseJar' [buildType:'release', flavor:'free', platform:'java7']",
                                                                                                                                                                                        "Jar 'second:paidDebugJar' [buildType:'debug', flavor:'paid', platform:'java7']",
                                                                                                                                                                                        "Jar 'second:paidReleaseJar' [buildType:'release', flavor:'paid', platform:'java7']"]]
        [7]  | []          | ['paid']         | [7]       | []                   | ['paid']          | [firstPaidDefaultJar: 'second/paidDefaultJar']       | [:]
        [7]  | []          | ['paid', 'free'] | [7]       | []                   | ['paid', 'free']  | [firstFreeDefaultJar: 'second/freeDefaultJar',
                                                                                                        firstPaidDefaultJar: 'second/paidDefaultJar']       | [:]
        [7]  | ['debug']   | ['free']         | [7]       | ['debug', 'release'] | ['free']          | [firstFreeDebugJar: 'second/freeDebugJar']           | [:]
        [7]  | ['debug']   | ['free']         | [7]       | ['debug']            | ['free', 'paid']  | [firstFreeDebugJar: 'second/freeDebugJar']           | [:]
        [8]  | ['debug']   | ['free']         | [7, 8, 9] | ['debug']            | ['free']          | [firstFreeDebugJar: 'second/freeDebug8Jar']          | [:]
        [8]  | ['debug']   | ['free']         | [9, 8, 7] | ['debug']            | ['free']          | [firstFreeDebugJar: 'second/freeDebug8Jar']          | [:]
        [9]  | ['debug']   | ['free']         | [7, 8]    | ['debug']            | ['free']          | [firstFreeDebugJar: 'second/freeDebug8Jar']          | [:]
        [7]  | ['debug']   | ['free']         | [8]       | ['debug']            | ['free']          | [:]                                                  | [firstFreeDebugJar: ["Cannot find a compatible variant for library 'second'",
                                                                                                                                                                                   "Required platform 'java7', available: 'java8'",
                                                                                                                                                                                   "Required flavor 'free', available: 'free'",
                                                                                                                                                                                   "Required buildType 'debug', available: 'debug'"]]
        [7]  | []          | ['paid']         | [7]       | []                   | ['free']          | [:]                                                  | [firstPaidDefaultJar: ["Cannot find a compatible variant for library 'second'",
                                                                                                                                                                                     "Required flavor 'paid', available: 'free'"]]
        [7]  | []          | ['paid']         | [7]       | []                   | ['free', 'other'] | [:]                                                  | [firstPaidDefaultJar: ["Cannot find a compatible variant for library 'second'",
                                                                                                                                                                                     "Required flavor 'paid', available: 'free', 'other'"]]
        [7]  | []          | ['paid', 'free'] | [7]       | []                   | ['free', 'other'] | [:]                                                  | [firstPaidDefaultJar: ["Cannot find a compatible variant for library 'second'",
                                                                                                                                                                                     "Required flavor 'paid', available: 'free', 'other'"]]
        [7]  | []          | ['paid', 'test'] | [7]       | []                   | ['free', 'other'] | [:]                                                  | [firstPaidDefaultJar: ["Cannot find a compatible variant for library 'second'",
                                                                                                                                                                                     "Required flavor 'paid', available: 'free', 'other'"],
                                                                                                                                                              firstTestDefaultJar: ["Cannot find a compatible variant for library 'second'",
                                                                                                                                                                                    "Required flavor 'test', available: 'free', 'other'"]]
        and:
        outcome = errors ? 'fails' : 'succeeds'
    }

    @Unroll("matching custom {jdk #jdk1, flavors #flavors1, builtTypes #buildTypes1} with Java {jdk #jdk2} #outcome")
    def "building a custom binary type and resolving against a library with standard JarBinarySpec instances"() {
        given:
        Assume.assumeTrue(jdk1.max() <= Integer.valueOf(JavaVersion.current().majorVersion))
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
        [7]    | []                   | []               | [7]    | [:]                                     | [:]
        [7]    | ['debug']            | ['free']         | [7, 8] | [firstFreeDebugJar: 'second/java7Jar']   | [:]
        [8, 9] | ['debug']            | ['free']         | [8, 9] | [firstFreeDebug8Jar: 'second/java8Jar',
                                                                     firstFreeDebug9Jar: 'second/java9Jar']  | [:]
        [7, 8] | ['debug']            | ['free']         | [8, 9] | [firstFreeDebug8Jar: 'second/java8Jar']  | [firstFreeDebug7Jar: ["Cannot find a compatible variant for library 'second'",
                                                                                                                                    "Required platform 'java7', available: 'java8', 'java9'",
                                                                                                                                    "Required flavor 'free' but no compatible variant was found",
                                                                                                                                    "Required buildType 'debug' but no compatible variant was found"]]
        [7]    | ['debug', 'release'] | ['free', 'paid'] | [7, 8] | [firstFreeDebugJar  : 'second/java7Jar',
                                                                     firstFreeReleaseJar: 'second/java7Jar',
                                                                     firstPaidDebugJar  : 'second/java7Jar',
                                                                     firstPaidReleaseJar: 'second/java7Jar'] | [:]

        and:
        outcome = errors ? 'fails' : 'succeeds'
    }

    @Unroll("matching Java #jdk1 with custom {jdk #jdk2, flavors #flavors2, builtTypes #buildTypes2} #outcome")
    def "building a standard JarBinarySpec instance and resolving against a library with custom binary types. "() {
        given:
        Assume.assumeTrue(jdk1.max() <= Integer.valueOf(JavaVersion.current().majorVersion))
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
        jdk1   | buildTypes2          | flavors2         | jdk2   | selected                                | errors
        [7]    | []                   | []               | [7]    | [:]                                     | [:]
        [7]    | ['debug']            | ['free']         | [7, 8] | [firstJar: 'second/freeDebug7Jar']      | [:]
        [8, 9] | ['debug']            | ['free']         | [8, 9] | [firstJava8Jar: 'second/freeDebug8Jar',
                                                                     firstJava9Jar: 'second/freeDebug9Jar'] | [:]
        [7, 8] | ['debug']            | ['free']         | [8, 9] | [firstJava8Jar: 'second/freeDebug8Jar'] | [firstJava7Jar: ["Cannot find a compatible variant for library 'second'.",
                                                                                                                               "Required platform 'java7', available: 'java8', 'java9'"]]
        [7]    | ['debug', 'release'] | []               | [7, 8] | [:]                                     | [firstJar: ["Multiple compatible variants found for library 'second':",
                                                                                                                          "Jar 'second:defaultDebug7Jar' [buildType:'debug', platform:'java7']",
                                                                                                                          "Jar 'second:defaultRelease7Jar' [buildType:'release', platform:'java7']"]]
        [7]    | ['debug', 'release'] | ['free', 'paid'] | [7, 8] | [:]                                     | [firstJar: ["Multiple compatible variants found for library 'second':",
                                                                                                                          "Jar 'second:freeDebug7Jar' [buildType:'debug', flavor:'free', platform:'java7']",
                                                                                                                          "Jar 'second:freeRelease7Jar' [buildType:'release', flavor:'free', platform:'java7']",
                                                                                                                          "Jar 'second:paidDebug7Jar' [buildType:'debug', flavor:'paid', platform:'java7']",
                                                                                                                          "Jar 'second:paidRelease7Jar' [buildType:'release', flavor:'paid', platform:'java7']"]]

        and:
        outcome = errors ? 'fails' : 'succeeds'
    }

    @Unroll("matching custom1 {jdk #jdk1, flavors #flavors) with custom2 {jdk #jdk2, builtTypes #buildTypes} #outcome")
    def "building a custom JarBinarySpec type and resolving against a library with a different custom JarBinarySpec type"() {
        given:
        Assume.assumeTrue(jdk1.max() <= Integer.valueOf(JavaVersion.current().majorVersion))
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
        jdk1   | flavors          | buildTypes           | jdk2   | selected                            | errors
        [7]    | []               | []                   | [7]    | [:]                                 | [:]
        [7]    | ['free']         | ['debug']            | [7, 8] | [firstFreeJar: 'second/debug7Jar']  | [:]
        [8, 9] | ['free']         | ['debug']            | [8, 9] | [firstFree8Jar: 'second/debug8Jar',
                                                                     firstFree9Jar: 'second/debug9Jar'] | [:]
        [7, 8] | ['free']         | ['debug']            | [8, 9] | [firstFree8Jar: 'second/debug8Jar'] | [firstFree7Jar: ["Cannot find a compatible variant for library 'second'",
                                                                                                                           "Required platform 'java7', available: 'java8', 'java9'",
                                                                                                                           "Required flavor 'free' but no compatible variant was found"]]
        [7]    | []               | ['debug', 'release'] | [7, 8] | [:]                                 | [firstDefaultJar: ["Multiple compatible variants found for library 'second':",
                                                                                                                             "Jar 'second:debug7Jar' [buildType:'debug', platform:'java7']",
                                                                                                                             "Jar 'second:release7Jar' [buildType:'release', platform:'java7']"]]
        [7]    | ['free', 'paid'] | ['debug', 'release'] | [7, 8] | [:]                                 | [firstFreeJar: ["Multiple compatible variants found for library 'second':",
                                                                                                                          "Jar 'second:debug7Jar' [buildType:'debug', platform:'java7']",
                                                                                                                          "Jar 'second:release7Jar' [buildType:'release', platform:'java7']"],
                                                                                                           firstPaidJar: ["Multiple compatible variants found for library 'second':",
                                                                                                                          "Jar 'second:debug7Jar' [buildType:'debug', platform:'java7']",
                                                                                                                          "Jar 'second:release7Jar' [buildType:'release', platform:'java7']"]]

        and:
        outcome = errors ? 'fails' : 'succeeds'
    }

}
