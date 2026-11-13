/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.groovy.compile.daemon

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import spock.lang.Issue

/**
 * Verifies that a Groovy compilation running in a reused compiler daemon sees only the system properties its task requested.
 */
@Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor, reason = "explicitly requiring a daemon")
class GroovyCompilerDaemonSystemPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.withWorkerDaemonsExpirationDisabled()
        executer.requireDaemon().requireIsolatedDaemons()
    }

    @Issue("https://github.com/gradle/gradle/issues/38395")
    def "reused compiler daemon only has the system properties requested via #method when consumer sets marker #consumerMarker"() {
        settingsFile << """
            include ":probe", ":producer", ":consumer"
        """

        file("probe/build.gradle") << """
            plugins {
                id("groovy")
            }

            dependencies {
                implementation localGroovy()
            }
        """

        // Global AST transformation recording the system property seen by each compilation
        file("probe/src/main/groovy/probe/ProbeTransform.groovy") << """
            package probe

            import org.codehaus.groovy.ast.ASTNode
            import org.codehaus.groovy.control.CompilePhase
            import org.codehaus.groovy.control.SourceUnit
            import org.codehaus.groovy.transform.ASTTransformation
            import org.codehaus.groovy.transform.GroovyASTTransformation

            import java.lang.management.ManagementFactory

            @GroovyASTTransformation(phase = CompilePhase.CONVERSION)
            class ProbeTransform implements ASTTransformation {
                void visit(ASTNode[] nodes, SourceUnit source) {
                    String logPath = System.getProperty("probe.log")
                    if (logPath != null) {
                        File logFile = new File(logPath)
                        logFile.parentFile.mkdirs()
                        logFile.withWriterAppend { writer ->
                            writer.println("process=" + ManagementFactory.getRuntimeMXBean().getName() + " compiling=" + new File(source.name).name + " repro.marker=" + System.getProperty("repro.marker"))
                        }
                    }
                }
            }
        """
        file("probe/src/main/resources/META-INF/services/org.codehaus.groovy.transform.ASTTransformation") << "probe.ProbeTransform"

        file("producer/build.gradle") << """
            plugins {
                id("groovy")
            }

            dependencies {
                implementation localGroovy()
                implementation project(":probe")
            }

            class FixedArgs implements CommandLineArgumentProvider {
                @Input List<String> args
                Iterable<String> asArguments() { args }
            }

            tasks.withType(GroovyCompile).configureEach { task ->
                options.fork = true
                def args = ["-Dprobe.log=\${file("../build/probe.log").absolutePath}".toString(), "-Drepro.marker=producer"]
                ${addForkArgs}
            }
        """
        file("producer/src/main/groovy/producer/Producer.groovy") << "package producer; class Producer {}"

        file("consumer/build.gradle") << """
            plugins {
                id("groovy")
            }

            dependencies {
                implementation localGroovy()
                implementation project(":probe")
            }

            class FixedArgs implements CommandLineArgumentProvider {
                @Input List<String> args
                Iterable<String> asArguments() { args }
            }

            tasks.withType(GroovyCompile).configureEach { task ->
                options.fork = true
                def args = ["-Dprobe.log=\${file("../build/probe.log").absolutePath}".toString()] + ${consumerArgs}
                ${addForkArgs}
                // Run after producer so that its compiler daemon is a reuse candidate
                mustRunAfter(":producer:compileGroovy")
            }
        """
        file("consumer/src/main/groovy/consumer/Consumer.groovy") << "package consumer; class Consumer {}"

        when:
        succeeds(":producer:compileGroovy", ":consumer:compileGroovy")

        then:
        def lines = file("build/probe.log").readLines()
        lines.collect { it.substring(it.indexOf(" ") + 1) }.sort() == [
            "compiling=Consumer.groovy repro.marker=${consumerMarker}".toString(),
            "compiling=Producer.groovy repro.marker=producer"
        ]

        and: "both compilations ran in the same compiler daemon"
        lines.collect { it.substring(0, it.indexOf(" ")) }.unique().size() == 1

        where:
        method                 | addForkArgs                                                                     | consumerMarker | consumerArgs
        "jvmArgumentProviders" | "task.options.forkOptions.jvmArgumentProviders.add(new FixedArgs(args: args))" | null           | "[]"
        "jvmArgumentProviders" | "task.options.forkOptions.jvmArgumentProviders.add(new FixedArgs(args: args))" | "consumer"     | '["-Drepro.marker=consumer"]'
        "jvmArgs"              | "task.options.forkOptions.jvmArgs.addAll(args)"                                 | null           | "[]"
        "jvmArgs"              | "task.options.forkOptions.jvmArgs.addAll(args)"                                 | "consumer"     | '["-Drepro.marker=consumer"]'
    }
}
