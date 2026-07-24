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

package org.gradle.internal.instrumentation.agent

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.classloader.InstrumentingClassLoader
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

/**
 * Verifies which of the two instrumentation paths the buildscript classpath ends up on, depending on the
 * agents attached to the build JVM.
 * <p>
 * Both paths are meant to be behaviorally equivalent, so no ordinary build output distinguishes them.
 * The probe therefore asks the buildscript classloader directly, via
 * {@link InstrumentingClassLoader#canReinstrumentClasses()}:
 * <ul>
 *     <li><b>substitution</b> - the artifact transform pre-instrumented the classpath and the classloader
 *     serves those bytes. This is what a regular build does.</li>
 *     <li><b>runtime transformation</b> - a third-party agent may rewrite bytecode, so Gradle re-instruments
 *     whatever the JVM supplies in order to compose with it.</li>
 * </ul>
 * The point of the exemptions in {@link AgentUtils} is that a debugging or profiling session stays on the
 * same path a regular build uses, so these tests are what keeps an exemption from silently regressing.
 */
@Requires(
    value = TestExecutionPreconditions.NotEmbeddedExecutor,
    reason = "The build runs in the test JVM under the embedded executor, so the agent switches under test cannot be applied to it"
)
class BuildScriptClasspathInstrumentationPathIntegrationTest extends AbstractIntegrationSpec {
    private static final String SUBSTITUTION = "substitution"
    private static final String RUNTIME_TRANSFORMATION = "runtime-transformation"
    private static final String NOT_INSTRUMENTING = "none"

    def setup() {
        executer.requireIsolatedDaemons()
        withInstrumentationPathProbe()
    }

    def "buildscript classpath uses the substitution path when no third-party agent is attached"() {
        when:
        succeeds("probe")

        then:
        instrumentationPathIs(SUBSTITUTION)
    }

    def "a third-party Java agent moves the buildscript classpath onto the runtime-transformation path"() {
        given:
        withThirdPartyJavaAgent()

        when:
        succeeds("probe")

        then:
        instrumentationPathIs(RUNTIME_TRANSFORMATION)
    }

    def "the JDWP debug agent keeps the buildscript classpath on the substitution path"() {
        given: "the debug agent attached the way an IDE attaches it"
        withJdwpAgent()

        when:
        succeeds("probe")

        then: "debugging exercises the same instrumentation a regular build does"
        instrumentationPathIs(SUBSTITUTION)
    }

    def "the JDWP debug agent alongside a third-party Java agent still uses the runtime-transformation path"() {
        given: "an exempt agent does not excuse a non-exempt one"
        withJdwpAgent()
        withThirdPartyJavaAgent()

        when:
        succeeds("probe")

        then:
        instrumentationPathIs(RUNTIME_TRANSFORMATION)
    }

    private void instrumentationPathIs(String expectedPath) {
        // Asserting the exact line rules out the probe silently reporting NOT_INSTRUMENTING, which would
        // otherwise make every expectation above vacuously true.
        outputContains("instrumentation path = $expectedPath")
    }

    private void withJdwpAgent() {
        // Port 0 lets the OS pick a free one, and the daemon does not wait for a debugger to attach.
        executer.withBuildJvmOpts("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:0")
    }

    private void withThirdPartyJavaAgent() {
        def builder = artifactBuilder()
        builder.sourceFile("TestAgent.java") << javaSnippet("""
            import java.lang.instrument.Instrumentation;

            public class TestAgent {
                public static void premain(String args, Instrumentation inst) {
                    // Registering no transformer is enough: the detection is based on the JVM arguments,
                    // because an agent can install a transformer at any point.
                }
            }
        """)
        builder.manifestAttributes("Premain-Class": "TestAgent")
        def agentJar = file("test-agent.jar")
        builder.buildJar(agentJar)
        executer.withBuildJvmOpts("-javaagent:${agentJar.absolutePath}")
    }

    private void withInstrumentationPathProbe() {
        // A buildSrc class is a project dependency on the buildscript classpath, so it goes through the
        // instrumenting resolver and is loaded by the classloader whose path we want to observe.
        file("buildSrc/src/main/java/test/Probe.java") << javaSnippet("""
            package test;

            public class Probe {
            }
        """)
        buildFile buildScriptSnippet("""
            import ${InstrumentingClassLoader.name}

            tasks.register("probe") {
                doLast {
                    def loader = test.Probe.classLoader
                    if (loader instanceof InstrumentingClassLoader) {
                        boolean canReinstrument = loader.canReinstrumentClasses()
                        println("instrumentation path = " + (canReinstrument ? "$RUNTIME_TRANSFORMATION" : "$SUBSTITUTION"))
                    } else {
                        println("instrumentation path = $NOT_INSTRUMENTING")
                    }
                }
            }
        """)
    }
}
