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

package org.gradle.api.internal.attributes

import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture

/**
 * Integration tests for {@code Number}-typed attribute values flowing through the
 * resolution-result streaming path ({@code DesugaringAttributeContainerSerializer}).
 * <p>
 * Only the request attributes of an <em>ad-hoc root</em> node (a detached configuration, or the
 * synthetic root of a resolvable configuration that has a real dependency, avoiding the
 * short-circuit executor) are serialized <em>raw</em> through this serializer. Module/project
 * node attributes are desugared to {@code String} and never hit the NUMBER branch. The
 * {@code write} branch is engaged at task-graph-time resolution and is exercised here. The
 * {@code read}/reconstruction branch ({@code resolveNumberType} + {@code parseNumber}), by
 * contrast, was empirically found <em>not</em> to be reachable from integration-test build shapes:
 * task-input snapshotting is write-only ({@code ResolutionResultSerializer.read} is unsupported),
 * execution-time {@code resolutionResult} queries use the in-memory graph, and build-operation
 * capture desugars values to their {@code toString} rather than re-streaming raw values. The read
 * path (including the cross-classloader failure for build-defined custom {@code Number} types) is
 * therefore covered by the unit test {@code CustomNumberDesugaringSerializerTest} instead.
 */
final class CustomNumberAttributeIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    private static final String CUSTOM_NUMBER = """
        class SemVer extends Number {
            final int v
            SemVer(int v) { this.v = v }
            static SemVer valueOf(String s) { new SemVer(Integer.parseInt(s)) }
            int intValue() { v }
            long longValue() { (long) v }
            float floatValue() { (float) v }
            double doubleValue() { (double) v }
            String toString() { String.valueOf(v) }
            boolean equals(Object o) { o instanceof SemVer && ((SemVer) o).v == v }
            int hashCode() { v }
        }
    """

    def "a JDK Number request attribute on a detached configuration used as a task input streams through the NUMBER write branch without regression"() {
        // Mirrors the ES 9.5.1 regression shape (EnumAttributeIntegrationTest), but with a Number
        // instead of a plain enum. The enum hit a CCE on write; a Number takes the NUMBER write
        // branch instead and proceeds cleanly.
        given:
        mavenRepo.module("org.example", "producer", "1.0").publish()

        buildFile("""
            def NUM = Attribute.of("myNumberAttribute", Long.class)

            repositories { maven { url = uri("${mavenRepo.uri}") } }

            def detached = configurations.detachedConfiguration(
                dependencies.create("org.example:producer:1.0")
            )
            detached.attributes { attribute(NUM, 9000000000L) }

            tasks.register("t") {
                inputs.files(detached)
                doLast { }
            }
        """)

        expect:
        succeeds("t")
    }

    def "a JDK Long request attribute on a resolvable ad-hoc root is captured in the resolve build operation"() {
        given:
        mavenRepo.module("org.example", "producer", "1.0").publish()

        buildFile("""
            def NUM = Attribute.of("myNumberAttribute", Long.class)

            repositories { maven { url = uri("${mavenRepo.uri}") } }

            configurations {
                dependencyScope("deps")
                resolvable("res") {
                    extendsFrom(configurations.deps)
                    attributes { attribute(NUM, 9000000000L) }
                }
            }

            dependencies { deps("org.example:producer:1.0") }

            tasks.register("resolve") {
                def files = configurations.res.incoming.files
                doLast { files.each { println("Resolved: " + it.name) } }
            }
        """)

        when:
        succeeds("resolve")

        then: "the request attribute reaches the build-op via desugaring to its toString (not the streaming read path)"
        def op = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        op.result.requestedAttributes.find { it.name == "myNumberAttribute" }.value.toString() == "9000000000"
    }

    def "an inline custom Number request attribute resolves end-to-end and is captured in the resolve build operation"() {
        // A build-script-defined custom Number is invisible to the serializer's core classloader,
        // so the streaming read()/reconstruction path CANNOT rebuild it (see finding #2, covered by
        // CustomNumberDesugaringSerializerTest). But resolution never engages that read path: the
        // value is desugared to its toString for the build-op/execution-time views. So resolution
        // succeeds cleanly here — documenting that the defect is latent, not hit by normal builds.
        given:
        mavenRepo.module("org.example", "producer", "1.0").publish()

        buildFile("""
            ${CUSTOM_NUMBER}
            def NUM = Attribute.of("myNumberAttribute", SemVer.class)

            repositories { maven { url = uri("${mavenRepo.uri}") } }

            configurations {
                dependencyScope("deps")
                resolvable("res") {
                    extendsFrom(configurations.deps)
                    attributes { attribute(NUM, SemVer.valueOf("7")) }
                }
            }

            dependencies { deps("org.example:producer:1.0") }

            tasks.register("resolve") {
                def files = configurations.res.incoming.files
                doLast { files.each { println("Resolved: " + it.name) } }
            }
        """)

        when:
        succeeds("resolve")

        then:
        def op = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        op.result.requestedAttributes.find { it.name == "myNumberAttribute" }.value.toString() == "7"
    }
}
