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
import org.gradle.api.artifacts.result.ResolvedComponentResult
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
    tasks.addPlaceholderAction("checkDeps", ${GenerateGraphTask.name}) {
        it.dependsOn configurations.${config}
        it.outputFile = rootProject.file("\${rootProject.buildDir}/${config}.txt")
        it.configuration = configurations.$config
    }
}
"""
    }

    /**
     * Verifies the result of executing the task injected by {@link #prepare()}. The closure delegates to a {@link GraphBuilder} instance.
     */
    void expectGraph(@DelegatesTo(GraphBuilder) Closure closure) {
        def graph = new GraphBuilder()
        closure.resolveStrategy = Closure.DELEGATE_ONLY
        closure.delegate = graph
        closure.call()

        if (graph.root == null) {
            throw new IllegalArgumentException("No root node defined")
        }

        def configDetailsFile = buildFile.parentFile.file("build/${config}.txt")
        def configDetails = configDetailsFile.text.readLines()

        def actualArtifacts = configDetails.findAll { it.startsWith('artifact:') }.collect { it.substring(9) }
        def expectedArtifacts = graph.artifactNodes.collect { "[${it.moduleVersionId}][${it.module}.jar]" }
        assert actualArtifacts == expectedArtifacts

        def actualFiles = configDetails.findAll { it.startsWith('file:') }.collect { it.substring(5) }
        def expectedFiles = graph.artifactNodes.collect { it.fileName }
        assert actualFiles == expectedFiles

        def actualFirstLevel = configDetails.findAll { it.startsWith('first-level:') }.collect { it.substring(12) } as LinkedHashSet
        def expectedFirstLevel = graph.root.deps.collect { "[${it.selected.moduleVersionId}:default]" } as LinkedHashSet
        assert actualFirstLevel == expectedFirstLevel

        def actualRoot = configDetails.find { it.startsWith('root:') }.substring(5)
        def expectedRoot = "[id:${graph.root.id}][mv:${graph.root.moduleVersionId}][reason:${graph.root.reason}]"
        assert actualRoot == expectedRoot

        def actualNodes = configDetails.findAll { it.startsWith('component:') }.collect { it.substring(10) }
        def expectedNodes = graph.nodes.values().collect { "[id:${it.id}][mv:${it.moduleVersionId}][reason:${it.reason}]" }
        assert actualNodes == expectedNodes

        def actualEdges = configDetails.findAll { it.startsWith('dependency:') }.collect { it.substring(11) }
        def expectedEdges = graph.edges.collect { "[from:${it.from.id}][${it.requested}->${it.selected.id}]" }
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
        def root(String value, @DelegatesTo(NodeBuilder) Closure cl) {
            if (root != null) {
                throw new IllegalStateException("Root node is already defined")
            }
            root = node(value, value)
            cl.resolveStrategy = Closure.DELEGATE_ONLY
            cl.delegate = root
            cl.call()
            return root
        }

        /**
         * Defines the root node of the graph. The closure delegates to a {@link NodeBuilder} instance that represents the root node.
         */
        def root(String path, String value, @DelegatesTo(NodeBuilder) Closure cl) {
            if (root != null) {
                throw new IllegalStateException("Root node is already defined")
            }
            root = node("project $path", value)
            cl.resolveStrategy = Closure.DELEGATE_ONLY
            cl.delegate = root
            cl.call()
            return root
        }

        def node(Map attrs) {
            def id = "${attrs.group}:${attrs.module}:${attrs.version}"
            return node(id, id, attrs)
        }

        def node(String id, String moduleVersionId) {
            def attrs
            if (moduleVersionId.matches(':\\w+:')) {
                def parts = moduleVersionId.split(':')
                attrs = [group: null, module: parts[1], version:null]
                moduleVersionId = ":${attrs.module}:unspecified"
            } else if (moduleVersionId.matches('\\w+:\\w+:')) {
                def parts = moduleVersionId.split(':')
                attrs = [group: parts[0], module: parts[1], version: null]
                moduleVersionId = "${attrs.group}:${attrs.module}:unspecified"
            } else {
                def parts = moduleVersionId.split(':')
                assert parts.length == 3
                attrs = [group: parts[1], module: parts[1], version: parts[2]]
            }
            return node(id, moduleVersionId, attrs)
        }

        def node(String id, String moduleVersion, Map attrs) {
            def node = nodes[moduleVersion]
            if (!node) {
                node = new NodeBuilder(id, moduleVersion, attrs, this)
                nodes[moduleVersion] = node
            }
            return node
        }
    }

    public static class EdgeBuilder {
        final String requested
        final NodeBuilder from
        NodeBuilder selected

        EdgeBuilder(NodeBuilder from, String requested, NodeBuilder selected) {
            this.from = from
            this.requested = requested
            this.selected = selected
        }

        EdgeBuilder selects(Map selectedModule) {
            selected = from.graph.node(selectedModule)
            return this
        }
    }

    public static class NodeBuilder {
        final List<EdgeBuilder> deps = []
        private final GraphBuilder graph
        final String id
        final String moduleVersionId
        final String group
        final String module
        final String version
        private String reason

        NodeBuilder(String id, String moduleVersionId, Map attrs, GraphBuilder graph) {
            this.graph = graph
            this.group = attrs.group
            this.module = attrs.module
            this.version = attrs.version
            this.moduleVersionId = moduleVersionId
            this.id = id
        }

        private def getReason() {
            reason ?: (this == graph.root ? 'root:' : 'requested:')
        }

        private def getFileName() {
            "$module${version ? '-' + version : ''}.jar"
        }

        private NodeBuilder addNode(String id, String moduleVersionId = id) {
            def node = graph.node(id, moduleVersionId)
            deps << new EdgeBuilder(this, node.id, node)
            return node
        }

        /**
         * Defines a dependency on the given external module.
         */
        NodeBuilder module(String moduleVersionId) {
            return addNode(moduleVersionId)
        }

        /**
         * Defines a dependency on the given external module. The closure delegates to a {@link NodeBuilder} instance that represents the target node.
         */
        NodeBuilder module(String moduleVersionId, @DelegatesTo(NodeBuilder) Closure cl) {
            def node = module(moduleVersionId)
            cl.resolveStrategy = Closure.DELEGATE_ONLY
            cl.delegate = node
            cl.call()
            return node
        }

        /**
         * Defines a dependency on the given project. The closure delegates to a {@link NodeBuilder} instance that represents the target node.
         */
        NodeBuilder project(String path, String value, @DelegatesTo(NodeBuilder) Closure cl) {
            def node = addNode("project $path", value)
            cl.resolveStrategy = Closure.DELEGATE_ONLY
            cl.delegate = node
            cl.call()
            return node
        }

        /**
         * Defines a dependency from the current node to the given node.
         */
        NodeBuilder edge(String requested, String selectedModuleVersionId) {
            def node = graph.node(selectedModuleVersionId, selectedModuleVersionId)
            deps << new EdgeBuilder(this, requested, node)
            return node
        }

        /**
         * Defines a dependency from the current node to the given node. The closure delegates to a {@link NodeBuilder} instance that represents the target node.
         */
        NodeBuilder edge(String requested, String selectedModuleVersionId, @DelegatesTo(NodeBuilder) Closure cl) {
            def node = edge(requested, selectedModuleVersionId)
            cl.resolveStrategy = Closure.DELEGATE_ONLY
            cl.delegate = node
            cl.call()
            return node
        }

        /**
         * Defines a dependency of the current node.
         */
        EdgeBuilder dependency(Map requested) {
            def edge = new EdgeBuilder(this, "${requested.group}:${requested.module}:${requested.version}", null)
            deps << edge
            return edge
        }

        /**
         * Marks that this node was selected due to conflict resolution.
         */
        NodeBuilder byConflictResolution() {
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
            writer.println("root:${formatComponent(root)}")
            configuration.incoming.resolutionResult.allComponents.each {
                writer.println("component:${formatComponent(it)}")
            }
            configuration.incoming.resolutionResult.allDependencies.each {
                writer.println("dependency:[from:${it.from.id}][${it.requested}->${it.selected.id}]")
            }
            configuration.files.each {
                writer.println("file:${it.name}")
            }
        }
    }

    def formatComponent(ResolvedComponentResult result) {
        return "[id:${result.id}][mv:${result.moduleVersion}][reason:${formatReason(result.selectionReason)}]"
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
