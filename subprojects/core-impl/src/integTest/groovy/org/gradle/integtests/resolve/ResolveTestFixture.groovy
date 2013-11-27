/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.test.fixtures.file.TestFile

/**
 * A test fixture that injects a task into a build that resolves a dependency configuration and does some validation of the resulting graph, to
 * ensure that the old and new dependency graphs plus the artifacts and files are as expected and well-formed.
 */
class ResolveTestFixture {
    private final TestFile buildFile
    private final String config

    ResolveTestFixture(TestFile buildFile, String config = "compile") {
        this.config = config
        this.buildFile = buildFile
    }

    /**
     * Injects the appropriate stuff into the build script.
     */
    void prepare() {
        buildFile << """
buildscript {
    dependencies.classpath files("${ClasspathUtil.getClasspathForClass(GenerateGraphTask).toURI()}")
}

allprojects {
    tasks.addPlaceholderAction("checkDeps") {
        tasks.create(name: "checkDeps", dependsOn: configurations.${config}, type: ${GenerateGraphTask.name}) {
            outputFile = rootProject.file("\${rootProject.buildDir}/${config}.txt")
            configuration = configurations.$config
        }
    }
}
"""
    }

    /**
     * Verifies the result of executing the task injected by {@link #prepare()}. The closure delegates to a {@link GraphBuilder} instance.
     */
    void expectGraph(Closure closure) {
        def graph = new GraphBuilder()
        closure.resolveStrategy = Closure.DELEGATE_ONLY
        closure.delegate = graph
        closure.call()

        if (graph.root == null) {
            throw new IllegalArgumentException("No root node defined")
        }

        def configDetailsFile = buildFile.parentFile.file("build/${config}.txt")
        def configDetails = configDetailsFile.text.readLines()

        println "VALIDATING"
        println(configDetailsFile.text)

        def actualArtifacts = configDetails.findAll { it.startsWith('artifact:') }.collect { it.substring(9) }
        def expectedArtifacts = graph.artifactNodes.collect { "[${it.id}][${it.module}.jar]" }
        assert actualArtifacts == expectedArtifacts

        def actualFiles = configDetails.findAll { it.startsWith('file:') }.collect { it.substring(5) }
        def expectedFiles = graph.artifactNodes.collect { it.fileName }
        assert actualFiles == expectedFiles

        def actualFirstLevel = configDetails.findAll { it.startsWith('first-level:') }.collect { it.substring(12) }
        def expectedFirstLevel = graph.root.deps.collect { "[${it.selected.id}:default]" }
        assert actualFirstLevel == expectedFirstLevel

        def actualRoot = configDetails.find { it.startsWith('root:') }.substring(5)
        def expectedRoot = "[${graph.root.id}][${graph.root.reason}]"
        assert actualRoot == expectedRoot

        def actualNodes = configDetails.findAll { it.startsWith('module-version:') }.collect { it.substring(15) }
        def expectedNodes = graph.nodes.values().collect { "[${it.id}][${it.reason}]" }
        assert actualNodes == expectedNodes

        def actualEdges = configDetails.findAll { it.startsWith('dependency:') }.collect { it.substring(11) }
        def expectedEdges = graph.edges.collect { "[${it.from.id}][${it.requested}->${it.selected.id}]" }
        assert actualEdges == expectedEdges
    }

    public static class GraphBuilder {
        final Map<String, NodeBuilder> nodes = new LinkedHashMap<>()
        NodeBuilder root

        private getArtifactNodes() {
            Set<NodeBuilder> result = new LinkedHashSet()
            visitNodes(root, result)
            return result
        }

        private void visitNodes(NodeBuilder node, Set<NodeBuilder> result) {
            Set<NodeBuilder> nodesToVisit = []
            for (EdgeBuilder edge: node.deps) {
                if (result.add(edge.selected)) {
                    nodesToVisit << edge.selected
                }
            }
            for(NodeBuilder child: nodesToVisit) {
                visitNodes(child, result)
            }
        }

        private getEdges() {
            Set<EdgeBuilder> result = new LinkedHashSet<>()
            Set<NodeBuilder> seen = []
            visitEdges(root, seen, result)
            return result
        }

        private visitEdges(NodeBuilder node, Set<NodeBuilder> seenNodes, Set<EdgeBuilder> edges) {
            for (EdgeBuilder edge: node.deps) {
                edges.add(edge)
                if (seenNodes.add(edge.selected)) {
                    visitEdges(edge.selected, seenNodes, edges)
                }
            }
        }

        /**
         * Defines the root node of the graph. The closure delegates to a {@link NodeBuilder} instance that represents the root node.
         */
        def root(String value, Closure cl) {
            if (root != null) {
                throw new IllegalStateException("Root node is already defined")
            }
            root = node(value)
            cl.resolveStrategy = Closure.DELEGATE_ONLY
            cl.delegate = root
            cl.call()
            return root
        }

        def node(String value) {
            def node = nodes[value]
            if (!node) {
                node = new NodeBuilder(value, this)
                nodes[value] = node
            }
            return node
        }
    }

    public static class EdgeBuilder {
        final String requested
        final NodeBuilder from
        final NodeBuilder selected

        EdgeBuilder(NodeBuilder from, String requested, NodeBuilder selected) {
            this.from = from
            this.selected = selected
            this.requested = requested
        }
    }

    public static class NodeBuilder {
        final List<EdgeBuilder> deps = []
        private final GraphBuilder graph
        final String id
        final String group
        final String module
        final String version
        private String reason

        NodeBuilder(String id, GraphBuilder graph) {
            this.graph = graph
            if (id.matches(':\\w+:')) {
                def parts = id.split(':')
                this.group = null
                this.module = parts[1]
                this.version = null
                this.id = ":${module}:unspecified"
            } else if (id.matches('\\w+:\\w+:')) {
                def parts = id.split(':')
                this.group = parts[0]
                this.module = parts[1]
                this.version = null
                this.id = "${group}:${module}:unspecified"
            } else {
                def parts = id.split(':')
                assert parts.length == 3
                this.group = parts[0]
                this.module = parts[1]
                this.version = parts[2]
                this.id = id
            }
        }

        private def getReason() {
            reason ?: (this == graph.root ? 'root:' : 'requested:')
        }

        private def getFileName() {
            "$module${version ? '-' + version : ''}.jar"
        }

        /**
         * Defines a dependency on the given node.
         */
        def node(String value) {
            def node = graph.node(value)
            deps << new EdgeBuilder(this, node.id, node)
            return node
        }

        /**
         * Defines a dependency on the given node. The closure delegates to a {@link NodeBuilder} instance that represents the target node.
         */
        def node(String value, Closure cl) {
            def node = node(value)
            cl.resolveStrategy = Closure.DELEGATE_ONLY
            cl.delegate = node
            cl.call()
            return node
        }

        /**
         * Defines a dependency on the given node.
         */
        def edge(String requested, String selected) {
            def node = graph.node(selected)
            deps << new EdgeBuilder(this, requested, node)
            return node
        }

        /**
         * Marks that this node was selected due to conflict resolution.
         */
        def byConflictResolution() {
            reason = 'conflict resolution:conflict'
            this
        }
    }
}

public class GenerateGraphTask extends DefaultTask {
    File outputFile
    Configuration configuration

    @TaskAction
    def generateOutput() {
        outputFile.parentFile.mkdirs()

        outputFile.withPrintWriter { writer ->
            configuration.resolvedConfiguration.firstLevelModuleDependencies.each {
                writer.println("first-level:[${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}:${it.configuration}]")
            }
            configuration.resolvedConfiguration.resolvedArtifacts.each {
                writer.println("artifact:[${it.moduleVersion.id}][${it.name}${it.classifier ? "-" + it.classifier : ""}.${it.extension}]")
            }
            def root = configuration.incoming.resolutionResult.root
            writer.println("root:[${root.id}][${formatReason(root.selectionReason)}]")
            configuration.incoming.resolutionResult.allComponents.each {
                writer.println("module-version:[${it.publishedAs}][${formatReason(it.selectionReason)}]")
            }
            configuration.incoming.resolutionResult.allDependencies.each {
                writer.println("dependency:[${it.from.publishedAs}][${it.requested}->${it.selected.publishedAs}]")
            }
            configuration.files.each {
                writer.println("file:${it.name}")
            }
        }
    }

    def formatReason(ComponentSelectionReason reason) {
        def reasons = []
        if (reason.conflictResolution) {
            reasons << "conflict"
        }
        if (reason.forced) {
            reasons << "forced"
        }
        if (reason.selectedByRule) {
            reasons << "selectedByRule"
        }
        return "${reason.description}:${reasons.join(',')}"
    }
}
