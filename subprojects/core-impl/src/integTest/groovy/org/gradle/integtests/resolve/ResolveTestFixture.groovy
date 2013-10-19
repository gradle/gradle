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
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.test.fixtures.file.TestFile

/**
 * A test fixture that injects a task into a build that resolves a dependency configuration and does some validation of the resulting graph.
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
        closure.delegate = graph
        closure.call()
        def configFile = buildFile.parentFile.file("build/${config}.txt").text.readLines()

        def actualArtifacts = configFile.findAll { it.startsWith('artifact:') }.collect { it.substring(9) }
        def expectedArtifacts = graph.artifactNodes.collect { "${it.group}:${it.module}:${it.version}:${it.module}:null:jar" }
        assert actualArtifacts == expectedArtifacts

        def actualFiles = configFile.findAll { it.startsWith('file:') }.collect { it.substring(5) }
        def expectedFiles = graph.artifactNodes.collect { "$it.module-${it.version}.jar" }
        assert actualFiles == expectedFiles
    }

    public static class GraphBuilder {
        final Map<String, NodeBuilder> nodes = new LinkedHashMap<>()
        NodeBuilder root

        private getArtifactNodes() {
            return nodes.values().findAll { it != root }
        }

        def root(String value) {
            root = node(value)
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

    public static class NodeBuilder {
        final List<NodeBuilder> deps = []
        private final GraphBuilder graph
        final String id
        final String group
        final String module
        final String version

        NodeBuilder(String id, GraphBuilder graph) {
            this.id = id
            def parts = id.split(':')
            assert parts.length == 3
            this.group = parts[0]
            this.module = parts[1]
            this.version = parts[2]
            this.graph = graph
        }

        def dependsOn(NodeBuilder other) {
            deps << other
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
                writer.println("artifact:${it.moduleVersion.id.group}:${it.moduleVersion.id.name}:${it.moduleVersion.id.version}:${it.name}:${it.classifier}:${it.extension}")
            }
            configuration.incoming.resolutionResult.allModuleVersions.each {
                writer.println("module-version:${it.id.group}:${it.id.name}:${it.id.version}")
            }
            configuration.files.each {
                writer.println("file:${it.name}")
            }
        }
    }
}
