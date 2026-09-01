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
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Path
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
        def graph = new GraphBuilder()
        closure.resolveStrategy = Closure.DELEGATE_ONLY
        closure.delegate = graph
        closure.call()

        def root = graph.root
        if (root == null) {
            throw new IllegalArgumentException("No root node defined")
        }

        def configDetailsFile = getResultFile(Path.path(path))
        def configDetails = configDetailsFile.text.readLines()

        def actualRoot = findLines(configDetails, 'root').first()
        def expectedRoot = "[${root.type}][id:${root.id}][mv:${root.moduleVersionId}][reason:${root.reason}]".toString()
        assert actualRoot.startsWith(expectedRoot)

        Map<String, Set<Variant>> actualVariants = parseVariants(findLines(configDetails, 'variant'))
        List<String> actualComponents = findLines(configDetails, 'component')
        List<ParsedComponent> expectedComponents = graph.nodes.collect { NodeBuilder baseNode ->
            new ParsedComponent(type: baseNode.type,
                id: baseNode.id,
                module: baseNode.moduleVersionId,
                reasons: baseNode.allReasons,
                variants: baseNode.variants,
                ignoreReasons: baseNode.ignoreReasons,
                ignoreReasonPrefixes: baseNode.ignoreReasonPrefixes)
        }
        compareComponents("components in graph", parseComponents(actualComponents, actualVariants), expectedComponents)

        def actualEdges = findLines(configDetails, 'dependency')
        def expectedEdges = graph.edges.collect { "${it.constraint ? '[constraint]' : ''}[from:${it.from.id}][${it.requested}->${it.selected.id}]" }
        compare("edges in graph", actualEdges, expectedEdges)

        def expectedFiles = root.files + graph.artifactNodes.collect { it.fileName }
        def expectedArtifacts = graph.artifactNodes.collect { "${it.fileName} (${it.componentId})" } + graph.files as List<String>

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

    List<String> findLines(List<String> lines, String prefix) {
        return lines.findAll { it.startsWith(prefix + ":") }.collect { it.substring(prefix.length() + 1) }
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
                .add(new Variant(name: parts[1], attributes: attributes))
        }
        return result
    }

    List<ParsedComponent> parseComponents(List<String> components, Map<String, Set<Variant>> variants) {
        components.collect { parseComponent(it, variants) }
    }

    ParsedComponent parseComponent(String line, Map<String, Set<Variant>> variants) {
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
        Set<Variant> ownedVariants = variants[id]
        assert ownedVariants != null: "Component '$id' is in the graph, but owns no variant"
        new ParsedComponent(type: type, id: id, module: module, reasons: reasons, variants: ownedVariants)
    }

    static class ParsedComponent {
        String type
        String id
        String module
        Set<String> reasons
        boolean ignoreRequested
        Set<String> ignoreReasons
        Set<String> ignoreReasonPrefixes
        Set<Variant> variants = []

        boolean diff(ParsedComponent actual, StringBuilder sb) {
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
                errors << "Expected reasons ${reasons} but was: ${actualReasons}"
            }
            this.variants.each { variant ->
                def actualVariant = actual.variants.find { it.name == variant.name }
                if (!actualVariant) {
                    errors << "Expected variant name $variant, but wasn't found in: $actual.variants.name"
                } else {
                    if (variant.attributes != null && variant.attributes != actualVariant.attributes) {
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

            throw new ComparisonFailure("Result contains unexpected $compType\n\nMissing from actual:\n" + missingFromActual + "\nMissing from expected:\n" + missingFromExpected + "\n\n", expectedFormatted, actualFormatted);
        }
    }

    static class GraphBuilder {
        private final Map<String, NodeBuilder> nodes = [:]
        private NodeBuilder root

        Collection<NodeBuilder> getNodes() {
            return nodes.values()
        }

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

        Set<ExpectedArtifact> getArtifactNodes() {
            Set<NodeBuilder> result = new LinkedHashSet<>()
            visitNodes(this.root, result)
            return result.collect { it.artifacts }.flatten()
        }

        Set<String> getFiles() {
            Set<NodeBuilder> result = new LinkedHashSet<>()
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
            def node = nodes[moduleVersion]
            if (!node) {
                node = new NodeBuilder(type, id, moduleVersion, attrs, this)
                nodes[moduleVersion] = node
            }
            if (attrs.variant) {
                node.variant(attrs.variant)
            }
            return node
        }
    }

    private static class EdgeBuilder {
        final String requested
        final NodeBuilder from
        NodeBuilder selected
        boolean constraint

        EdgeBuilder(NodeBuilder from, String requested, NodeBuilder selected) {
            this.from = from
            this.requested = requested
            this.selected = selected
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
        private boolean implicitArtifact = true
        final List<String> files = []
        private final Set<ExpectedArtifact> artifacts = new LinkedHashSet<>()
        private final Set<String> reasons = new LinkedHashSet<String>()
        private boolean ignoreRequested
        private final Set<String> ignoreReasons = new HashSet<>()
        private final Set<String> ignoreReasonPrefixes = new HashSet<>()
        Set<Variant> variants = []

        NodeBuilder(String type, String id, String moduleVersionId, Map attrs, GraphBuilder graph) {
            this.graph = graph
            this.group = attrs.group
            this.module = attrs.module
            this.version = attrs.version
            this.moduleVersionId = moduleVersionId
            this.id = id
            this.type = type
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
         * Like {@link #edge(String, String, Closure)}, but to be used in cases where the requested or selected
         * module contains a {@code :} and cannot be passed as a single string.
         */
        NodeBuilder edge(Map<String, String> requested, Map<String, String> selected, @DelegatesTo(NodeBuilder) Closure cl = {}) {
            def group = selected.group
            def module = selected.module
            def version = selected.version

            def moduleVersionId = "${group}:${module}:${version}"
            def node = graph.node("module:${moduleVersionId},${group}:${module}", moduleVersionId, moduleVersionId, [group: group, module: module, version: version])
            deps << new EdgeBuilder(this, "${requested.group}:${requested.module}:${requested.version}", node)
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
                legacyName: attributes.legacyName
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

        NodeBuilder variant(String name, Map<String, ?> attributes = null) {
            Map<String, String> stringAttributes = attributes != null ? attributes.collectEntries { entry ->
                [entry.key, entry.value instanceof Closure ? entry.value.call() : entry.value.toString()]
            } : null
            this.variants << new Variant(name: name, attributes: stringAttributes)
            this
        }
    }

}
