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
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason
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
    dependencies.classpath files("${ClasspathUtil.getClasspathForClass(GenerateGraphTask)}")
}

task checkDeps(dependsOn: configurations.${config}, type: ${GenerateGraphTask.name}) {
    outputFile = file("\${buildDir}/${config}.txt")
    configuration = configurations.$config
}
"""
    }

    /**
     * Verifies the result of executing the task.
     */
    void expectGraph(Closure closure) {
        def graph = new GraphBuilder()
        closure.resolveStrategy = Closure.DELEGATE_ONLY
        closure.delegate = graph
        closure.call()

        if (graph.root == null) {
            throw new IllegalArgumentException("No root node defined")
        }

        def configFile = buildFile.parentFile.file("build/${config}.txt").text.readLines()

        def actualArtifacts = configFile.findAll { it.startsWith('artifact:') }.collect { it.substring(9) }
        def expectedArtifacts = graph.artifactNodes.collect { "${it.group}:${it.module}:${it.version}:${it.module}.jar" }
        assert actualArtifacts == expectedArtifacts

        def actualFiles = configFile.findAll { it.startsWith('file:') }.collect { it.substring(5) }
        def expectedFiles = graph.artifactNodes.collect { "$it.module-${it.version}.jar" }
        assert actualFiles == expectedFiles

        def actualFirstLevel = configFile.findAll { it.startsWith('first-level:') }.collect { it.substring(12) }
        def expectedFirstLevel = graph.root.deps.collect { "${it.selected.id}:default" }
        assert actualFirstLevel == expectedFirstLevel

        def actualRoot = configFile.find { it.startsWith('root:') }.substring(5)
        def expectedRoot = "${graph.root.id}:${graph.root.reason}"
        assert actualRoot == expectedRoot

        def actualNodes = configFile.findAll { it.startsWith('module-version:') }.collect { it.substring(15) }
        def expectedNodes = graph.nodes.values().collect { "${it.id}:${it.reason}" }
        assert actualNodes == expectedNodes

        def actualEdges = configFile.findAll { it.startsWith('dependency:') }.collect { it.substring(11) }
        def expectedEdges = graph.nodes.values().collect { from -> from.deps.collect { "${from.id}:${it.requested}->${it.selected.id}" } }.flatten()
        assert actualEdges == expectedEdges
    }

    public static class GraphBuilder {
        final Map<String, NodeBuilder> nodes = new LinkedHashMap<>()
        final Set<NodeBuilder> reachable = new LinkedHashSet<>()
        NodeBuilder root

        private getArtifactNodes() {
            return reachable
        }

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
        final NodeBuilder selected

        EdgeBuilder(String requested, NodeBuilder selected) {
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
            this.id = id
            def parts = id.split(':')
            assert parts.length == 3
            this.group = parts[0]
            this.module = parts[1]
            this.version = parts[2]
            this.graph = graph
        }

        def getReason() {
            reason ?: (this == graph.root ? 'root:' : 'requested:')
        }

        def node(String value) {
            return edge(value, value).selected
        }

        def node(String value, Closure cl) {
            def node = node(value)
            cl.resolveStrategy = Closure.DELEGATE_ONLY
            cl.delegate = node
            cl.call()
            return node
        }

        def edge(String requested, String selected) {
            def node = graph.node(selected)
            graph.reachable << node
            def edge = new EdgeBuilder(requested, node)
            deps << edge
            return edge
        }

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
                writer.println("first-level:${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}:${it.configuration}")
            }
            configuration.resolvedConfiguration.resolvedArtifacts.each {
                writer.println("artifact:${it.moduleVersion.id}:${it.name}${it.classifier ? "-" + it.classifier : ""}.${it.extension}")
            }
            def root = configuration.incoming.resolutionResult.root
            writer.println("root:${root.id}:${formatReason(root.selectionReason)}")
            configuration.incoming.resolutionResult.allModuleVersions.each {
                writer.println("module-version:${it.id}:${formatReason(it.selectionReason)}")
            }
            configuration.incoming.resolutionResult.allDependencies.each {
                writer.println("dependency:${it.from.id}:${it.requested}->${it.selected.id}")
            }
            configuration.files.each {
                writer.println("file:${it.name}")
            }
        }
    }

    def formatReason(ModuleVersionSelectionReason reason) {
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
