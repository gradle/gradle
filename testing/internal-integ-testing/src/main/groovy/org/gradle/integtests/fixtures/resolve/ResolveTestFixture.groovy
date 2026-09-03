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
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Path
import org.jspecify.annotations.Nullable
import org.junit.ComparisonFailure

/**
 * A test fixture that produces Groovy build logic that resolves a configuration
 * and records the state of the dependency graph and resolved artifacts to a file.
 * After execution, this test fixture can validate the result of resolution against
 * an expected graph shape.
 */
class ResolveTestFixture {

    private final TestFile rootDir

    /**
     * @param rootDir The root directory of the build that this test fixture will be used in.
     */
    ResolveTestFixture(TestFile rootDir) {
        this.rootDir = rootDir
    }

    /**
     * Create a Groovy code snippet that creates tasks to resolve the
     * given configurations. The code snippet is intended to be inlined
     * into a Groovy project buildscript.
     */
    static String configureProject(String first, String... rest) {
        return """
            ${configureCheckTask()}
            ${configureProjectTasks(first, rest)}
        """
    }

    /**
     * Create a Groovy code snippet that creates tasks to resolve the
     * given configurations in all projects of a build. The code snippet
     * is intended to be inlined into a Groovy settings script.
     *
     * @deprecated Prefer {@link #configureProject(String, String...)}. This method
     * should only be used when the test setup otherwise prevents the more targeted
     * project-specific method, for example when a parent project configures child
     * projects inline, and a configuration in the child project is being resolved.
     */
    @Deprecated
    static String configureSettings(String first, String... rest) {
        return """
            ${configureCheckTask()}
            gradle.lifecycle.beforeProject {
                ${configureProjectTasks(first, rest)}
            }
        """
    }

    private static String configureProjectTasks(String first, String... rest) {
        List<String> allTaskDefinitions = []

        if (rest.length == 0) {
            allTaskDefinitions << configureTask(first, "checkDeps")
        } else {
            allTaskDefinitions << configureTask(first)
            for (String configurationName : rest) {
                allTaskDefinitions << configureTask(configurationName)
            }
        }

        allTaskDefinitions.join("\n")
    }

    private static String configureTask(
        String configurationName,
        String taskName = "check${configurationName.capitalize()}"
    ) {
        """
            tasks.register("${taskName}", GenerateGraphTask) {
                def configuration = configurations.${configurationName}

                it.outputFile = file("\${buildDir}/last-graph.txt")

                configureFrom(configuration)
            }
        """
    }

    private static String configureCheckTask() {
        @GroovyBuildScriptLanguage
        String text = '''
            abstract class GenerateGraphTask extends DefaultTask {
                @Internal
                File outputFile

                @Internal
                abstract Property<ResolvedComponentResult> getRootComponent()

                @Internal
                abstract Property<ResolvedVariantResult> getRootVariant()

                @Internal
                abstract ConfigurableFileCollection getFiles()

                @Internal
                FileCollection incomingFiles

                @Internal
                ArtifactCollection incomingArtifacts

                @Internal
                FileCollection artifactViewFiles

                @Internal
                ArtifactCollection artifactViewArtifacts

                @Internal
                FileCollection lenientArtifactViewFiles

                @Internal
                ArtifactCollection lenientArtifactViewArtifacts

                @Internal
                abstract Property<String> getConfigurationCacheUnsafeLines()

                GenerateGraphTask() {
                    outputs.upToDateWhen { false }
                }

                def configureFrom(Configuration configuration) {
                    rootComponent = configuration.incoming.resolutionResult.rootComponent
                    rootVariant = configuration.incoming.resolutionResult.rootVariant
                    files.from(configuration)

                    incomingFiles = configuration.incoming.files
                    incomingArtifacts = configuration.incoming.artifacts

                    artifactViewFiles = configuration.incoming.artifactView { }.files
                    artifactViewArtifacts = configuration.incoming.artifactView { }.artifacts

                    lenientArtifactViewFiles = configuration.incoming.artifactView { it.lenient = true }.files
                    lenientArtifactViewArtifacts = configuration.incoming.artifactView { it.lenient = true }.artifacts

                    inputs.files configuration

                    configurationCacheUnsafeLines.set(project.provider {
                        def stringWriter = new StringWriter()
                        def pw = new PrintWriter(stringWriter)

                        try {
                            // ResolvedConfiguration
                            configuration.resolvedConfiguration.firstLevelModuleDependencies.each {
                                pw.println("first-level:[${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}]")
                            }
                            visitLegacyGraph("resolved", configuration.resolvedConfiguration.firstLevelModuleDependencies, pw, new HashSet<>())
                            configuration.resolvedConfiguration.resolvedArtifacts.each {
                                pw.println("artifact:[${it.moduleVersion.id}][${it.name}:${it.classifier}:${it.extension}:${it.type}] (${it.id.componentIdentifier.displayName})")
                            }
                            configuration.resolvedConfiguration.resolvedArtifacts.each {
                                writeFile("file-artifact-resolved-config", pw, it.file)
                            }

                            // LenientConfiguration
                            configuration.resolvedConfiguration.lenientConfiguration.firstLevelModuleDependencies.each {
                                pw.println("lenient-first-level:[${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}]")
                            }
                            visitLegacyGraph("lenient", configuration.resolvedConfiguration.lenientConfiguration.firstLevelModuleDependencies, pw, new HashSet<>())
                            configuration.resolvedConfiguration.lenientConfiguration.artifacts.each {
                                pw.println("lenient-artifact:[${it.moduleVersion.id}][${it.name}:${it.classifier}:${it.extension}:${it.type}] (${it.id.componentIdentifier.displayName})")
                            }
                            configuration.resolvedConfiguration.lenientConfiguration.artifacts.each {
                                writeFile("file-artifact-lenient-config", pw, it.file)
                            }
                        } catch (Exception ignored) {
                            // We will emit the failure later when the the fields on the task are read.
                        }

                        pw.flush()
                        stringWriter.toString()
                    })
                }

                @TaskAction
                void generateOutput() {
                    outputFile.parentFile.mkdirs()
                    //noinspection GroovyMissingReturnStatement
                    outputFile.withPrintWriter { writer ->
                        GraphNode rootNode = createNode(rootComponent.get(), rootVariant.get())
                        Set<GraphNode> nodes = collectAllNodes(rootNode)

                        // These are always checked regardless of whether or not building artifacts is requested
                        writeGraphStructure(writer, rootNode, nodes)

                        incomingArtifacts.artifacts.each {
                            writeArtifact("incoming-artifact-artifact", writer, it)
                        }

                        files.each {
                            writeFile("file-file", writer, it)
                        }
                        files.filter { true }.each {
                            writeFile("file-filtered", writer, it)
                        }

                        incomingFiles.each {
                            writeFile("incoming-file", writer, it)
                        }
                        incomingArtifacts.each {
                            writeArtifact("incoming-artifact", writer, it)
                        }
                        incomingArtifacts.resolvedArtifacts.get().each {
                            writeArtifact("incoming-resolved-artifact", writer, it)
                        }
                        incomingArtifacts.artifactFiles.each {
                            writeFile("incoming-artifact-file", writer, it)
                        }

                        artifactViewFiles.each {
                            writeFile("artifact-view-file", writer, it)
                        }
                        artifactViewArtifacts.each {
                            writeArtifact("artifact-view-artifact", writer, it)
                        }
                        artifactViewFiles.files.each {
                            writeFile("artifact-view-file-file", writer, it)
                        }
                        artifactViewArtifacts.artifacts.each {
                            writeArtifact("artifact-view-artifact-artifact", writer, it)
                        }
                        artifactViewArtifacts.resolvedArtifacts.get().each {
                            writeArtifact("artifact-view-resolved-artifact", writer, it)
                        }
                        artifactViewArtifacts.artifactFiles.each {
                            writeFile("artifact-view-artifact-file", writer, it)
                        }

                        lenientArtifactViewFiles.each {
                            writeFile("lenient-artifact-view-file", writer, it)
                        }
                        lenientArtifactViewArtifacts.each {
                            writeArtifact("lenient-artifact-view-artifact", writer, it)
                        }
                        lenientArtifactViewFiles.files.each {
                            writeFile("lenient-artifact-view-file-file", writer, it)
                        }
                        lenientArtifactViewArtifacts.artifacts.each {
                            writeArtifact("lenient-artifact-view-artifact-artifact", writer, it)
                        }
                        lenientArtifactViewArtifacts.resolvedArtifacts.get().each {
                            writeArtifact("lenient-artifact-view-resolved-artifact", writer, it)
                        }
                        lenientArtifactViewArtifacts.artifactFiles.each {
                            writeFile("lenient-artifact-view-artifact-file", writer, it)
                        }

                        // ResolvedConfiguration and LenientConfiguration (captured during configuration)
                        writer.print(configurationCacheUnsafeLines.get())
                    }
                }

                protected void visitLegacyGraph(String prefix, Collection<ResolvedDependency> nodes, PrintWriter writer, Set<ResolvedDependency> visited) {
                    for (ResolvedDependency node : nodes) {
                        if (!visited.add(node)) {
                            continue
                        }
                        writer.println(prefix + "-resolved-dependency:[${node.moduleGroup}:${node.moduleName}:${node.moduleVersion}]")
                        for (ResolvedDependency child : node.children) {
                            writer.println(prefix + "-resolved-dependency-edge:[${node.moduleGroup}:${node.moduleName}:${node.moduleVersion}]->[${child.moduleGroup}:${child.moduleName}:${child.moduleVersion}]")
                        }
                        visitLegacyGraph(prefix, node.children, writer, visited)
                    }
                }

                /**
                 * Walks the variant graph, returning every node reachable from the given root node.
                 */
                protected Set<GraphNode> collectAllNodes(GraphNode rootNode) {
                    Map<ResolvedVariantResult, GraphNode> nodes = new LinkedHashMap<>()
                    Deque<GraphNode> queue = new ArrayDeque<>()

                    nodes.put(rootNode.variant, rootNode)
                    queue.addLast(rootNode)

                    while (!queue.isEmpty()) {
                        GraphNode node = queue.removeFirst()
                        for (ResolvedDependencyResult edge : node.outgoingEdges) {
                            ResolvedVariantResult target = edge.resolvedVariant
                            if (!nodes.containsKey(target)) {
                                GraphNode targetNode = createNode(edge.selected, target)
                                nodes.put(target, targetNode)
                                queue.addLast(targetNode)
                            }
                        }
                    }

                    return new LinkedHashSet<>(nodes.values())
                }

                protected GraphNode createNode(ResolvedComponentResult component, ResolvedVariantResult variant) {
                    List<ResolvedDependencyResult> outgoingEdges = component.getDependenciesForVariant(variant).collect { DependencyResult edge ->
                        if (!(edge instanceof ResolvedDependencyResult)) {
                            throw new IllegalStateException("Expected all dependencies to resolve, but ${edge} did not.")
                        }
                        return (ResolvedDependencyResult) edge
                    }

                    return new GraphNode(component, variant, outgoingEdges)
                }

                protected void writeGraphStructure(PrintWriter writer, GraphNode rootNode, Set<GraphNode> nodes) {
                    // Sanity check that two nodes do not share the same name, as we this fixture keys nodes by their display name.
                    nodes.groupBy { it.variant.owner }.each { ComponentIdentifier owner, List<GraphNode> owned ->
                        List<String> names = owned.collect { it.variant.displayName }
                        assert names.size() == names.unique(false).size() : "Component ${owner} has multiple variants named the same: ${names}"
                    }

                    writer.println("root-variant:${formatNode(rootNode.variant)}")
                    nodes.each { GraphNode node ->
                        writer.println("variant:${formatNode(node.variant)}@@${formatAttributes(node.variant.attributes)}@@${formatCapabilities(node.variant.capabilities)}")
                        node.outgoingEdges.each { ResolvedDependencyResult edge ->
                            writer.println("variant-dependency:${edge.constraint}@@${formatNode(node.variant)}@@${edge.requested}@@${formatNode(edge.resolvedVariant)}")
                        }
                    }

                    // The component graph is an overlay on the variant graph. A component is the owner of one or
                    // more variants, and its dependencies are those of all of its variants. It is written out so
                    // that deriving one from the other can be verified.
                    Set<ResolvedComponentResult> components = new LinkedHashSet<>(nodes.collect { it.component })
                    writer.println("root:${formatComponent(rootNode.component)}")
                    components.each { ResolvedComponentResult component ->
                        writer.println("component:${formatComponent(component)}")
                        component.dependencies.each { DependencyResult edge ->
                            writer.println("dependency:${edge.constraint ? '[constraint]' : ''}[from:${edge.from.id}][${edge.requested}->${edge.selected.id}]")
                        }
                    }
                }

                protected String formatComponent(ResolvedComponentResult result) {
                    String type
                    if (result.id instanceof ProjectComponentIdentifier) {
                        type = "project:${org.gradle.util.Path.path(result.id.build.buildPath).append(org.gradle.util.Path.path(result.id.projectPath))}"
                    } else if (result.id instanceof ModuleComponentIdentifier) {
                        type = "module:${result.id.group}:${result.id.module}:${result.id.version},${result.id.moduleIdentifier.group}:${result.id.moduleIdentifier.name}"
                    } else {
                        type = "other"
                    }
                    "[$type][id:${result.id}][mv:${result.moduleVersion}][reason:${formatReason(result.selectionReason)}]"
                }

                /**
                 * Identifies a node of the variant graph, as the component that owns it and the variant it is.
                 */
                protected String formatNode(ResolvedVariantResult variant) {
                    return "${variant.owner.displayName}@@${variant.displayName}"
                }

                protected String formatCapabilities(List<Capability> capabilities) {
                    capabilities.collect {
                        "${it.group}:${it.name}:${it.version}"
                    }.sort().join(',')
                }

                protected String formatAttributes(AttributeContainer attributes) {
                    attributes.keySet().collect {
                        "$it.name=${attributes.getAttribute(it as Attribute<Object>)}"
                    }.sort().join(',')
                }

                protected String formatReason(ComponentSelectionReason reason) {
                    def reasons = reason.descriptions.collect {
                        def message
                        if (it.hasCustomDescription() && it.cause != ComponentSelectionCause.REQUESTED) {
                            message = "${it.cause.defaultReason}: ${it.description}"
                        } else {
                            message = it.description
                        }
                        message.readLines().join(" ")
                    }.join('!!')
                    return reasons
                }

                protected void writeFile(String linePrefix, PrintWriter writer, File file) {
                    writer.println("$linePrefix:${file.name}")
                }

                protected void writeArtifact(String linePrefix, PrintWriter writer, ResolvedArtifactResult artifact) {
                    writer.println("$linePrefix:${artifact.file.name} (${artifact.id.componentIdentifier.displayName})")
                }

                /**
                 * A variant of the graph, the component that owns it, and its outgoing edges.
                 */
                record GraphNode(
                    ResolvedComponentResult component,
                    ResolvedVariantResult variant,
                    List<ResolvedDependencyResult> outgoingEdges
                ) {}
            }
        '''
        return text
    }

    def getResultFile(Path projectPath) {
        def currentDir = rootDir
        for (String part : projectPath.segments()) {
            currentDir = currentDir.file(part)
        }
        currentDir.file("build/last-graph.txt")
    }

    /**
     * Verifies the result of executing tasks created by this test fixture.
     *
     * That task writes information about the graph, files and artifacts to a flat file accessible here via {@link #getResultFile(Path)}.
     * This method reads that file (the actual result) and compares it to the expected result - the graph info provided to this fixture via
     * the DSL supplied as an argument.
     *
     * @param path The path to the project containing the result file, e.g. ":" or ":subproject"
     * @param closure a closure containing DSL that configures the expected graph
     */
    void expectGraph(String path= ":", @DelegatesTo(GraphBuilder) Closure closure) {
        def graphBuilder = new GraphBuilder()
        closure.resolveStrategy = Closure.DELEGATE_ONLY
        closure.delegate = graphBuilder
        closure.call()

        if (graphBuilder.root == null) {
            throw new IllegalArgumentException("No root node defined")
        }

        def configDetailsFile = getResultFile(Path.path(path))
        def configDetails = configDetailsFile.text.readLines()

        Map<String, Set<Variant>> actualVariants = parseVariants(findLines(configDetails, 'variant'))
        String rootVariantName = parseNodeId(findLines(configDetails, 'root-variant').first()).variantName
        def graph = graphBuilder.normalize(actualVariants, rootVariantName)
        NodeBuilder root = graph.root

        Map<String, List<NodeBuilder>> nodesByComponent = graph.nodesByVariantId.values().groupBy { it.id }
        Map<String, List<NodeBuilder>> constraintsByComponent = graph.constraintTargetsByComponentId.values().groupBy { it.id }
        Set<String> expectedComponentIds = nodesByComponent.keySet() + constraintsByComponent.keySet()

        def actualRoot = findLines(configDetails, 'root').first()
        def expectedRoot = "[${root.type}][id:${root.id}][mv:${root.moduleVersionId}][reason:${componentReasons(nodesByComponent[root.id], constraintsByComponent[root.id] ?: [], root).join('!!')}]".toString()
        assert actualRoot.startsWith(expectedRoot)

        Map<String, Set<String>> ignoredReasonPrefixes = [:]
        List<ParsedComponent> expectedComponents = expectedComponentIds.collect { String id ->
            List<NodeBuilder> nodes = nodesByComponent[id]
            assert nodes != null: "Component '$id' is only constrained, but has no hard edges targeting it."
            Set<String> moduleVersions = nodes.collect { it.moduleVersionId } as Set
            assert moduleVersions.size() == 1: "Component '$id' is declared with several module versions ${moduleVersions.sort()}."
            Set<String> types = nodes.collect { it.type } as Set
            assert types.size() == 1: "Component '$id' is declared with several types ${types.sort()}."

            List<NodeBuilder> constrained = constraintsByComponent[id] ?: []
            ignoredReasonPrefixes[id] = (nodes + constrained).collectMany { it.ignoreReasonPrefixes }.toSet()
            new ParsedComponent(
                types.first(),
                id,
                moduleVersions.first(),
                componentReasons(nodes, constrained, root),
                nodes.collect { it.variant }.toSet(),
            )
        }

        def actualComponents = findLines(configDetails, 'component').collect { parseComponent(it, actualVariants, ignoredReasonPrefixes) }
        compareComponents("components in graph", actualComponents, expectedComponents)

        def actualEdges = findLines(configDetails, 'dependency')
        def expectedEdges = graph.edges.collect { "${it.constraint ? '[constraint]' : ''}[from:${it.from.id}][${it.requested}->${it.selected.id}]" }
        compare("edges in graph", actualEdges, expectedEdges)

        def expectedFiles = root.files + graph.artifactNodes.collect { it.fileName }
        def expectedArtifacts = graph.artifactNodes.collect { "${it.fileName} (${it.componentId})" } + graph.fileDependencies as List<String>

        def actualArtifacts = findLines(configDetails, 'incoming-artifact-artifact')
        compare("incoming.artifacts.artifacts", actualArtifacts, expectedArtifacts)

        def actualFiles = findLines(configDetails, 'file-file')
        compare("files", actualFiles, expectedFiles)

        actualFiles = findLines(configDetails, 'file-filtered')
        compare("files (filtered)", actualFiles, expectedFiles)

        actualFiles = findLines(configDetails, 'incoming-file')
        compare("incoming.files", actualFiles, expectedFiles)

        actualArtifacts = findLines(configDetails, 'incoming-artifact')
        compare("incoming.artifacts", actualArtifacts, expectedArtifacts)

        actualArtifacts = findLines(configDetails, 'incoming-resolved-artifact')
        compare("incoming.resolvedArtifacts", actualArtifacts, expectedArtifacts)

        actualFiles = findLines(configDetails, 'incoming-artifact-file')
        compare("incoming.artifacts.artifactFiles", actualFiles, expectedFiles)

        actualFiles = findLines(configDetails, 'artifact-view-file')
        compare("artifactView.files", actualFiles, expectedFiles)

        actualArtifacts = findLines(configDetails, 'artifact-view-artifact')
        compare("artifactView.artifacts", actualArtifacts, expectedArtifacts)

        actualFiles = findLines(configDetails, 'artifact-view-file-file')
        compare("artifactView.files.files", actualFiles, expectedFiles)

        actualArtifacts = findLines(configDetails, 'artifact-view-artifact-artifact')
        compare("artifactView.artifacts.artifacts", actualArtifacts, expectedArtifacts)

        actualArtifacts = findLines(configDetails, 'artifact-view-resolved-artifact')
        compare("artifactView.resolvedArtifacts", actualArtifacts, expectedArtifacts)

        actualFiles = findLines(configDetails, 'artifact-view-artifact-file')
        compare("artifactView.artifacts.artifactFiles", actualFiles, expectedFiles)

        actualFiles = findLines(configDetails, 'lenient-artifact-view-file')
        compare("artifactView.files (lenient)", actualFiles, expectedFiles)

        actualArtifacts = findLines(configDetails, 'lenient-artifact-view-artifact')
        compare("artifactView.artifacts (lenient)", actualArtifacts, expectedArtifacts)

        actualFiles = findLines(configDetails, 'lenient-artifact-view-file-file')
        compare("artifactView.files.files (lenient)", actualFiles, expectedFiles)

        actualArtifacts = findLines(configDetails, 'lenient-artifact-view-artifact-artifact')
        compare("artifactView.artifacts.artifacts (lenient)", actualArtifacts, expectedArtifacts)

        actualArtifacts = findLines(configDetails, 'lenient-artifact-view-resolved-artifact')
        compare("artifactView.resolvedArtifacts (lenient)", actualArtifacts, expectedArtifacts)

        actualFiles = findLines(configDetails, 'lenient-artifact-view-artifact-file')
        compare("artifactView.artifacts.artifactFiles (lenient)", actualFiles, expectedFiles)

        // ResolvedConfiguration and LenientConfiguration checks
        def expectedFirstLevel = root.deps.findAll { !it.constraint }.collect { d ->
            "[${d.selected.moduleVersionId}]"
        } as Set

        def actualFirstLevel = findLines(configDetails, 'first-level') as Set
        compare("first level dependencies", actualFirstLevel, expectedFirstLevel)

        actualFirstLevel = findLines(configDetails, 'lenient-first-level') as Set
        compare("lenient first level dependencies", actualFirstLevel, expectedFirstLevel)

        def expectedResolvedDependencies = graph.nodesWithoutRoot.collect { "[${it.moduleVersionId}]".toString() } as Set

        def actualResolvedDeps = findLines(configDetails, 'resolved-resolved-dependency') as Set
        compare("resolved dependencies in graph", actualResolvedDeps, expectedResolvedDependencies)

        def expectedResolvedEdges = graph.edges
            .findAll { !it.constraint && it.from != root }
            .collect { "[${it.from.moduleVersionId}]->[${it.selected.moduleVersionId}]" } as Set

        def actualResolvedEdges = findLines(configDetails, 'resolved-resolved-dependency-edge')
            .findAll() { !it.contains("[${root.moduleVersionId}]->[") } as Set
        compare("resolved dependency edges", actualResolvedEdges, expectedResolvedEdges)

        def actualLenientResolvedDeps = findLines(configDetails, 'lenient-resolved-dependency') as Set
        compare("lenient resolved dependencies in graph", actualLenientResolvedDeps, expectedResolvedDependencies)

        def actualLenientResolvedEdges = findLines(configDetails, 'lenient-resolved-dependency-edge')
            .findAll() { !it.contains("[${root.moduleVersionId}]->[") } as Set
        compare("lenient resolved dependency edges", actualLenientResolvedEdges, expectedResolvedEdges)

        def expectedLegacyArtifacts = graph.artifactNodes.collect { "[${it.moduleVersionId}][${it.legacyArtifactName}] (${it.componentId})" }

        actualArtifacts = findLines(configDetails, 'artifact')
        compare("artifacts", actualArtifacts, expectedLegacyArtifacts)

        actualArtifacts = findLines(configDetails, 'lenient-artifact')
        compare("lenient artifacts", actualArtifacts, expectedLegacyArtifacts)

        def expectedArtifactOnlyFiles = graph.artifactNodes.collect { it.fileName }

        actualFiles = findLines(configDetails, 'file-artifact-resolved-config')
        compare("resolved configuration artifact files", actualFiles, expectedArtifactOnlyFiles)

        actualFiles = findLines(configDetails, 'file-artifact-lenient-config')
        compare("lenient configuration artifact files", actualFiles, expectedArtifactOnlyFiles)
    }

    private static Set<String> componentReasons(Collection<NodeBuilder> owned, Collection<NodeBuilder> constrained, NodeBuilder root) {
        Set<String> reasons = (owned + constrained).collect { it.reasons }.flatten().toSet()
        if (owned.any { it.is(root) }) {
            reasons.add('root')
        } else if (owned.any { it.requested }) {
            reasons.add('requested')
        }
        return reasons
    }

    List<String> findLines(List<String> lines, String prefix) {
        return lines.findAll { it.startsWith(prefix + ":") }.collect { it.substring(prefix.length() + 1) }
    }

    static record NodeId(
        String componentId,
        String variantName
    ) { }

    static NodeId parseNodeId(String value) {
        List<String> parts = value.split('@@', -1) as List<String>
        assert parts.size() == 2: "Malformed node ID '$value'"
        return new NodeId(parts[0], parts[1])
    }

    /**
     * Parses the variants of the graph, grouped by the ID of the component that owns them.
     */
    Map<String, Set<Variant>> parseVariants(List<String> lines) {
        Map<String, Set<Variant>> result = [:]
        lines.each { line ->
            // owner@@name@@attributes@@capabilities
            List<String> parts = line.split('@@', -1) as List<String>
            assert parts.size() == 4: "Malformed variant '$line'"
            Map<String, String> attributes = parts[2]
                .split(',') // attributes are separated by commas
                .findAll() // only keep non empty entries
                .collectEntries { it.split('=', 2) as List }
            result.computeIfAbsent(parts[0]) { new LinkedHashSet<Variant>() }
                .add(new Variant(parts[1], attributes))
        }
        return result
    }


    ParsedComponent parseComponent(String line, Map<String, Set<Variant>> variants, Map<String, Set<String>> ignoredReasonPrefixes) {
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
        // Reasons the expectation opted out of are dropped here.
        Set<String> ignoredPrefixes = ignoredReasonPrefixes[id] ?: [] as Set<String>
        Set<String> reasons = line.substring(start, idx).split('!!').findAll { String reason ->
            !ignoredPrefixes.any { prefix -> reason.startsWith(prefix) }
        } as Set<String>
        Set<Variant> ownedVariants = variants[id]
        assert ownedVariants != null: "Component '$id' is in the graph, but owns no variant"
        new ParsedComponent(type, id, module, reasons, ownedVariants)
    }

    static record ParsedComponent(
        String type,
        String id,
        String module,
        Set<String> reasons,
        Set<Variant> variants
    ) {

        boolean diff(ParsedComponent actual, StringBuilder sb) {
            List<String> errors = []
            if (type != actual.type) {
                errors.add("Expected type '$type' but was: $actual.type".toString())
            }
            if (id != actual.id) {
                errors.add("Expected ID '$id' but was: $actual.id".toString())
            }
            if (this.module != actual.module) {
                errors.add("Expected module '${this.module}' but was: $actual.module".toString())
            }
            if (actual.reasons != reasons) {
                errors.add("Expected reasons ${reasons} but was: ${actual.reasons}".toString())
            }
            this.variants.each { variant ->
                def actualVariant = actual.variants.find { it.name == variant.name }
                if (!actualVariant) {
                    errors.add("Expected variant name $variant, but wasn't found in: ${actual.variants*.name}".toString())
                } else {
                    if (variant.attributes != null && variant.attributes != actualVariant.attributes) {
                        errors.add("On variant $variant.name, expected attributes $variant.attributes, but was: $actualVariant.attributes".toString())
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

    static void compareComponents(String compType, Collection<ParsedComponent> actual, Collection<ParsedComponent> expected) {
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

            def missingFromActual = Joiner.on("\n").join(expectedSorted - actualSorted)
            def missingFromExpected = Joiner.on("\n").join(actualSorted - expectedSorted)

            throw new ComparisonFailure("Result contains unexpected $compType (expected ${expectedSorted.size()}, was ${actualSorted.size()})\n\nMissing from actual:\n" + missingFromActual + "\nMissing from expected:\n" + missingFromExpected + "\n\nExpected:\n" + expectedFormatted + "\n\nActual:\n" + actualFormatted + "\n\n", expectedFormatted, actualFormatted);
        }

    }

    static class GraphBuilder {

        private final List<NodeBuilder> allDeclarations = []
        private NodeBuilder root

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
            if (Objects.equals(Path.path(projectIdentityPath), Path.ROOT)) {
                def projectName = moduleVersion.split(':')[1]
                return node("project:$projectIdentityPath", "root project '$projectName'", moduleVersion)
            } else {
                return node("project:$projectIdentityPath", "project '$projectIdentityPath'", moduleVersion)
            }
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
                    attrs = [group: parts[0], module: parts[1], version: parts[2], variant: parts[3]]
                    id = "${attrs.group}:${attrs.module}:${attrs.version}"
                    moduleVersionId = id
                }
            }
            return node(type, id, moduleVersionId, attrs)
        }

        NodeBuilder node(String type, String id, String moduleVersion, Map<String, String> attrs) {
            NodeBuilder node = new NodeBuilder(type, id, moduleVersion, attrs, this)
            allDeclarations.add(node)
            if (attrs.variant) {
                node.variant(attrs.variant)
            }
            return node
        }

        /**
         * Resolves the declarations into the nodes of the graph, merging together those that describe the same
         * node or component and retargeting every edge to the merged representation. Nodes that declare no
         * variant are normalized to the single variant of the component they belong to, if there is such
         * a single variant.
         */
        NormalizedGraph normalize(Map<String, Set<Variant>> actualVariants, String rootVariantName) {
            Map<NodeId, NodeBuilder> nodesById = [:]
            Map<String, NodeBuilder> constraintTargetsByComponent = [:]
            Map<NodeBuilder, NodeBuilder> normalizedCounterparts = new IdentityHashMap<>()

            allDeclarations.groupBy { it.id }.each { String componentId, List<NodeBuilder> declarations ->
                declarations.each { NodeBuilder declaration ->
                    if (declaration.constraintTarget) {
                        NodeBuilder targetNode = constraintTargetsByComponent[componentId] ?: declaration
                        if (targetNode.is(declaration)) {
                            constraintTargetsByComponent[componentId] = targetNode
                        } else {
                            targetNode.mergeFrom(declaration)
                        }
                        normalizedCounterparts.put(declaration, targetNode)
                    } else {
                        if (declaration.variant == null) {
                            String resolvedVariant = declaration.is(root) ? rootVariantName : singleVariantOf(componentId, actualVariants).name
                            declaration.variant(resolvedVariant)
                        }
                        Object key = new NodeId(componentId, declaration.variant.name)
                        NodeBuilder normalizedNode = nodesById[key] ?: declaration
                        if (normalizedNode.is(declaration)) {
                            nodesById[key] = normalizedNode
                        } else {
                            normalizedNode.mergeFrom(declaration)
                        }
                        normalizedCounterparts.put(declaration, normalizedNode)
                    }
                }
            }

            // Retarget edges to the normalized from/to nodes.
            nodesById.values().each { NodeBuilder node ->
                node.deps.each { EdgeBuilder edge ->
                    edge.from = normalizedCounterparts.get(edge.from)
                    edge.selected = normalizedCounterparts.get(edge.selected)
                }
            }

            new NormalizedGraph(
                normalizedCounterparts.get(this.root),
                nodesById,
                constraintTargetsByComponent
            )
        }

        private Variant singleVariantOf(String componentId, Map<String, Set<Variant>> actualVariants) {
            Set<Variant> owned = actualVariants[componentId]
            assert owned != null && !owned.isEmpty() : "Component '$componentId' is not in the resolved graph."
            assert owned.size() == 1: "Component '$componentId' owns multiple variants ${owned.sort()}. Specify which one using `variant(...)`."
            return owned.first()
        }

    }

    static record NormalizedGraph(
        NodeBuilder root,
        Map<NodeId, NodeBuilder> nodesByVariantId,
        Map<String, NodeBuilder> constraintTargetsByComponentId
    ) {

        Collection<NodeBuilder> getNodesWithoutRoot() {
            Set<NodeBuilder> nodes = new HashSet<>()
            visitDeps(this.root.deps, nodes, new HashSet<NodeBuilder>())
            return nodes
        }

        private void visitDeps(List<EdgeBuilder> edges, Set<NodeBuilder> nodes, Set<NodeBuilder> seen) {
            for (EdgeBuilder edge : edges) {
                NodeBuilder selected = edge.selected
                if (seen.add(selected)) {
                    nodes.add(selected)
                    visitDeps(selected.deps, nodes, seen)
                }
            }
        }

        /**
         * The artifacts of the graph. The artifacts of a component are deduplicated across its variants, so an
         * artifact declared by several nodes is expected once. A node that declares the same artifact several
         * times expects it that many times, which can happen if multiple artifacts have the same name but different
         * relative paths.
         */
        List<ExpectedArtifact> getArtifactNodes() {
            Set<NodeBuilder> result = new LinkedHashSet<>()
            visitNodes(this.root, result)
            Map<ExpectedArtifact, Integer> counts = new LinkedHashMap<>()
            result.each { NodeBuilder node ->
                List<ExpectedArtifact> declared = (node.artifacts.empty && node.implicitArtifact)
                    ? [new ExpectedArtifact(node.id, node.group, node.module, node.version, null, null, null, null, null, null, null)]
                    : node.artifacts
                declared.countBy { it }.each { ExpectedArtifact artifact, Integer count ->
                    counts[artifact] = Math.max(counts[artifact] ?: 0, count)
                }
            }
            return counts.collectMany { ExpectedArtifact artifact, Integer count -> [artifact] * count }
        }

        private Set<String> getFileDependencies() {
            Set<NodeBuilder> result = new LinkedHashSet<>()
            result.add(this.root)
            visitNodes(this.root, result)
            return result.collectMany { node -> node.files } as Set<String>
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

    }

    private static class EdgeBuilder {
        final String requested
        NodeBuilder from
        NodeBuilder selected
        final boolean constraint

        EdgeBuilder(NodeBuilder from, String requested, NodeBuilder selected, boolean constraint) {
            this.from = from
            this.requested = requested
            this.selected = selected
            this.constraint = constraint
        }
    }

    static record ExpectedArtifact(
        String componentId,
        String group,
        String module,
        String moduleVersion,
        String version,
        String classifier,
        String type,
        String extension,
        String name,
        String declaredFileName,
        String legacyName
    ) {

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

        String getFileName() {
            if (declaredFileName) {
                return declaredFileName
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

    static record Variant(
        String name,
        Map<String, String> attributes
    ) { }

    static class NodeBuilder {
        final List<EdgeBuilder> deps = []
        private final GraphBuilder graph
        final String type
        final String id
        final String moduleVersionId
        final String group
        final String module
        final String version
        private boolean implicitArtifact = true
        final List<String> files = []
        private final List<ExpectedArtifact> artifacts = []
        private final Set<String> reasons = new LinkedHashSet<String>()
        private boolean notRequested
        /**
         * True if this NodeBuilder represents the target of a constraint edge.
         * This would be better modeled by a separate ComponentBuilder.
         */
        private boolean constraintTarget
        private final Set<String> ignoreReasonPrefixes = new HashSet<>()
        /**
         * The variant this node represents or null if this declaration does not specify a variant.
         */
        private @Nullable Variant variant

        NodeBuilder(String type, String id, String moduleVersionId, Map attrs, GraphBuilder graph) {
            this.graph = graph
            this.group = attrs.group
            this.module = attrs.module
            this.version = attrs.version
            this.moduleVersionId = moduleVersionId
            this.id = id
            this.type = type
        }

        /**
         * Merges the given declaration into this one.
         */
        private void mergeFrom(NodeBuilder other) {
            deps.addAll(other.deps)
            artifacts.addAll(other.artifacts)
            files.addAll(other.files)
            reasons.addAll(other.reasons)
            ignoreReasonPrefixes.addAll(other.ignoreReasonPrefixes)
            notRequested |= other.notRequested
            implicitArtifact &= other.implicitArtifact
            if (other.variant != null) {
                assert variant == null || variant.name == other.variant.name
                variant = other.variant
            }
        }

        boolean isRequested() {
            return !notRequested
        }

        /**
         * Defines an edge from the current node to the given external module node. The closure delegates to a {@link NodeBuilder} instance that represents the target node.
         */
        NodeBuilder module(String moduleVersionId, @DelegatesTo(NodeBuilder) Closure cl = {}) {
            def node = graph.moduleNode(moduleVersionId)
            addOutgoingEdge(node.id, node)
            applyTo(node, cl)
            return node
        }

        /**
         * Defines an edge from the current node to the given module node with a unique snapshot version.
         */
        NodeBuilder snapshot(String moduleVersionId, String timestamp, String requestedVersion = null) {
            def id = moduleVersionId + ":" + timestamp
            def parts = moduleVersionId.split(':')
            assert parts.length == 3
            String group = parts[0]
            String name = parts[1]
            String version = parts[2]
            Map<String, String> attrs = [group: group, module: name, version: version]
            def node = graph.node("module:$moduleVersionId,$group:$name", id, moduleVersionId, attrs)
            addOutgoingEdge(requestedVersion ? "${group}:${name}:${requestedVersion}" : moduleVersionId, node)
            return node
        }

        /**
         * Defines an edge from the current node to the given project node. The closure delegates to a {@link NodeBuilder} instance that represents the target node.
         */
        NodeBuilder project(String projectIdentityPath, String moduleVersion, @DelegatesTo(NodeBuilder) Closure cl = {}) {
            def node = graph.projectNode(projectIdentityPath, moduleVersion)
            addOutgoingEdge(node.id, node)
            applyTo(node, cl)
            return node
        }

        /**
         * Defines a constraint edge that targets the given module component.
         */
        NodeBuilder constraint(String requested, String selectedModuleVersionId = requested, @DelegatesTo(NodeBuilder) Closure cl = {}) {
            def node = graph.moduleNode(selectedModuleVersionId)
            addOutgoingEdge(requested, node, true)
            applyTo(node, cl)
            return node
        }

        /**
         * Defines a constraint edge that targets the given project component.
         */
        NodeBuilder constraint(String requested, String selectedProjectIdentityPath, String selectedModuleVersionId, @DelegatesTo(NodeBuilder) Closure cl = {}) {
            def node = graph.projectNode(selectedProjectIdentityPath, selectedModuleVersionId)
            addOutgoingEdge(requested, node, true)
            applyTo(node, cl)
            return node
        }

        /**
         * Defines an edge from the current node to the given module node. The closure delegates to a {@link NodeBuilder} instance that represents the target node.
         */
        NodeBuilder edge(String requested, String selectedModuleVersionId, @DelegatesTo(NodeBuilder) Closure cl = {}) {
            def node = graph.moduleNode(selectedModuleVersionId)
            addOutgoingEdge(requested, node)
            applyTo(node, cl)
            return node
        }

        /**
         * Like {@link #edge(String, String, Closure)}, but to be used in cases where the requested or selected
         * module contains a {@code :} and cannot be passed as a single string.
         */
        NodeBuilder edge(Map<String, String> requested, Map<String, String> selected, @DelegatesTo(NodeBuilder) Closure cl = {}) {
            def group = selected.group
            def module = selected.module
            def version = selected.version

            def moduleVersionId = "${group}:${module}:${version}"
            def node = graph.node("module:${moduleVersionId},${group}:${module}", moduleVersionId, moduleVersionId, [group: group, module: module, version: version])
            addOutgoingEdge("${requested.group}:${requested.module}:${requested.version}", node)
            applyTo(node, cl)
            return node
        }

        /**
         * Defines an edge from the current node to the given project node. The closure delegates to a {@link NodeBuilder} instance that represents the target node.
         */
        NodeBuilder edge(String requested, String selectedProjectIdentityPath, String selectedModuleVersionId, @DelegatesTo(NodeBuilder) Closure cl = {}) {
            def node = graph.projectNode(selectedProjectIdentityPath, selectedModuleVersionId)
            addOutgoingEdge(requested, node)
            applyTo(node, cl)
            return node
        }

        private void addOutgoingEdge(String requested, NodeBuilder selected, boolean constraint = false) {
            // Constraints target components, and only variants have outgoing edges.
            assertNotConstraintTarget()
            def edge = new EdgeBuilder(this, requested, selected, constraint)
            if (constraint) {
                assert selected.variant == null: "Cannot declare constraint targeting node with declared variant: " + selected.id
                selected.constraintTarget = true
                selected.implicitArtifact = false
            }
            deps.add(edge)
        }

        private static void applyTo(NodeBuilder node, Closure cl) {
            cl.resolveStrategy = Closure.DELEGATE_ONLY
            cl.delegate = node
            cl.call()
        }

        /**
         * Specifies that this node has no artifacts associated with it.
         */
        NodeBuilder noArtifacts() {
            assertNotConstraintTarget()
            implicitArtifact = false
            return this
        }

        /**
         * Specifies an artifact for this node. A default is assumed when none specified
         */
        NodeBuilder artifact(Map<String, String> attributes = [:]) {
            assertNotConstraintTarget()
            def artifact = new ExpectedArtifact(
                id,
                this.group,
                this.module,
                this.version,
                attributes.version,
                attributes.classifier,
                attributes.type,
                attributes.extension,
                attributes.name,
                attributes.fileName,
                attributes.legacyName
            )
            artifacts << artifact
            return this
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
            assertNotConstraintTarget()
            notRequested = true
            this
        }

        NodeBuilder maybeByConflictResolution() {
            ignoreReasonPrefixes.add("conflict resolution")
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

        NodeBuilder variant(String name, Map<String, String> attributes = null) {
            assertNotConstraintTarget()
            assert variant == null : "Node '$id' already declares variant '${variant?.name}'."
            this.variant = new Variant(name, attributes)
            this
        }

        private void assertNotConstraintTarget() {
            assert !constraintTarget: "Only a component can be described for the target of a constraint: " + id
        }

    }

}
