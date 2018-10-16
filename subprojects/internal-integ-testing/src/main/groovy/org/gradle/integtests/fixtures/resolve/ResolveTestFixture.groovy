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
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.test.fixtures.file.TestFile
import org.junit.ComparisonFailure

/**
 * A test fixture that injects a task into a build that resolves a dependency configuration and does some validation of the resulting graph, to
 * ensure that the old and new dependency graphs plus the artifacts and files are as expected and well-formed.
 */
class ResolveTestFixture {
    private final TestFile buildFile
    private final String config
    private String defaultConfig = "default"
    private boolean buildArtifacts = true

    ResolveTestFixture(TestFile buildFile, String config = "compile") {
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
     * Injects the appropriate stuff into the build script.
     */
    void prepare(String additionalContent = '') {
        def existingScript = buildFile.exists() ? buildFile.text : ""
        def start = existingScript.indexOf("RESOLVE_TEST_FIXTURE_START") - 2
        def end = existingScript.indexOf("RESOLVE_TEST_FIXTURE_END") + 25
        if (start >= 0) {
            existingScript = existingScript.substring(0, start) + existingScript.substring(end, existingScript.length())
        }
        def inputs = buildArtifacts ? "it.inputs.files configurations." + config : ""
        buildFile.text = existingScript + """//RESOLVE_TEST_FIXTURE_START
buildscript {
    dependencies.classpath files("${ClasspathUtil.getClasspathForClass(GenerateGraphTask).toURI()}")
}
$additionalContent
allprojects {
    tasks.register("checkDeps", ${GenerateGraphTask.name}) {
        it.outputFile = rootProject.file("\${rootProject.buildDir}/${config}.txt")
        it.configuration = configurations.$config
        it.buildArtifacts = ${buildArtifacts}
        ${inputs}
    }
}//RESOLVE_TEST_FIXTURE_END
"""
    }

    def getResultFile() {
        buildFile.parentFile.file("build/${config}.txt")
    }

    /**
     * Verifies the result of executing the task injected by {@link #prepare()}. The closure delegates to a {@link GraphBuilder} instance.
     */
    void expectGraph(@DelegatesTo(GraphBuilder) Closure closure) {
        def graph = new GraphBuilder(defaultConfig)
        closure.resolveStrategy = Closure.DELEGATE_ONLY
        closure.delegate = graph
        closure.call()

        if (graph.root == null) {
            throw new IllegalArgumentException("No root node defined")
        }

        def configDetailsFile = getResultFile()
        def configDetails = configDetailsFile.text.readLines()

        def actualRoot = findLines(configDetails, 'root').first()
        def expectedRoot = "[id:${graph.root.id}][mv:${graph.root.moduleVersionId}][reason:${graph.root.reason}]".toString()
        assert actualRoot.startsWith(expectedRoot)

        def expectedFirstLevel = graph.root.deps.findAll { !graph.constraints.contains(it.selected) }.collect { "[${it.selected.moduleVersionId}:${it.selected.configuration}]" } as Set

        def actualFirstLevel = findLines(configDetails, 'first-level')
        compare("first level dependencies", actualFirstLevel, expectedFirstLevel)

        actualFirstLevel = findLines(configDetails, 'first-level-filtered')
        compare("filtered first level dependencies", actualFirstLevel, expectedFirstLevel)

        actualFirstLevel = findLines(configDetails, 'lenient-first-level')
        compare("lenient first level dependencies", actualFirstLevel, expectedFirstLevel)

        actualFirstLevel = findLines(configDetails, 'lenient-first-level-filtered')
        compare("lenient filtered first level dependencies", actualFirstLevel, expectedFirstLevel)

        def actualConfigurations = findLines(configDetails, 'configuration') as Set
        def expectedConfigurations = graph.nodesWithoutRoot.collect { "[${it.moduleVersionId}]".toString() } - graph.virtualConfigurations.collect { "[${it}]".toString() }
        compare("configurations in graph", actualConfigurations, expectedConfigurations)

        def actualComponents = findLines(configDetails, 'component')
        def expectedComponents = graph.nodes.collect {
            def variantDetails = it.checkVariant ? "[variant:name:${it.variantName} attributes:${it.variantAttributes}]" : ''
            "[id:${it.id}][mv:${it.moduleVersionId}][reason:${it.reason}]$variantDetails"
        }
        compareNodes("components in graph", parseNodes(actualComponents), parseNodes(expectedComponents))

        def actualEdges = findLines(configDetails, 'dependency')
        def expectedEdges = graph.edges.collect { "[from:${it.from.id}][${it.requested}->${it.selected.id}]" }
        compare("edges in graph", actualEdges, expectedEdges)

        def expectedArtifacts = graph.artifactNodes.collect { "[${it.moduleVersionId}][${it.artifactName}]" }

        def actualArtifacts = findLines(configDetails, 'artifact')
        compare("artifacts", actualArtifacts, expectedArtifacts)

        actualArtifacts = findLines(configDetails, 'lenient-artifact')
        compare("lenient artifacts", actualArtifacts, expectedArtifacts)

        actualArtifacts = findLines(configDetails, 'filtered-lenient-artifact')
        compare("filtered lenient artifacts", actualArtifacts, expectedArtifacts)

        if (buildArtifacts) {
            def expectedFiles = graph.root.files + graph.artifactNodes.collect { it.fileName }

            def actualFiles = findLines(configDetails, 'file')
            compare("files", actualFiles, expectedFiles)

            actualFiles = findLines(configDetails, 'file-incoming')
            compare("incoming.files", actualFiles, expectedFiles)

            actualFiles = findLines(configDetails, 'file-artifact-incoming')
            compare("incoming.artifacts", actualFiles, expectedFiles)

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

    static List<ParsedNode> parseNodes(List<String> nodes) {
        nodes.collect { parseNode(it) }
    }

    static ParsedNode parseNode(String line) {
        int start = 4
        // we look for ][ instead of just ], because of that one test that checks that we can have random characters in id
        // see IvyDynamicRevisionRemoteResolveIntegrationTest. uses latest version from version range with punctuation characters
        int idx = line.indexOf('][')
        String id = line.substring(start, idx) // [id:
        start = idx + 5
        idx = line.indexOf('][', start)
        String module = line.substring(start, idx) // [mv:
        start = idx + 9
        idx = line.indexOf(']', start) // [reason:
        List<String> reasons = line.substring(start, idx).split('!!') as List<String>
        start = idx + 15
        String variant = null
        Map<String, String> attributes = [:]
        if (start<line.length()) {
            idx = line.indexOf(' attributes:', start) // [variant name:
            variant = line.substring(start, idx)
            start = idx + 12
            idx = line.indexOf(']', start) // attributes:
            attributes = line.substring(start, idx)
                .split(',') // attributes are separated by commas
                .findAll() // only keep non empty entries (thank you, split!)
                .collectEntries { it.split('=') as List }
        }
        new ParsedNode(id: id, module:module, reasons: reasons, variant: variant, attributes: attributes)
    }

    static class ParsedNode {
        String id
        String module
        List<String> reasons
        String variant
        Map<String, String> attributes

        boolean diff(ParsedNode actual, StringBuilder sb) {
            List<String> errors = []
            if (id != actual.id) {
                errors << "Expected ID '$id' but was: $actual.id"
            }
            if (module != actual.module) {
                errors << "Expected module '$module' but was: $actual.module"
            }
            if (!actual.reasons.containsAll(reasons)) {
                reasons.each { reason ->
                    if (!actual.reasons.contains(reason)) {
                        errors << "Expected reason '$reason' but wasn't found. Actual reasons: ${actual.reasons}"
                    }
                }
            }
            if (variant) {
                if (variant != actual.variant) {
                    errors << "Expected variant name $variant, but was: $actual.variant"
                }
                if (attributes != actual.attributes) {
                    errors << "Expected variant attributes $attributes, but was: $actual.attributes"
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
            "id: $id, module: $module, reasons: ${reasons}${variant?', variant ' + variant:''}${attributes?', variant attributes' + attributes:''}"
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
            if (!expectedSorted.find { it.id == node.id } ) {
                errors.append("Found unexpected node $node")
            }
        }
        if (errors.length()>0) {
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
        def actualFormatted = Joiner.on("\n").join(actualSorted)
        def expectedFormatted = Joiner.on("\n").join(expectedSorted)
        if (!equals) {
            throw new ComparisonFailure("Result contains unexpected $compType", expectedFormatted, actualFormatted);
        }

    }

    static class GraphBuilder {
        private final Map<String, NodeBuilder> nodes = new LinkedHashMap<>()
        private NodeBuilder root
        private String defaultConfig

        final Set<NodeBuilder> constraints = new LinkedHashSet<>()
        final Set<String> virtualConfigurations = []

        GraphBuilder(String defaultConfig) {
            this.defaultConfig = defaultConfig
        }

        Collection<NodeBuilder> getNodes() {
            return nodes.values()
        }

        Collection<NodeBuilder> getNodesWithoutRoot() {
            def nodes = new HashSet<>()
            visitDeps(root.deps, nodes, new HashSet<>())
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
            visitNodes(root, result)
            return result.collect { it.artifacts }.flatten()
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
            visitEdges(root, seen, result)
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
         *
         * @param projectPath The path of the project to which the graph belongs.
         * @param moduleVersion The module version for this project.
         */
        def root(String projectPath, String moduleVersion, @DelegatesTo(NodeBuilder) Closure cl) {
            if (root != null) {
                throw new IllegalStateException("Root node is already defined")
            }
            root = node("project $projectPath", moduleVersion)
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
            return node(id, moduleVersionId, attrs)
        }

        def node(String id, String moduleVersion, Map attrs) {
            def node = nodes[moduleVersion]
            if (!node) {
                if (!attrs.configuration) {
                    attrs.configuration = defaultConfig
                }
                node = new NodeBuilder(id, moduleVersion, attrs, this)
                nodes[moduleVersion] = node
            }
            return node
        }
    }

    static class EdgeBuilder {
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

    static class ExpectedArtifact {
        String group
        String module
        String version
        String classifier
        String type
        String name

        String getType() {
            return type ?: 'jar'
        }

        String getName() {
            return name ?: module
        }

        ModuleVersionIdentifier getModuleVersionId() {
            return DefaultModuleVersionIdentifier.newId(group, module, version ?: 'unspecified')
        }

        String getArtifactName() {
            return "${getName()}${classifier ? '-' + classifier : ''}.${getType()}"
        }

        String getFileName() {
            return "${getName()}${version ? '-' + version : ''}${classifier ? '-' + classifier : ''}.${getType()}"
        }
    }

    static class NodeBuilder {
        final List<EdgeBuilder> deps = []
        private final GraphBuilder graph
        final String id
        final String moduleVersionId
        final String group
        final String module
        final String version
        String configuration
        private boolean implicitArtifact = true
        final List<String> files = []
        private final Set<ExpectedArtifact> artifacts = new LinkedHashSet<>()
        private final Set<String> reasons = new TreeSet<String>()
        String variantName
        String variantAttributes

        boolean checkVariant

        NodeBuilder(String id, String moduleVersionId, Map attrs, GraphBuilder graph) {
            this.graph = graph
            this.group = attrs.group
            this.module = attrs.module
            this.version = attrs.version
            this.configuration = attrs.configuration
            this.moduleVersionId = moduleVersionId
            this.id = id
            this.variantName = attrs.variantName
            this.variantAttributes = attrs.variantAttributes
        }

        Set<ExpectedArtifact> getArtifacts() {
            return artifacts.empty && implicitArtifact ? [new ExpectedArtifact(group: group, module: module, version: version)] : artifacts
        }

        String getReason() {
            reasons.empty ? (this == graph.root ? 'root' : 'requested') : reasons.join('!!')
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
         * Defines a dependency on a unique snapshot module.
         */
        NodeBuilder snapshot(String moduleVersionId, String timestamp) {
            def id = moduleVersionId + ":" + timestamp
            def parts = moduleVersionId.split(':')
            assert parts.length == 3
            def attrs = [group: parts[0], module: parts[1], version: parts[2]]
            def node = graph.node(id, moduleVersionId, attrs)
            deps << new EdgeBuilder(this, moduleVersionId, node)
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
         * Defines a link between nodes created through a dependency constraint.
         */
        NodeBuilder edgeFromConstraint(String requested, String selectedModuleVersionId, @DelegatesTo(NodeBuilder) Closure cl = {}) {
            def node = graph.node(selectedModuleVersionId, selectedModuleVersionId)
            deps << new EdgeBuilder(this, requested, node)
            if (this == graph.root) {
                graph.constraints.add(node)
            }
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
         * Defines a dependency from the current node to the given node. The closure delegates to a {@link NodeBuilder} instance that represents the target node.
         */
        NodeBuilder edge(String requested, String id, String selectedModuleVersionId, @DelegatesTo(NodeBuilder) Closure cl) {
            def node = graph.node(id, selectedModuleVersionId)
            deps << new EdgeBuilder(this, requested, node)
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
         * Specifies that this node has no artifacts associated with it.
         */
        NodeBuilder noArtifacts() {
            implicitArtifact = false
            return this
        }

        /**
         * Specifies an artifact for this node. A default is assumed when none specified
         */
        NodeBuilder artifact(Map attributes) {
            def artifact = new ExpectedArtifact(group: group, module: module, version: version, name: attributes.name, classifier: attributes.classifier, type: attributes.type)
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

        NodeBuilder byConstraint(String reason) {
            reasons << "${ComponentSelectionCause.CONSTRAINT.defaultReason}: $reason".toString()
            this
        }

        /**
         * Marks that this node was selected by the given reason
         */
        NodeBuilder byReasons(List<String> reasons) {
            this.reasons.addAll(reasons)
            this
        }

        NodeBuilder variant(String name, Map<String, ?> attributes = [:]) {
            checkVariant = true
            variantName = name
            variantAttributes = attributes.collect { "$it.key=$it.value" }.sort().join(',')
            if (id.startsWith("project ")) {
                configuration = variantName
            }
            this
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
}

class GenerateGraphTask extends DefaultTask {
    @Internal
    File outputFile
    @Internal
    Configuration configuration
    @Internal
    boolean buildArtifacts

    @TaskAction
    def generateOutput() {
        outputFile.parentFile.mkdirs()

        outputFile.withPrintWriter { writer ->
            configuration.resolvedConfiguration.firstLevelModuleDependencies.each {
                writer.println("first-level:[${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}:${it.configuration}]")
            }
            configuration.resolvedConfiguration.getFirstLevelModuleDependencies { true }.each {
                writer.println("first-level-filtered:[${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}:${it.configuration}]")
            }
            configuration.resolvedConfiguration.lenientConfiguration.firstLevelModuleDependencies.each {
                writer.println("lenient-first-level:[${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}:${it.configuration}]")
            }
            configuration.resolvedConfiguration.lenientConfiguration.getFirstLevelModuleDependencies { true }.each {
                writer.println("lenient-first-level-filtered:[${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}:${it.configuration}]")
            }
            visitNodes(configuration.resolvedConfiguration.firstLevelModuleDependencies, writer, new HashSet<>())

            def root = configuration.incoming.resolutionResult.root
            writer.println("root:${formatComponent(root)}")
            configuration.incoming.resolutionResult.allComponents.each {
                writer.println("component:${formatComponent(it)}")
            }
            configuration.incoming.resolutionResult.allDependencies.each {
                writer.println("dependency:[from:${it.from.id}][${it.requested}->${it.selected.id}]")
            }
            if (buildArtifacts) {
                configuration.files.each {
                    writer.println("file:${it.name}")
                }
                configuration.incoming.files.each {
                    writer.println("file-incoming:${it.name}")
                }
                configuration.incoming.artifacts.artifacts.each {
                    writer.println("file-artifact-incoming:${it.file.name}")
                }
                configuration.files { true }.each {
                    writer.println("file-filtered:${it.name}")
                }
                configuration.fileCollection { true }.each {
                    writer.println("file-collection-filtered:${it.name}")
                }
                configuration.resolvedConfiguration.files.each {
                    writer.println("file-resolved-config:${it.name}")
                }
                configuration.resolvedConfiguration.getFiles { true }.each {
                    writer.println("file-resolved-config-filtered:${it.name}")
                }
                configuration.resolvedConfiguration.resolvedArtifacts.each {
                    writer.println("file-artifact-resolved-config:${it.file.name}")
                }
                configuration.resolvedConfiguration.lenientConfiguration.files.each {
                    writer.println("file-lenient-config:${it.name}")
                }
                configuration.resolvedConfiguration.lenientConfiguration.getFiles { true }.each {
                    writer.println("file-lenient-config-filtered:${it.name}")
                }
                configuration.resolvedConfiguration.lenientConfiguration.artifacts.each {
                    writer.println("file-artifact-lenient-config:${it.file.name}")
                }
                configuration.resolvedConfiguration.lenientConfiguration.getArtifacts { true }.each {
                    writer.println("file-artifact-lenient-config-filtered:${it.file.name}")
                }
            }

            configuration.resolvedConfiguration.resolvedArtifacts.each {
                writer.println("artifact:[${it.moduleVersion.id}][${it.name}${it.classifier ? "-" + it.classifier : ""}.${it.extension}]")
            }
            configuration.resolvedConfiguration.lenientConfiguration.artifacts.each {
                writer.println("lenient-artifact:[${it.moduleVersion.id}][${it.name}${it.classifier ? "-" + it.classifier : ""}.${it.extension}]")
            }
            configuration.resolvedConfiguration.lenientConfiguration.getArtifacts { true }.each {
                writer.println("filtered-lenient-artifact:[${it.moduleVersion.id}][${it.name}${it.classifier ? "-" + it.classifier : ""}.${it.extension}]")
            }
        }
    }

    def visitNodes(Collection<ResolvedDependency> nodes, PrintWriter writer, Set<ResolvedDependency> visited) {
        for (ResolvedDependency node : nodes) {
            if (!visited.add(node)) {
                continue
            }
            writer.println("configuration:[${node.moduleGroup}:${node.moduleName}:${node.moduleVersion}]")
            visitNodes(node.children, writer, visited)
        }
    }

    def formatComponent(ResolvedComponentResult result) {
        return "[id:${result.id}][mv:${result.moduleVersion}][reason:${formatReason(result.selectionReason)}][variant:${formatVariant(result.variant)}]"
    }

    def formatVariant(ResolvedVariantResult variant) {
        return "name:${variant.displayName} attributes:${formatAttributes(variant.attributes)}"
    }

    def formatAttributes(AttributeContainer attributes) {
        attributes.keySet().collect {
            "$it.name=${attributes.getAttribute(it)}"
        }.sort().join(',')
    }

    def formatReason(ComponentSelectionReasonInternal reason) {
        def reasons = reason.descriptions.collect {
            if (it.hasCustomDescription() && it.cause != ComponentSelectionCause.REQUESTED) {
                "${it.cause.defaultReason}: ${it.description}".replaceAll('\n', ' ')
            } else {
                it.description
            }
        }.join('!!')
        return reasons
    }
}
