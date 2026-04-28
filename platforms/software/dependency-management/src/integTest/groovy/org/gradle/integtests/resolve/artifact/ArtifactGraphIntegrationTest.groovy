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

package org.gradle.integtests.resolve.artifact

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

class ArtifactGraphIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def setup() {
        buildFile << """
            import java.util.function.Consumer
            import java.util.Deque
            import java.util.ArrayDeque
            import java.util.Set
            import java.util.HashSet

            import org.gradle.api.internal.artifacts.result.artifact.ArtifactNode
            import org.gradle.api.internal.artifacts.result.artifact.ArtifactEdge
            import org.gradle.api.internal.artifacts.result.artifact.ResolvedArtifactEdge
            import org.gradle.api.internal.artifacts.result.artifact.UnresolvedArtifactEdge
        """
    }

    def printNode(String expression) {
        """
            ${expression}.with { node ->
                println(node.id.displayName + " " + node.files*.name + " " + node.artifacts.artifactFiles*.name)
            }
        """
    }

    def printUnresolvedEdge(String expression) {
        """
            ${expression}.with { edge ->
                println(edge.requested.displayName + " " + edge.failure.message.replace("\\n", " "))
            }
        """
    }

    String getCommon() {
        """

            static void walk(ArtifactNode node, Consumer<ArtifactNode> visitor) {
                Set<ArtifactNode> seen = new HashSet<>()
                Deque<ArtifactNode> queue = new ArrayDeque<>()

                seen.add(node)
                queue.push(node)

                while (!queue.empty) {
                    node = queue.pop()
                    visitor.accept(node)
                    for (ArtifactEdge edge : node.dependencies) {
                        if (edge instanceof ResolvedArtifactEdge) {
                            def target = edge.targetNode
                            if (seen.add(target)) {
                                queue.push(target)
                            }
                        }
                    }
                }
            }
        """
    }

    def "can handle cycles in the graph"() {
        def foo = mavenRepo.module("org", "foo")
        def bar = mavenRepo.module("org", "bar").dependsOn(foo).publish()
        foo.dependsOn(bar).publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("org:foo:1.0")
            }

            ${common}

            def graph = configurations.runtimeClasspath.incoming.artifactView {}.artifactGraph

            tasks.register("resolve") {
                def foo = graph.root.map { it.dependencies.first().targetNode }
                def bar = foo.map { it.dependencies.first().targetNode }
                def barFoo = bar.map { it.dependencies.first().targetNode }
                doLast {
                    ${printNode("foo.get()")}
                    ${printNode("bar.get()")}
                    ${printNode("barFoo.get()")}
                    assert foo.get().is(barFoo.get())
                }
            }
        """

        when:
        succeeds("resolve")

        then:
        outputContains("""
org:foo:1.0 (runtime) [foo-1.0.jar] [foo-1.0.jar]
org:bar:1.0 (runtime) [bar-1.0.jar] [bar-1.0.jar]
org:foo:1.0 (runtime) [foo-1.0.jar] [foo-1.0.jar]
""")
    }

    def "handles unresolved edges"() {
        def bar = mavenHttpRepo.module("org", "bar")
        def baz = mavenHttpRepo.module("org", "baz").publish().allowAll()
        mavenHttpRepo.module("org", "foo").dependsOn(bar).dependsOn(baz).publish().allowAll()

        bar.pom.allowGetOrHead()

        buildFile << """
            plugins {
                id("java-library")
            }

            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
            }

            dependencies {
                implementation("org:foo:1.0")
            }

            ${common}

            def graph = configurations.runtimeClasspath.incoming.artifactView {}.artifactGraph

            tasks.register("resolve") {
                def foo = graph.root.map { it.dependencies.first().targetNode }
                def unresolvedBar = foo.map { it.dependencies[0] }
                def baz = foo.map { it.dependencies[1].targetNode }
                doLast {
                    ${printUnresolvedEdge("unresolvedBar.get()")}
                    ${printNode("baz.get()")}
                }
            }
        """

        when:
        succeeds("resolve")

        then:
        outputContains("""
org:bar:1.0 Could not find org:bar:1.0. Searched in the following locations:   - ${bar.pom.uri}
org:baz:1.0 (runtime) [baz-1.0.jar] [baz-1.0.jar]
""")
    }

    def "can resolves subset of nodes artifacts"() {
        def baz = mavenHttpRepo.module("org", "baz").publish()
        def bar = mavenHttpRepo.module("org", "bar").dependsOn(baz).publish()
        def foo = mavenHttpRepo.module("org", "foo").dependsOn(bar).publish()

        foo.pom.allowGetOrHead()
        bar.pom.allowGetOrHead()
        baz.pom.allowGetOrHead()
        baz.artifact.allowGetOrHead()

        buildFile << """
            plugins {
                id("java-library")
            }

            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
            }

            dependencies {
                implementation("org:foo:1.0")
            }

            ${common}

            def graph = configurations.runtimeClasspath.incoming.artifactView {}.artifactGraph

            tasks.register("resolve") {
                def foo = graph.root.map { it.dependencies.first().targetNode }
                def bar = foo.map { it.dependencies.first().targetNode }
                def baz = bar.map { it.dependencies.first().targetNode }
                doLast {
                    ${printNode("baz.get()")}
                }
            }
        """

        when:
        succeeds("resolve")

        then:
        outputContains("org:baz:1.0 (runtime) [baz-1.0.jar] [baz-1.0.jar]")
    }

    def "can add build dependencies for subset of node artifacts"() {
        settingsFile << """
            include(":foo")
            include(":bar")
            include(":baz")
        """
        [file("bar/build.gradle"), file("baz/build.gradle")].each {
            it << """
                plugins {
                    id("java-library")
                }
            """
        }
        file("foo/build.gradle") << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(project(":bar"))
            }
        """

        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(project(":foo"))
                implementation(project(":baz"))
            }

            def graph = configurations.runtimeClasspath.incoming.artifactView {}.artifactGraph

            tasks.register("resolve") {
                def foo = graph.root.map { it.dependencies[0].targetNode }
                def baz = foo.map { it.dependencies[0].targetNode }
                def bar = graph.root.map { it.dependencies[1].targetNode }
                dependsOn(baz.map { it.files })
                dependsOn(bar.map { it.files })
            }
        """

        when:
        succeeds(":resolve")

        then:
        executed(":baz:jar")
        executed(":bar:jar")
        notExecuted(":foo:jar")
    }

    def "can resolve file collection from subset of graph using spec"() {
        def qux = mavenHttpRepo.module("org", "qux").publish()
        def baz = mavenHttpRepo.module("org", "baz").publish()
        def bar = mavenHttpRepo.module("org", "bar").dependsOn(baz).dependsOn(qux).publish()
        def foo = mavenHttpRepo.module("org", "foo").dependsOn(bar).publish()

        foo.pom.allowGetOrHead()
        bar.pom.allowGetOrHead()
        baz.pom.allowGetOrHead()
        qux.pom.allowGetOrHead()
        bar.artifact.allowGetOrHead()
        qux.artifact.allowGetOrHead()

        buildFile << """
            plugins {
                id("java-library")
            }

            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
            }

            dependencies {
                implementation("org:foo:1.0")
            }

            ${common}

            def graph = configurations.runtimeClasspath.incoming.artifactView {}.artifactGraph

            tasks.register("resolve") {
                def files = graph.getFilesFromRoot { root ->
                    def nodes = []
                    walk(root) { node ->
                        def componentId = node.id.componentId
                        if (componentId instanceof ModuleComponentIdentifier && (componentId.module == "bar" || componentId.module == "qux")) {
                            nodes.add(node)
                        }
                    }
                    nodes
                }
                doLast {
                    println(files*.name)
                }
            }
        """

        when:
        succeeds("resolve")

        then:
        outputContains("[bar-1.0.jar, qux-1.0.jar]")
    }

    def "def can resolve single node from component with multiple nodes in the graph"() {
        settingsFile << """
            include(":other")
        """
        file("other/build.gradle") << """
            plugins {
                id("java-library")
                id("java-test-fixtures")
            }
        """
        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(testFixtures(project(":other")))
            }

            ${common}

            def graph = configurations.runtimeClasspath.incoming.artifactView {}.artifactGraph

            def otherFiles = graph.getFiles { node ->
                def componentId = node.id.componentId
                if (componentId instanceof ProjectComponentIdentifier && componentId.projectPath == ":other") {
                    return node.capabilities.any { it.name == "other" }
                }
                return false
            }
            def otherTestFixturesFiles = graph.getFiles { node ->
                def componentId = node.id.componentId
                if (componentId instanceof ProjectComponentIdentifier && componentId.projectPath == ":other") {
                    return node.capabilities.any { it.name == "other-test-fixtures" }
                }
                return false
            }

            tasks.register("resolveOther") {
                dependsOn(otherFiles)
                doLast {
                    println(otherFiles*.name)
                }
            }

            tasks.register("resolveOtherTestFixtures") {
                dependsOn(otherTestFixturesFiles)
                doLast {
                    println(otherTestFixturesFiles*.name)
                }
            }
        """

        when:
        succeeds("resolveOther")

        then:
        outputContains("[other.jar]")
        outputDoesNotContain("[other-test-fixtures.jar]")
        executed(":other:jar")
        notExecuted(":other:testFixturesJar")

        when:
        succeeds(":resolveOtherTestFixtures")

        then:
        outputDoesNotContain("[other.jar]")
        outputContains("[other-test-fixtures.jar]")
        executed(":other:testFixturesJar")
        notExecuted(":other:jar")
    }

}
