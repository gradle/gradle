/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.cc.impl.promo

import org.gradle.api.internal.ConfigurationCacheDegradationController
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import org.gradle.initialization.StartParameterBuildOptions.IsolatedProjectsOption
import org.gradle.internal.cc.impl.AbstractConfigurationCacheIntegrationTest
import org.gradle.process.ShellScript

import static org.gradle.integtests.fixtures.logging.ConfigurationCacheOutputNormalizer.PROMO_PREFIX

class ConfigurationCachePromoIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "shows promo message when build succeeds without giving explicit CC state"() {
        given:
        buildFile """
            tasks.register("greet") { doLast { println("Hello") } }
        """

        when:
        run("greet")

        then:
        postBuildOutputContains(PROMO_PREFIX)
    }


    def "shows no promo message if build fails at configuration time"() {
        given:
        buildFile """
            throw new RuntimeException("failed")

            tasks.register("run") {}
        """

        when:
        fails("run")

        then:
        // TODO(mlopatkin) post-build output scraping is broken for failed builds
        outputDoesNotContain(PROMO_PREFIX)
        postBuildOutputDoesNotContain(PROMO_PREFIX)
    }

    def "shows no promo message if build fails at execution time"() {
        given:
        buildFile """
            tasks.register("fail") { doLast { throw new UnsupportedOperationException("I must fail") } }
        """

        when:
        fails("fail")

        then:
        // TODO(mlopatkin) post-build output scraping is broken for failed builds
        outputDoesNotContain(PROMO_PREFIX)
        postBuildOutputDoesNotContain(PROMO_PREFIX)
    }

    def "shows promo message when running with isolated projects disabled in command-line"() {
        given:
        buildFile """
            tasks.register("greet") { doLast { println("Hello") } }
        """

        when:
        run("greet", "-D${IsolatedProjectsOption.PROPERTY_NAME}=false")

        then:
        postBuildOutputContains(PROMO_PREFIX)
    }

    def "shows promo message when running with isolated projects disabled in properties files"() {
        given:
        buildFile """
            tasks.register("greet") { doLast { println("Hello") } }
        """

        propertiesFile.text = "${IsolatedProjectsOption.PROPERTY_NAME}=false"

        when:
        run("greet")

        then:
        postBuildOutputContains(PROMO_PREFIX)
    }

    def "shows no promo message when #ccSwitch is given in command-line"() {
        given:
        buildFile """
            tasks.register("greet") { doLast { println("Hello") } }
        """

        when:
        run("greet", ccSwitch)

        then:
        postBuildOutputDoesNotContain(PROMO_PREFIX)

        where:
        ccSwitch << [
            "--${ConfigurationCacheOption.LONG_OPTION}",
            "--no-${ConfigurationCacheOption.LONG_OPTION}",
            "-D${ConfigurationCacheOption.DEPRECATED_PROPERTY_NAME}=false",
            "-D${ConfigurationCacheOption.DEPRECATED_PROPERTY_NAME}=true",
            "-D${ConfigurationCacheOption.PROPERTY_NAME}=false",
            "-D${ConfigurationCacheOption.PROPERTY_NAME}=true",
            "-D${IsolatedProjectsOption.PROPERTY_NAME}=true",
        ]
    }

    def "shows no promo message when #ccStateLine is given in properties file"() {
        given:
        buildFile """
            tasks.register("greet") { doLast { println("Hello") } }
        """

        propertiesFile.text = ccStateLine

        when:
        run("greet")

        then:
        postBuildOutputDoesNotContain(PROMO_PREFIX)

        where:
        ccStateLine << [
            "${ConfigurationCacheOption.PROPERTY_NAME}=false",
            "${ConfigurationCacheOption.PROPERTY_NAME}=true",
            "${IsolatedProjectsOption.PROPERTY_NAME}=true",
        ]
    }

    def "shows no promo message if configuration is not cc compatible"() {
        given:
        buildFile """
            gradle.taskGraph.beforeTask {
                println("I break CC contract")
            }

            tasks.register("greet") { doLast { println("Hello") } }
        """

        executer.noDeprecationChecks()

        when:
        run("greet")

        then:
        postBuildOutputDoesNotContain(PROMO_PREFIX)
    }

    def "shows no promo message if execution is not cc compatible"() {
        given:
        buildFile """
            tasks.register("greet") {
                doLast { task ->
                    println("Hello from " + task.project.name)
                }
            }
        """

        executer.noDeprecationChecks()

        when:
        run("greet")

        then:
        postBuildOutputDoesNotContain(PROMO_PREFIX)
    }

    def "shows no promo message if external process used at configuration time with #execMethod"() {
        given:
        def script = ShellScript.builder().printText("Hello").writeTo(testDirectory, "script")

        buildFile """
            interface Ops {
                @Inject ExecOperations getExecOps()
            }

            def execWithExecOperations() {
                def ops = objects.newInstance(Ops)
                ops.execOps.exec { commandLine(${ShellScript.cmdToVarargLiterals(script.commandLine)}) }
            }

            def execWithGroovyApi() {
                [${ShellScript.cmdToVarargLiterals(script.commandLine)}].execute().waitForProcessOutput(System.out, System.err)
            }

            $execMethod()

            tasks.register("greet") {}
        """
        when:
        run("greet")

        then:
        postBuildOutputDoesNotContain(PROMO_PREFIX)

        where:
        execMethod << ["execWithExecOperations", "execWithGroovyApi"]
    }

    def "shows promo message if external process used at execution time with #execMethod"() {
        given:
        def script = ShellScript.builder().printText("Hello").writeTo(testDirectory, "script")

        buildFile """
            interface Ops {
                @Inject ExecOperations getExecOps()
            }

            tasks.register("greet") {
                def ops = objects.newInstance(Ops)
                def holder = new Object() {
                    def execWithExecOperations() {
                        ops.execOps.exec { commandLine(${ShellScript.cmdToVarargLiterals(script.commandLine)}) }
                    }

                    def execWithGroovyApi(def unused) {
                        [${ShellScript.cmdToVarargLiterals(script.commandLine)}].execute().waitForProcessOutput(System.out, System.err)
                    }
                }
                doLast {
                    holder.$execMethod()
                }
            }
        """

        when:
        run("greet")

        then:
        postBuildOutputContains(PROMO_PREFIX)

        where:
        execMethod << ["execWithExecOperations", "execWithGroovyApi"]
    }

    def "shows promo message if external process used by build logic build with #execMethod"() {
        given:
        def script = ShellScript.builder().printText("Hello").writeTo(testDirectory, "script")

        buildFile("buildSrc/build.gradle", """
            interface Ops {
                @Inject ExecOperations getExecOps()
            }

            tasks.register("greet") {
                def ops = objects.newInstance(Ops)
                def holder = new Object() {
                    def execWithExecOperations() {
                        ops.execOps.exec { commandLine(${ShellScript.cmdToVarargLiterals(script.commandLine)}) }
                    }

                    def execWithGroovyApi(def unused) {
                        [${ShellScript.cmdToVarargLiterals(script.commandLine)}].execute().waitForProcessOutput(System.out, System.err)
                    }
                }
                doLast {
                    holder.$execMethod()
                }
            }

            tasks.named("jar") {
                dependsOn("greet")
            }
        """)

        buildFile """
            tasks.register("run") {}
        """

        when:
        run("run")

        then:
        outputContains("Hello")
        postBuildOutputContains(PROMO_PREFIX)

        where:
        execMethod << ["execWithExecOperations", "execWithGroovyApi"]
    }

    def "shows promo message if configuration-only calls are used correctly"() {
        given:
        buildFile """
            tasks.register("greet") { task ->
                def myName = task.project.name
                doLast {
                    println("Hello from " + myName)
                }
            }
        """

        when:
        run("greet")

        then:
        postBuildOutputContains(PROMO_PREFIX)
    }

    def "shows no promo message if a task in the graph is marked as cc incompatible"() {
        given:
        buildFile """
            tasks.register("incompatible") { task ->
                $incompatibleReason
                onlyIf { !Boolean.getBoolean("skip.incompatible") }

                doLast {
                    println("I am not compatible")
                }
            }

            tasks.register("withIncompatibleDep") {
                dependsOn ":incompatible"
                doLast { println "I am compatible" }
            }
        """

        when: "incompatible task runs"
        run("incompatible")

        then:
        postBuildOutputDoesNotContain(PROMO_PREFIX)

        when: "incompatible task runs as dependency"
        run("withIncompatibleDep")

        then:
        postBuildOutputDoesNotContain(PROMO_PREFIX)

        when: "incompatible task is a skipped dependency"
        run("withIncompatibleDep", "-Dskip.incompatible=true")

        then:
        postBuildOutputDoesNotContain(PROMO_PREFIX)

        when: "promo is present when incompatible task is excluded from the task graph"
        run("withIncompatibleDep", "-x", "incompatible")

        then:
        postBuildOutputContains(PROMO_PREFIX)

        where:
        incompatibleReason << [
            "notCompatibleWithConfigurationCache('reasons')",
            "services.get(${ConfigurationCacheDegradationController.name}).requireConfigurationCacheDegradation(task, provider { 'reasons' })"
        ]
    }

    def "shows promo message if an incompatible task is in the build logic build"() {
        given:
        buildFile("buildSrc/build.gradle", """
            tasks.named("jar") { task ->
                $incompatibleReason
            }
        """)
        buildFile """
            tasks.register("run") {}
        """

        when:
        run("run")

        then:
        postBuildOutputContains(PROMO_PREFIX)

        where:
        incompatibleReason << [
            "notCompatibleWithConfigurationCache('reasons')",
            "services.get(${ConfigurationCacheDegradationController.name}).requireConfigurationCacheDegradation(task, provider { 'reasons' })"
        ]
    }
}
