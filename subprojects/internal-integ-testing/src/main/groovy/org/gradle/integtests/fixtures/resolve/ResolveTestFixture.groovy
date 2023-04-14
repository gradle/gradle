/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.fixtures.resolve

import com.google.common.base.Joiner
import groovy.transform.Canonical
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.test.fixtures.file.TestFile
import org.junit.ComparisonFailure

/**
 * A test fixture that injects a "checkDeps" task into a build that resolves a dependency configuration and does some validation of the resulting graph, to
 * ensure that the old and new dependency graphs plus the artifacts and files are as expected and well-formed.
 */
class ResolveTestFixture {
    private static final START_MARKER = "// RESOLVE_TEST_FIXTURE_START"
    private static final END_MARKER = "// RESOLVE_TEST_FIXTURE_END"
    final TestFile buildFile
    String config
    private String defaultConfig = "default"
    private boolean buildArtifacts = true

    private boolean configurationCacheEnabled = GradleContextualExecuter.configCache

    ResolveTestFixture(TestFile buildFile, String config = "runtimeClasspath") {
        this.config = config
        this.buildFile = buildFile
    }

    ResolveTestFixture withoutBuildingArtifacts() {
        buildArtifacts = false
        return this
    }

    ResolveTestFixture expectDefaultConfiguration(String config) {
        defaultConfig = config
        return this
    }

    /**
     * Creates a 'checkDeps' task that resolves the given configuration.
     */
    void prepare(String configToCheck) {
        prepare {
            config(configToCheck, "checkDeps")
        }
    }

    /**
     * Injects the appropriate stuff into the build script. By default, creates a 'checkDeps' task that resolves the configuration provided in the constructor.
     */
    void prepare(@DelegatesTo(CheckTaskBuilder) Closure closure = {}) {
        def builder = new CheckTaskBuilder()
        closure.delegate = builder
        closure.run()
        if (builder.configs.isEmpty()) {
            builder.config(config, "checkDeps")
        }

        def existingScript = buildFile.exists() ? buildFile.text : ""
        def start = existingScript.indexOf(START_MARKER)
        def end = existingScript.indexOf(END_MARKER) + END_MARKER.length()
        if (start >= 0) {
            existingScript = existingScript.substring(0, start) + existingScript.substring(end, existingScript.length())
        }
        def buildScriptBlock = """
buildscript {
    dependencies.classpath files("${ClasspathUtil.getClasspathForClass(GenerateGraphTask).toURI()}")
}
"""
        String generatedContent = ""
        builder.configs.forEach { config, taskName ->
            def inputs = buildArtifacts ? "it.inputs.files configurations." + config : ""
            if (configurationCacheEnabled) {
                generatedContent += """
allprojects {
    tasks.register("${taskName}", ${ConfigurationCacheCompatibleGenerateGraphTask.name}) {
        it.outputFile = rootProject.file("\${rootProject.buildDir}/last-graph.txt")
        it.rootComponent = configurations.${config}.incoming.resolutionResult.rootComponent
        it.files.from(configurations.${config})
        it.artifacts = configurations.${config}.incoming.artifacts
        it.buildArtifacts = ${buildArtifacts}
        ${inputs}
    }
}
"""
            } else {
                generatedContent += """
allprojects {
    tasks.register("${taskName}", ${GenerateGraphTask.name}) {
        it.outputFile = rootProject.file("\${rootProject.buildDir}/last-graph.txt")
        it.configuration = configurations.$config
        it.buildArtifacts = ${buildArtifacts}
        ${inputs}
    }
}
"""
            }
        }

        buildFile.text = """
$buildScriptBlock
$existingScript
$START_MARKER
$generatedContent
$END_MARKER
"""
    }

    def getResultFile() {
        buildFile.parentFile.file("build/last-graph.txt")
    }

    /**
     * Verifies the result of executing the task injected by {@link #prepare()}. The closure delegates to a {@link GraphBuilder} instance.
     */
    void expectGraph(@DelegatesTo(GraphBuilder) Closure closure) {
        def graph = new GraphBuilder()
        closure.resolveStrategy = Closure.DELEGATE_ONLY
        closure.delegate = graph
        closure.call()

        def root = graph.root
        if (root == null) {
            throw new IllegalArgumentException("No root node defined")
        }

        def configDetailsFile = getResultFile()
        def configDetails = configDetailsFile.text.readLines()

        def actualRoot = findLines(configDetails, 'root').first()
        def expectedRoot = "[${root.type}][id:${root.id}][mv:${root.moduleVersionId}][reason:${root.reason}]".toString()
        assert actualRoot.startsWith(expectedRoot)

        def actualComponents = findLines(configDetails, 'component')
        def expectedComponents = graph.nodes.collect { baseNode ->
            def variants = baseNode.variants
            new ParsedNode(type: baseNode.type,
                id: baseNode.id,
                module: baseNode.moduleVersionId,
                reasons: baseNode.allReasons,
                variants: variants,
                ignoreReasons: baseNode.ignoreReasons,
                ignoreReasonPrefixes: baseNode.ignoreReasonPrefixes)
        }
        compareNodes("components in graph", parseNodes(actualComponents), expectedComponents)

        def actualEdges = findLines(configDetails, 'dependency')
        def expectedEdges = graph.edges.collect { "${it.constraint ? '[constraint]' : ''}[from:${it.from.id}][${it.requested}->${it.selected.id}]" }
        compare("edges in graph", actualEdges, expectedEdges)

        def expectedFiles = root.files + graph.artifactNodes.collect { it.fileName }

        if (buildArtifacts) {
            def actualFiles = findLines(configDetails, 'file')
            compare("files", actualFiles, expectedFiles)

            actualFiles = findLines(configDetails, 'file-artifact-incoming')
            compare("incoming.artifacts", actualFiles, expectedFiles)
        }

        def expectedArtifacts = graph.artifactNodes.collect { "${it.versionedArtifactName} (${it.componentId})" } + graph.files

        def actualArtifacts = findLines(configDetails, 'artifact-incoming')
        compare("artifacts", actualArtifacts, expectedArtifacts)

        if (configurationCacheEnabled) {
            return
        }

        def expectedFirstLevel = root.deps.findAll { !it.constraint }.collect { d ->
            def configs = d.selected.firstLevelConfigurations.collect {
                "[${d.selected.moduleVersionId}:${it}]"
            }
            if (configs.empty) {
                configs = ["[${d.selected.moduleVersionId}:$defaultConfig"]
            }
            configs
        }.flatten() as Set

        def actualFirstLevel = findLines(configDetails, 'first-level')
        compare("first level dependencies", actualFirstLevel, expectedFirstLevel)

        actualFirstLevel = findLines(configDetails, 'first-level-filtered')
        compare("filtered first level dependencies", actualFirstLevel, expectedFirstLevel)

        actualFirstLevel = findLines(configDetails, 'lenient-first-level')
        compare("lenient first level dependencies", actualFirstLevel, expectedFirstLevel)

        actualFirstLevel = findLines(configDetails, 'lenient-first-level-filtered')
        compare("lenient filtered first level dependencies", actualFirstLevel, expectedFirstLevel)

        def actualConfigurations = findLines(configDetails, 'configuration') as Set
        def expectedConfigurations = graph.nodesWithoutRoot.collect { "[${it.moduleVersionId}]".toString() } - graph.virtualConfigurations.collect { "[${it}]".toString() } as Set
        compare("configurations in graph", actualConfigurations, expectedConfigurations)

        def expectedLegacyArtifacts = graph.artifactNodes.collect { "[${it.moduleVersionId}][${it.legacyArtifactName}]" }

        actualArtifacts = findLines(configDetails, 'artifact')
        compare("artifacts", actualArtifacts, expectedLegacyArtifacts)

        actualArtifacts = findLines(configDetails, 'lenient-artifact')
        compare("lenient artifacts", actualArtifacts, expectedLegacyArtifacts)

        actualArtifacts = findLines(configDetails, 'filtered-lenient-artifact')
        compare("filtered lenient artifacts", actualArtifacts, expectedLegacyArtifacts)

        if (buildArtifacts) {
            def actualFiles = findLines(configDetails, 'file-files')
            compare("this.files", actualFiles, expectedFiles)

            actualFiles = findLines(configDetails, 'file-incoming')
            compare("incoming.files", actualFiles, expectedFiles)

            actualFiles = findLines(configDetails, 'file-filtered')
            compare("filtered files", actualFiles, expectedFiles)

            actualFiles = findLines(configDetails, 'file-collection-filtered')
            compare("filtered FileCollection", actualFiles, expectedFiles)

            actualFiles = findLines(configDetails, 'file-resolved-config')
            compare("resolved configuration files", actualFiles, expectedFiles)

            actualFiles = findLines(configDetails, 'file-resolved-config-filtered')
            compare("resolved configuration filtered files", actualFiles, expectedFiles)

            actualFiles = findLines(configDetails, 'file-lenient-config')
            compare("lenient configuration files", actualFiles, expectedFiles)

            actualFiles = findLines(configDetails, 'file-lenient-config-filtered')
            compare("lenient configuration filtered files", actualFiles, expectedFiles)

            // The following do not include file dependencies
            expectedFiles = graph.artifactNodes.collect { it.fileName }

            actualFiles = findLines(configDetails, 'file-artifact-resolved-config')
            compare("resolved configuration artifact files", actualFiles, expectedFiles)

            actualFiles = findLines(configDetails, 'file-artifact-lenient-config')
            compare("lenient configuration artifact files", actualFiles, expectedFiles)

            actualFiles = findLines(configDetails, 'file-artifact-lenient-config-filtered')
            compare("filtered lenient configuration artifact files", actualFiles, expectedFiles)
        }
    }

    List<String> findLines(List<String> lines, String prefix) {
        return lines.findAll { it.startsWith(prefix + ":") }.collect { it.substring(prefix.length() + 1) }
    }

    List<ParsedNode> parseNodes(List<String> nodes) {
        nodes.collect { parseNode(it) }
    }

    ParsedNode parseNode(String line) {
        int start = 1
        // we look for ][ instead of just ], because of that one test that checks that we can have random characters in id
        // see IvyDynamicRevisionRemoteResolveIntegrationTest. uses latest version from version range with punctuation characters
        int idx = line.indexOf('][')
        if (idx < 0) {
            throw new IllegalArgumentException("Missing type in '$line'")
        }
        String type = line.substring(start, idx)
        start = idx + 5
        idx = line.indexOf('][', start)
        if (idx < 0) {
            throw new IllegalArgumentException("Missing id in '$line'")
        }
        String id = line.substring(start, idx) // [id:
        start = idx + 5
        idx = line.indexOf('][', start)
        if (idx < 0) {
            throw new IllegalArgumentException("Missing module in '$line'")
        }
        String module = line.substring(start, idx) // [mv:
        start = idx + 9
        idx = line.indexOf(']', start) // [reason:
        if (idx < 0) {
            throw new IllegalArgumentException("Missing reasons in '$line'")
        }
        List<String> reasons = line.substring(start, idx).split('!!') as List<String>
        Set<Variant> variants = []
        start = idx + 15
        while (start < line.length()) {
            idx = line.indexOf(' attributes:', start) // [variant name:
            String variant = line.substring(start, idx)
            start = idx + 12
            idx = line.indexOf('@@', start)
            if (idx < 0) {
                idx = line.indexOf(']', start) // attributes:
            }
            Map<String, String> attributes = line.substring(start, idx)
                .split(',') // attributes are separated by commas
                .findAll() // only keep non empty entries (thank you, split!)
                .collectEntries { it.split('=') as List }
            start = idx + 15 // '@@'
            variants << new Variant(name: variant, attributes: attributes)
        }
        new ParsedNode(type: type, id: id, module: module, reasons: reasons, variants: variants)
    }

    static class ParsedNode {
        String type
        String id
        String module
        Set<String> reasons
        boolean ignoreRequested
        Set<String> ignoreReasons
        Set<String> ignoreReasonPrefixes
        Set<Variant> variants = []

        boolean diff(ParsedNode actual, StringBuilder sb) {
            List<String> errors = []
            if (type != actual.type) {
                errors << "Expected type '$type' but was: $actual.type"
            }
            if (id != actual.id) {
                errors << "Expected ID '$id' but was: $actual.id"
            }
            if (this.module != actual.module) {
                errors << "Expected module '${this.module}' but was: $actual.module"
            }
            def actualReasons = actual.reasons.findAll {
                if (it == "requested" && ignoreRequested) {
                    false
                } else if (ignoreReasons.contains(it)) {
                    false
                } else if (ignoreReasonPrefixes.any { prefix -> it.startsWith(prefix) }) {
                    false
                } else {
                    true
                }
            }.toSet()
            if (actualReasons != reasons) {
                errors << "Expected reasons ${reasons} but was: ${actual.reasons}"
            }
            this.variants.each { variant ->
                def actualVariant = actual.variants.find { it.name == variant.name }
                if (!actualVariant) {
                    errors << "Expected variant name $variant, but wasn't found in: $actual.variants.name"
                } else {
                    if (variant.attributes != actualVariant.attributes) {
                        errors << "On variant $variant.name, expected attributes $variant.attributes, but was: $actualVariant.attributes"
                    }
                }
            }

            if (errors) {
                sb.append("On component $id:\n")
                errors.each {
                    sb.append("   - ").append(it).append("\n")
                }
                return true
            }
            return false
        }

        String toString() {
            "id: $id, module: ${this.module}, reasons: ${reasons}${this.variants}"
        }
    }

    static void compareNodes(String compType, Collection<ParsedNode> actual, Collection<ParsedNode> expected) {
        def actualSorted = actual.sort { it.id }
        def expectedSorted = expected.sort { it.id }
        StringBuilder errors = new StringBuilder()
        StringBuilder matched = new StringBuilder()
        expectedSorted.each { node ->
            def actualNode = actualSorted.find { it.id == node.id }

            if (!actualNode) {
                errors.append("Expected to find node ${node.id} but wasn't present in result\n")
            } else if (!node.diff(actualNode, errors)) {
                matched.append("   - $node\n")
            }
        }
        actualSorted.each { node ->
            if (!expectedSorted.find { it.id == node.id }) {
                errors.append("Found unexpected node $node")
            }
        }
        if (errors.length() > 0) {
            throw new AssertionError("Result contains unexpected $compType\n${errors}\nMatched $compType:\n${matched}")
        }
    }

    void compare(String compType, Collection<String> actual, Collection<String> expected) {
        def actualSorted = new ArrayList<String>(actual).sort()
        def expectedSorted = new ArrayList<String>(expected).sort()
        boolean equals = actual.size() == expectedSorted.size()
        if (equals) {
            for (int i = 0; i < actual.size(); i++) {
                equals &= actualSorted.get(i).startsWith(expectedSorted.get(i))
            }
        }
        if (!equals) {
            def actualFormatted = Joiner.on("\n").join(actualSorted)
            def expectedFormatted = Joiner.on("\n").join(expectedSorted)
            throw new ComparisonFailure("Result contains unexpected $compType", expectedFormatted, actualFormatted);
        }
    }

    static class GraphBuilder {
        private final Map<String, NodeBuilder> nodes = [:]
        private NodeBuilder root

        final Set<String> virtualConfigurations = []

        Collection<NodeBuilder> getNodes() {
            return nodes.values()
        }

        Collection<NodeBuilder> getNodesWithoutRoot() {
            def nodes = new HashSet<>()
            visitDeps(this.root.deps, nodes, new HashSet<>())
            return nodes
        }

        void virtualConfiguration(String id) {
            virtualConfigurations << id
        }

        private void visitDeps(List<EdgeBuilder> edges, Set<NodeBuilder> nodes, Set<NodeBuilder> seen) {
            for (EdgeBuilder edge : edges) {
                def selected = edge.selected
                if (seen.add(selected)) {
                    nodes.add(selected)
                    visitDeps(selected.deps, nodes, seen)
                }
            }
        }

        Set<ExpectedArtifact> getArtifactNodes() {
            Set<NodeBuilder> result = new LinkedHashSet()
            visitNodes(this.root, result)
            return result.collect { it.artifacts }.flatten()
        }

        Set<String> getFiles() {
            Set<NodeBuilder> result = new LinkedHashSet()
            result.add(this.root)
            visitNodes(this.root, result)
            return result.collect { node -> node.files }.flatten()
        }

        private void visitNodes(NodeBuilder node, Set<NodeBuilder> result) {
            Set<NodeBuilder> nodesToVisit = []
            for (EdgeBuilder edge : node.deps) {
                def targetNode = edge.selected
                if (result.add(targetNode)) {
                    nodesToVisit << targetNode
                }
            }
            for (NodeBuilder child : nodesToVisit) {
                visitNodes(child, result)
            }
        }

        private getEdges() {
            Set<EdgeBuilder> result = new LinkedHashSet<>()
            Set<NodeBuilder> seen = []
            visitEdges(this.root, seen, result)
            return result
        }

        private visitEdges(NodeBuilder node, Set<NodeBuilder> seenNodes, Set<EdgeBuilder> edges) {
            for (EdgeBuilder edge : node.deps) {
                edges.add(edge)
                if (seenNodes.add(edge.selected)) {
                    visitEdges(edge.selected, seenNodes, edges)
                }
            }
        }

        /**
         * Defines the root node of the graph. The closure delegates to a {@link NodeBuilder} instance that represents the root node.
         *
         * @param projectPath The path of the project to which the graph belongs.
         * @param moduleVersion The module version for this project.
         */
        def root(String projectPath, String moduleVersion, @DelegatesTo(NodeBuilder) Closure cl) {
            if (this.root != null) {
                throw new IllegalStateException("Root node is already defined")
            }
            this.root = projectNode(projectPath, moduleVersion)
            cl.resolveStrategy = Closure.DELEGATE_ONLY
            cl.delegate = this.root
            cl.call()
            return this.root
        }

        private NodeBuilder projectNode(String projectIdentityPath, String moduleVersion) {
            return node("project:$projectIdentityPath", "project $projectIdentityPath", moduleVersion)
        }

        private NodeBuilder moduleNode(String moduleVersionId) {
            def parts = moduleVersionId.split(':')
            // the supplied moduleVersionId may contain additional attributes
            assert parts.length >= 3
            def group = parts[0]
            def module = parts[1]
            def version = parts[2]
            def actualMVI = "${group}:${module}:${version}"
            return node("module:${actualMVI},${group}:${module}", actualMVI, moduleVersionId)
        }

        NodeBuilder module(Map attrs) {
            def group = attrs.group
            def module = attrs.module
            def version = attrs.version
            def moduleVersionId = "$group:$module:$version"
            return node("module:$moduleVersionId,$group:$module", moduleVersionId, moduleVersionId, attrs)
        }

        NodeBuilder node(String type, String id, String moduleVersionId) {
            def attrs
            if (moduleVersionId.matches(':\\w+:')) {
                def parts = moduleVersionId.split(':')
                attrs = [group: null, module: parts[1], version: null]
                moduleVersionId = ":${attrs.module}:unspecified"
            } else if (moduleVersionId.matches('\\w+:\\w+:')) {
                def parts = moduleVersionId.split(':')
                attrs = [group: parts[0], module: parts[1], version: null]
                moduleVersionId = "${attrs.group}:${attrs.module}:unspecified"
            } else {
                def parts = moduleVersionId.split(':')
                if (parts.length == 3) {
                    attrs = [group: parts[0], module: parts[1], version: parts[2]]
                } else {
                    assert parts.length == 4
                    attrs = [group: parts[0], module: parts[1], version: parts[2], configuration: parts[3]]
                    id = "${attrs.group}:${attrs.module}:${attrs.version}"
                    moduleVersionId = id
                }
            }
            return node(type, id, moduleVersionId, attrs)
        }

        NodeBuilder node(String type, String id, String moduleVersion, Map attrs) {
            def node = nodes[moduleVersion]
            if (!node) {
                node = new NodeBuilder(type, id, moduleVersion, attrs, this)
                nodes[moduleVersion] = node
            }
            if (attrs.configuration) {
                node.configuration(attrs.configuration)
            }
            return node
        }
    }

    static class EdgeBuilder {
        final String requested
        final NodeBuilder from
        NodeBuilder selected
        boolean constraint

        EdgeBuilder(NodeBuilder from, String requested, NodeBuilder selected) {
            this.from = from
            this.requested = requested
            this.selected = selected
        }

        EdgeBuilder selects(Map selectedModule) {
            selected = from.graph.module(selectedModule)
            return this
        }
    }

    static class ExpectedArtifact {
        String componentId
        String group
        String module
        String moduleVersion
        String version
        String classifier
        String type
        String extension
        String name
        String fileName
        String artifactName
        String legacyName

        ModuleVersionIdentifier getModuleVersionId() {
            String effectiveVersion = moduleVersion ? moduleVersion : 'unspecified'
            return DefaultModuleVersionIdentifier.newId(this.group, this.module, effectiveVersion)
        }

        String getLegacyArtifactName() {
            def effectiveName = legacyName != null ? legacyName : nameComponent
            def effectiveType = type != null ? type : 'jar'
            def effectiveExt = extension != null ? extension : effectiveType
            return "${effectiveName}:${classifier}:${effectiveExt}:${effectiveType}"
        }

        String getVersionedArtifactName() {
            if (artifactName) {
                return artifactName
            }
            def baseName = "${nameComponent}${classifierComponent}"
            if (componentId.startsWith("project :")) {
                return "${baseName}${extensionComponent}"
            } else {
                return "${nameComponent}${versionComponent}${classifierComponent}${extensionComponent}"
            }
        }

        String getFileName() {
            if (fileName) {
                return fileName
            }
            return "${nameComponent}${versionComponent}${classifierComponent}${extensionComponent}"
        }

        String getNameComponent() {
            return name ?: this.module
        }

        private String getVersionComponent() {
            if (version == "") {
                return ""
            } else if (version != null) {
                return "-${version}"
            } else if (moduleVersion == "") {
                return ""
            } else if (moduleVersion != null) {
                return "-${moduleVersion}"
            } else {
                return ""
            }
        }

        private String getExtensionComponent() {
            if (extension == "") {
                return ""
            } else if (extension != null) {
                return ".$extension"
            } else if (type == "") {
                return ""
            } else if (type != null) {
                return ".$type"
            } else {
                return ".jar"
            }
        }

        private String getClassifierComponent() {
            if (classifier) {
                return "-$classifier"
            } else {
                return ""
            }
        }
    }

    static class CheckTaskBuilder {
        final Map<String, String> configs = [:]

        void config(String config) {
            configs.put(config, "check${config.capitalize()}")
        }

        void config(String config, String taskName) {
            configs.put(config, taskName)
        }
    }

    @Canonical
    static class Variant {
        String name
        Map<String, String> attributes

        String toString() {
            "variant $name, variant attributes $attributes"
        }
    }

    static class NodeBuilder {
        final List<EdgeBuilder> deps = []
        private final GraphBuilder graph
        final String type
        final String id
        final String moduleVersionId
        final String group
        final String module
        final String version
        final Set<String> configurations = []
        Set<String> firstLevelConfigurations
        private boolean implicitArtifact = true
        final List<String> files = []
        private final Set<ExpectedArtifact> artifacts = new LinkedHashSet<>()
        private final Set<String> reasons = new TreeSet<String>()
        private boolean ignoreRequested
        private final Set<String> ignoreReasons = new HashSet<>()
        private final Set<String> ignoreReasonPrefixes = new HashSet<>()
        Set<Variant> variants = []

        boolean checkVariant

        NodeBuilder(String type, String id, String moduleVersionId, Map attrs, GraphBuilder graph) {
            this.graph = graph
            this.group = attrs.group
            this.module = attrs.module
            this.version = attrs.version
            this.moduleVersionId = moduleVersionId
            this.id = id
            this.type = type
            if (attrs.variantName) {
                variant(attrs.variantName, attrs.variantAttributes)
            }
            reasons.add('requested')
        }

        Set<ExpectedArtifact> getArtifacts() {
            return artifacts.empty && implicitArtifact ? [new ExpectedArtifact(componentId: id, group: this.group, module: this.module, moduleVersion: this.version)] : artifacts
        }

        String getReason() {
            allReasons.join('!!')
        }

        Set<String> getAllReasons() {
            if (this == graph.root) {
                reasons.remove('requested')
                reasons.add('root')
            }
            if (ignoreRequested) {
                reasons.remove('requested')
            }
            return reasons
        }

        private NodeBuilder addNode(NodeBuilder node) {
            deps << new EdgeBuilder(this, node.id, node)
            return node
        }

        /**
         * Defines a dependency on the given external module.
         */
        NodeBuilder module(String moduleVersionId) {
            return addNode(graph.moduleNode(moduleVersionId))
        }

        /**
         * Defines a dependency on the given external module. The closure delegates to a {@link NodeBuilder} instance that represents the target node.
         */
        NodeBuilder module(String moduleVersionId, @DelegatesTo(NodeBuilder) Closure cl) {
            def node = addNode(graph.moduleNode(moduleVersionId))
            applyTo(node, cl)
            return node
        }

        /**
         * Defines a dependency on a unique snapshot module.
         */
        NodeBuilder snapshot(String moduleVersionId, String timestamp, String requestedVersion = null) {
            def id = moduleVersionId + ":" + timestamp
            def parts = moduleVersionId.split(':')
            assert parts.length == 3
            def (group, name, version) = parts
            def attrs = [group: group, module: name, version: version]
            def node = graph.node("module:$moduleVersionId,$group:$name", id, moduleVersionId, attrs)
            deps << new EdgeBuilder(this, requestedVersion ? "${group}:${name}:${requestedVersion}" : moduleVersionId, node)
            return node
        }

        /**
         * Defines a dependency on the given project. The closure delegates to a {@link NodeBuilder} instance that represents the target node.
         */
        NodeBuilder project(String projectIdentityPath, String moduleVersion, @DelegatesTo(NodeBuilder) Closure cl = {}) {
            def node = addNode(graph.projectNode(projectIdentityPath, moduleVersion))
            applyTo(node, cl)
            return node
        }

        /**
         * Defines a link between nodes created through a dependency constraint.
         */
        NodeBuilder constraint(String requested, String selectedModuleVersionId = requested, @DelegatesTo(NodeBuilder) Closure cl = {}) {
            def node = graph.moduleNode(selectedModuleVersionId)
            def edge = new EdgeBuilder(this, requested, node)
            edge.constraint = true
            deps << edge
            applyTo(node, cl)
            return node
        }

        /**
         * Adds a constraint that selects the given project.
         */
        NodeBuilder constraint(String requested, String selectedProjectIdentityPath, String selectedModuleVersionId, @DelegatesTo(NodeBuilder) Closure cl = {}) {
            def node = graph.projectNode(selectedProjectIdentityPath, selectedModuleVersionId)
            def edge = new EdgeBuilder(this, requested, node)
            edge.constraint = true
            deps << edge
            applyTo(node, cl)
            return node
        }

        /**
         * Defines a dependency from the current node to the given module. The closure delegates to a {@link NodeBuilder} instance that represents the target node.
         */
        NodeBuilder edge(String requested, String selectedModuleVersionId, @DelegatesTo(NodeBuilder) Closure cl = {}) {
            def node = graph.moduleNode(selectedModuleVersionId)
            deps << new EdgeBuilder(this, requested, node)
            applyTo(node, cl)
            return node
        }

        /**
         * Defines a dependency from the current node to the given project. The closure delegates to a {@link NodeBuilder} instance that represents the target node.
         */
        NodeBuilder edge(String requested, String selectedProjectIdentityPath, String selectedModuleVersionId, @DelegatesTo(NodeBuilder) Closure cl = {}) {
            def node = graph.projectNode(selectedProjectIdentityPath, selectedModuleVersionId)
            deps << new EdgeBuilder(this, requested, node)
            applyTo(node, cl)
            return node
        }

        private static void applyTo(NodeBuilder node, Closure cl) {
            cl.resolveStrategy = Closure.DELEGATE_ONLY
            cl.delegate = node
            cl.call()
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
         * Specifies that this node has no artifacts associated with it.
         */
        NodeBuilder noArtifacts() {
            implicitArtifact = false
            return this
        }

        /**
         * Specifies an artifact for this node. A default is assumed when none specified
         */
        NodeBuilder artifact(Map attributes = [:]) {
            def artifact = new ExpectedArtifact(
                componentId: id,
                group: this.group,
                module: this.module,
                moduleVersion: this.version,
                version: attributes.version,
                name: attributes.name,
                classifier: attributes.classifier,
                type: attributes.type,
                extension: attributes.extension, // defaults to the type, empty string means no extension
                fileName: attributes.fileName, // overrides the expected file name, defaults to (name)-(version)-(classifier).(type)
                artifactName: attributes.artifactName, // overrides the expected artifact name, defaults to (name)-(classifier).(type)
                legacyName: attributes.legacyName
            )
            artifacts << artifact
            return this
        }

        /**
         * Marks that this node was selected due to conflict resolution.
         */
        NodeBuilder byConflictResolution() {
            reasons << 'conflict resolution'
            this
        }

        /**
         * Marks that this node was selected due to conflict resolution.
         */
        NodeBuilder byConflictResolution(String message) {
            reasons << "${ComponentSelectionCause.CONFLICT_RESOLUTION.defaultReason}: $message".toString()
            this
        }

        /**
         * Marks that this node was selected by a rule.
         */
        NodeBuilder selectedByRule() {
            reasons << 'selected by rule'
            this
        }

        /**
         * Marks that this node was selected by a rule.
         */
        NodeBuilder selectedByRule(String message) {
            reasons << "${ComponentSelectionCause.SELECTED_BY_RULE.defaultReason}: $message".toString()
            this
        }

        /**
         * Marks that this node has a forced vers.
         */
        NodeBuilder forced() {
            reasons << 'forced'
            this
        }

        /**
         * Marks that this node was substituted in a composite.
         */
        NodeBuilder compositeSubstitute() {
            reasons << 'composite build substitution'
            this
        }

        /**
         * Marks that this node was selected by the given reason
         */
        NodeBuilder byReason(String reason) {
            reasons << reason
            this
        }

        NodeBuilder notRequested() {
            reasons.remove('requested')
            this
        }

        NodeBuilder maybeRequested() {
            ignoreRequested = true
            ignoreReasons.add('requested')
            this
        }

        NodeBuilder maybeByConflictResolution() {
            ignoreReasonPrefixes.add("conflict resolution")
            this
        }

        NodeBuilder maybeByConstraint() {
            ignoreReasonPrefixes.add("constraint")
            this
        }

        NodeBuilder maybeSelectedByRule() {
            ignoreReasonPrefixes.add("selected by rule")
            this
        }

        NodeBuilder maybeByReason(String reason) {
            ignoreReasons.add(reason)
            this
        }

        NodeBuilder byConstraint(String reason = null) {
            if (reason == null) {
                reasons << ComponentSelectionCause.CONSTRAINT.defaultReason
            } else {
                reasons << "${ComponentSelectionCause.CONSTRAINT.defaultReason}: $reason".toString()
            }
            this
        }

        NodeBuilder byAncestor() {
            byReason(ComponentSelectionCause.BY_ANCESTOR.defaultReason)
        }

        NodeBuilder byConsistentResolution(String source) {
            byConstraint("version resolved in configuration ':$source' by consistent resolution")
        }

        /**
         * Marks that this node was selected by the given reason
         */
        NodeBuilder byReasons(List<String> reasons) {
            this.reasons.addAll(reasons)
            this
        }

        NodeBuilder variant(String name, Map<String, ?> attributes = [:]) {
            configuration(name)
            checkVariant = true
            String variantName = name
            Map<String, String> stringAttributes = attributes.collectEntries { entry ->
                [entry.key, entry.value instanceof Closure ? entry.value.call() : entry.value.toString()]
            }
            this.variants << new Variant(name: variantName, attributes: stringAttributes)
            this
        }

        void setConfiguration(String configuration) {
            configurations.clear()
            configurations.add(configuration)
        }

        void configuration(String configuration) {
            configurations << configuration
        }

        void setFirstLevelConfigurations(Collection<String> firstLevelConfigurations) {
            this.firstLevelConfigurations = firstLevelConfigurations as Set
        }

        Set<String> getFirstLevelConfigurations() {
            firstLevelConfigurations == null ? configurations : firstLevelConfigurations
        }
    }

    /**
     * Enables Maven derived variants, as if the Java plugin was applied
     */
    void addDefaultVariantDerivationStrategy() {
        buildFile << """
            allprojects { dependencies.components.variantDerivationStrategy = new org.gradle.internal.component.external.model.JavaEcosystemVariantDerivationStrategy() }
        """
    }

    void addJavaEcosystem() {
        buildFile << """
            allprojects {
                apply plugin: 'org.gradle.jvm-ecosystem'
            }
        """
    }
}
