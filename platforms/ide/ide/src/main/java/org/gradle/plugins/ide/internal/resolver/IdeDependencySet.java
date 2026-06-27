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
package org.gradle.plugins.ide.internal.resolver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.Artifact;
import org.gradle.api.internal.artifacts.ArtifactViewInternal;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.query.ArtifactResolutionQueryFactory;
import org.gradle.api.internal.artifacts.result.artifact.ArtifactEdge;
import org.gradle.api.internal.artifacts.result.artifact.ArtifactGraph;
import org.gradle.api.internal.artifacts.result.artifact.ArtifactNode;
import org.gradle.api.internal.artifacts.result.artifact.UnresolvedArtifactEdge;
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext;
import org.gradle.api.internal.provider.MergeProvider;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Actions;
import org.gradle.internal.component.model.VariantIdentifier;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.util.ImmutableLists;
import org.gradle.internal.util.ImmutableSets;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal.ClassPathNotation.GRADLE_API;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal.ClassPathNotation.GRADLE_TEST_KIT;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY;

/**
 * Resolves a set of "plus" and "minus" configurations into their default artifacts and
 * any sources/javadoc artifacts that are available. The result is provided to a
 * {@link IdeDependencyVisitor}.
 * <p>
 * Only artifacts in the plus configurations that are not contained in the minus
 * configurations are visited.
 */
@ServiceScope(Scope.Project.class)
public class IdeDependencySet {

    private final JavaModuleDetector javaModuleDetector;
    private final ArtifactResolutionQueryFactory resolutionQueryFactory;
    private final GradleApiSourcesResolver gradleApiSourcesResolver;

    @Inject
    public IdeDependencySet(
        JavaModuleDetector javaModuleDetector,
        ArtifactResolutionQueryFactory resolutionQueryFactory,
        DependencyManagementServices dependencyManagementServices
    ) {
        this.javaModuleDetector = javaModuleDetector;
        this.resolutionQueryFactory = resolutionQueryFactory;
        this.gradleApiSourcesResolver = new GradleApiSourcesResolver(
            dependencyManagementServices.newDetachedResolver(StandaloneDomainObjectContext.ANONYMOUS)
        );
    }

    /**
     * Visits the resolved artifacts of the given plus configurations, without artifacts from
     * variants present in the given minus configurations. Artifacts resolved only from test
     * configurations are marked as test-only.
     */
    public void visit(
        Collection<Configuration> plusConfigurations,
        Collection<Configuration> minusConfigurations,
        Collection<Configuration> testConfigurations,
        boolean inferModulePath,
        IdeDependencyVisitor visitor
    ) {
        if (plusConfigurations.isEmpty()) {
            return;
        }

        boolean offline = visitor.isOffline();
        boolean fetchSources = visitor.downloadSources() && !offline;
        boolean fetchJavadoc = visitor.downloadJavaDoc() && !offline;

        ImmutableSet<String> testConfNames = ImmutableSets.transform(testConfigurations, Configuration::getName);
        Provider<IdeResults> resultsProvider = resultsFor(plusConfigurations, fetchSources, fetchJavadoc, offline)
            .zip(minusKeysFor(minusConfigurations), (plusResults, minusKeys) ->
                processResults(plusResults, minusKeys, testConfNames, fetchSources, fetchJavadoc)
        );

        // At this moment, we perform undeclared dependency resolution.
        // Not ideal. If you are from the future and are reading this,
        // have fun. The whole `visitor` side needs to be made lazy.
        IdeResults results = resultsProvider.get();

        visitResults(visitor, results, inferModulePath);
    }

    /**
     * The results to visit.
     * <p>
     * {@code defaults}, {@code sources}, and {@code javadoc} are each keyed by {@link VariantIdentifier}
     * and dedupe artifacts within a variant by {@link ComponentArtifactIdentifier} (first wins).
     *
     * @param defaults The default artifacts resolved for a given variant.
     * @param sources The sources artifacts resolved for a given variant.
     * @param javadoc The javadoc artifacts resolved for a given variant.
     * @param testOnlyVariants The variants that are only present in the test configurations.
     * @param unresolvedEdges Edges that failed to resolve.
     * @param additionalDocs Additional docs artifacts selected using legacy artifact resolution.
     */
    record IdeResults(
        Map<VariantIdentifier, Map<ComponentArtifactIdentifier, ResolvedArtifactResult>> defaults,
        Map<VariantIdentifier, Map<ComponentArtifactIdentifier, ResolvedArtifactResult>> sources,
        Map<VariantIdentifier, Map<ComponentArtifactIdentifier, ResolvedArtifactResult>> javadoc,
        Set<VariantIdentifier> testOnlyVariants,
        Map<ComponentSelector, UnresolvedArtifactEdge> unresolvedEdges,
        Map<ComponentIdentifier, AdditionalDocs> additionalDocs
    ) {}

    /**
     * A node in the graph and the artifacts resolved for it.
     */
    record NodeAndArtifacts(
        ArtifactNode node,
        Set<ResolvedArtifactResult> artifacts
    ) {}

    /**
     * Additional docs artifacts selected using legacy artifact resolution.
     */
    record AdditionalDocs(
        Set<ResolvedArtifactResult> sources,
        Set<ResolvedArtifactResult> javadoc
    ) {}

    /**
     * The variant identifiers and unresolved component selectors observed across the
     * minus configurations. Contains the keys for data that we need to subtract from the
     * plus results.
     */
    record MinusKeys(
        Set<VariantIdentifier> variants,
        Set<ComponentSelector> unresolvedSelectors
    ) {}

    /**
     * Collects keys to subtract from plus configuration data. Does not perform artifact resolution.
     */
    private static Provider<MinusKeys> minusKeysFor(Collection<Configuration> minusConfigurations) {
        ImmutableList<Provider<List<ArtifactNode>>> configNodes = ImmutableLists.transform(minusConfigurations, conf ->
            getArtifactGraph(conf, null).getAllNodes()
        );
        return new MergeProvider<>(configNodes).map(nodeLists -> {
            Set<VariantIdentifier> variants = new HashSet<>();
            Set<ComponentSelector> unresolvedSelectors = new HashSet<>();
            for (List<ArtifactNode> nodes : nodeLists) {
                for (ArtifactNode node : nodes) {
                    variants.add(node.getId());
                    for (ArtifactEdge edge : node.getDependencies()) {
                        if (edge instanceof UnresolvedArtifactEdge unresolved) {
                            unresolvedSelectors.add(unresolved.getRequested());
                        }
                    }
                }
            }
            return new MinusKeys(variants, unresolvedSelectors);
        });
    }

    /**
     * Resolves sources and javadoc for nodes which could not be resolved using variant reselection.
     * <p>
     * This uses the legacy {@link ArtifactResolutionQueryFactory}. Ivy repos do not have derived
     * variants and therefore do not support variant reselection for selecting sources and javadoc.
     * <p>
     * Eventually, we should add derived variant support for Ivy and remove this method.
     */
    private Map<ComponentIdentifier, AdditionalDocs> resolveAdditionalDocs(
        Set<VariantIdentifier> defaults,
        Set<VariantIdentifier> sources,
        Set<VariantIdentifier> javadoc,
        boolean fetchSources,
        boolean fetchJavadoc
    ) {
        if (!fetchSources && !fetchJavadoc) {
            return Collections.emptyMap();
        }

        Set<ModuleComponentIdentifier> missingComponents = new HashSet<>();
        for (VariantIdentifier variantId : defaults) {
            ComponentIdentifier componentId = variantId.getComponentId();
            if ((componentId instanceof ModuleComponentIdentifier mid) && (
                (fetchSources && !sources.contains(variantId)) ||
                (fetchJavadoc && !javadoc.contains(variantId)))
            ) {
                missingComponents.add(mid);
            }
        }

        if (missingComponents.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Class<? extends Artifact>> types = new ArrayList<>(2);
        if (fetchSources) {
            types.add(SourcesArtifact.class);
        }
        if (fetchJavadoc) {
            types.add(JavadocArtifact.class);
        }

        ArtifactResolutionResult queryResult = resolutionQueryFactory.createArtifactResolutionQuery()
            .forComponents(missingComponents)
            .withArtifacts(JvmLibrary.class, types)
            .execute();

        Map<ComponentIdentifier, AdditionalDocs> additionalDocs = new LinkedHashMap<>();
        for (ComponentArtifactsResult componentResult : queryResult.getResolvedComponents()) {
            if (fetchSources) {
                for (ArtifactResult artifact : componentResult.getArtifacts(SourcesArtifact.class)) {
                    if (artifact instanceof ResolvedArtifactResult resolved) {
                        additionalDocs.computeIfAbsent(
                            componentResult.getId(),
                            id -> new AdditionalDocs(new LinkedHashSet<>(), new LinkedHashSet<>())
                        ).sources().add(resolved);
                    }
                }
            }
            if (fetchJavadoc) {
                for (ArtifactResult artifact : componentResult.getArtifacts(JavadocArtifact.class)) {
                    if (artifact instanceof ResolvedArtifactResult resolved) {
                        additionalDocs.computeIfAbsent(
                            componentResult.getId(),
                            id -> new AdditionalDocs(new LinkedHashSet<>(), new LinkedHashSet<>())
                        ).javadoc().add(resolved);
                    }
                }
            }
        }
        return Collections.unmodifiableMap(additionalDocs);
    }

    private static Provider<List<ConfigurationResults>> resultsFor(
        Collection<Configuration> configurations,
        boolean fetchSources,
        boolean fetchJavadoc,
        boolean skipModuleArtifacts
    ) {
        ImmutableList<Provider<ConfigurationResults>> configurationResults = ImmutableLists.transform(configurations, conf ->
            Providers.zip(
                resolveNodeArtifacts(conf, null, skipModuleArtifacts),
                resolveDocumentationArtifacts(conf, fetchSources, DocsType.SOURCES, skipModuleArtifacts),
                resolveDocumentationArtifacts(conf, fetchJavadoc, DocsType.JAVADOC, skipModuleArtifacts),
                (defaultArtifacts, sourcesArtifacts, javadocArtifacts) ->
                    new ConfigurationResults(conf.getName(), defaultArtifacts, sourcesArtifacts, javadocArtifacts)
            )
        );
        return new MergeProvider<>(configurationResults);
    }

    /**
     * Resolved artifacts for a single configuration.
     */
    record ConfigurationResults(
        String configurationName,
        List<NodeAndArtifacts> defaultArtifacts,
        List<NodeAndArtifacts> sourcesArtifacts,
        List<NodeAndArtifacts> javadocArtifacts
    ) {}

    /**
     * resolve docs for the given configuration if enabled.
     */
    private static Provider<List<NodeAndArtifacts>> resolveDocumentationArtifacts(
        Configuration conf,
        boolean enabled,
        String docsType,
        boolean skipModuleArtifacts
    ) {
        if (!enabled) {
            return Providers.of(Collections.emptyList());
        }

        return resolveNodeArtifacts(conf, attributes -> {
            attributes.attribute(Usage.USAGE_ATTRIBUTE, attributes.named(Usage.class, Usage.JAVA_RUNTIME));
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, attributes.named(Category.class, Category.DOCUMENTATION));
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, attributes.named(Bundling.class, Bundling.EXTERNAL));
            attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, attributes.named(DocsType.class, docsType));
        }, skipModuleArtifacts);
    }

    private static Provider<List<NodeAndArtifacts>> resolveNodeArtifacts(Configuration conf, @Nullable Action<AttributeContainer> action, boolean skipModuleArtifacts) {
        ArtifactGraph graph = getArtifactGraph(conf, action);
        Spec<ArtifactNode> filter = skipModuleArtifacts
            ? node -> !(node.getId().getComponentId() instanceof ModuleComponentIdentifier)
            : Specs.satisfyAll();

        // TODO: HACK ALERT!
        // We chain the result of resolving the complete composite set of all artifacts in the graph
        // before the per-node MergeProvider in order to trick Gradle into resolving all artifacts in
        // parallel using ParallelResolveArtifactSet. When we pull on the the resulting Provider, the
        // composite resolves first, downloading all artifacts in parallel. The downstream per-node providers
        // then use those downloaded artifacts without further IO. This would not be necessary if we executed
        // this provider as part of the task graph. Eventually we can remove this when we properly wire
        // this provider's build dependencies as part of a work graph.
        return graph.getArtifacts(filter).getResolvedArtifacts().flatMap(unused ->
            graph.getAllNodes().flatMap(nodes -> new MergeProvider<>(ImmutableLists.transform(nodes, node -> {
                if (!filter.isSatisfiedBy(node)) {
                    return Providers.of(new NodeAndArtifacts(node, Collections.emptySet()));
                }
                return node.getArtifacts().getResolvedArtifacts().map(artifacts -> new NodeAndArtifacts(node, artifacts));
            })))
        );
    }

    private static ArtifactGraph getArtifactGraph(Configuration configuration, @Nullable Action<? super AttributeContainer> attrsAction) {
        Action<? super ArtifactView.ViewConfiguration> viewAction = Actions.doNothing();
        if (attrsAction != null) {
            viewAction = view -> {
                view.withVariantReselection();
                view.attributes(attrsAction);
            };
        }

        return ((ArtifactViewInternal) configuration.getIncoming().artifactView(viewAction)).getArtifactGraph();
    }

    private IdeResults processResults(
        List<ConfigurationResults> plusResults,
        MinusKeys minusKeys,
        Set<String> testConfNames,
        boolean fetchSources,
        boolean fetchJavadoc
    ) {
        Set<VariantIdentifier> contributedByTest = new HashSet<>();
        Set<VariantIdentifier> contributedByNonTest = new HashSet<>();

        Map<VariantIdentifier, Map<ComponentArtifactIdentifier, ResolvedArtifactResult>> defaults = new LinkedHashMap<>();
        Map<VariantIdentifier, Map<ComponentArtifactIdentifier, ResolvedArtifactResult>> sources = new LinkedHashMap<>();
        Map<VariantIdentifier, Map<ComponentArtifactIdentifier, ResolvedArtifactResult>> javadoc = new LinkedHashMap<>();
        Map<ComponentSelector, UnresolvedArtifactEdge> unresolvedEdges = new LinkedHashMap<>();

        for (ConfigurationResults conf : plusResults) {
            Set<VariantIdentifier> testOrProdBucket = testConfNames.contains(conf.configurationName()) ? contributedByTest : contributedByNonTest;
            for (NodeAndArtifacts nodeArtifacts : conf.defaultArtifacts()) {
                VariantIdentifier variantId = nodeArtifacts.node().getId();
                addArtifacts(defaults, variantId, nodeArtifacts.artifacts());

                testOrProdBucket.add(variantId);
                for (ArtifactEdge edge : nodeArtifacts.node().getDependencies()) {
                    if (edge instanceof UnresolvedArtifactEdge unresolved) {
                        unresolvedEdges.put(unresolved.getRequested(), unresolved);
                    }
                }
            }
            for (NodeAndArtifacts nodeArtifacts : conf.sourcesArtifacts()) {
                addArtifacts(sources, nodeArtifacts.node().getId(), nodeArtifacts.artifacts());
            }
            for (NodeAndArtifacts nodeArtifacts : conf.javadocArtifacts()) {
                addArtifacts(javadoc, nodeArtifacts.node().getId(), nodeArtifacts.artifacts());
            }
        }

        for (VariantIdentifier variantId : minusKeys.variants()) {
            defaults.remove(variantId);
            sources.remove(variantId);
            javadoc.remove(variantId);
        }
        for (ComponentSelector selector : minusKeys.unresolvedSelectors()) {
            unresolvedEdges.remove(selector);
        }

        Set<VariantIdentifier> testOnlyVariants = new LinkedHashSet<>();
        for (VariantIdentifier variantId : defaults.keySet()) {
            if (contributedByTest.contains(variantId) && !contributedByNonTest.contains(variantId)) {
                testOnlyVariants.add(variantId);
            }
        }

        Map<ComponentIdentifier, AdditionalDocs> additionalDocs = resolveAdditionalDocs(defaults.keySet(), sources.keySet(), javadoc.keySet(), fetchSources, fetchJavadoc);
        return new IdeResults(defaults, sources, javadoc, testOnlyVariants, unresolvedEdges, additionalDocs);
    }

    /**
     * Add the given artifacts to the per-variant artifact map, deduping by {@link ComponentArtifactIdentifier}.
     * First artifact wins for any duplicate ID.
     */
    private static void addArtifacts(
        Map<VariantIdentifier, Map<ComponentArtifactIdentifier, ResolvedArtifactResult>> target,
        VariantIdentifier variantId,
        Set<ResolvedArtifactResult> artifacts
    ) {
        if (artifacts.isEmpty()) {
            return;
        }

        Map<ComponentArtifactIdentifier, ResolvedArtifactResult> artifactsById = target.computeIfAbsent(variantId, k -> new LinkedHashMap<>());
        for (ResolvedArtifactResult artifact : artifacts) {
            artifactsById.putIfAbsent(artifact.getId(), artifact);
        }
    }

    private void visitResults(IdeDependencyVisitor visitor, IdeResults results, boolean inferModulePath) {
        for (Map.Entry<VariantIdentifier, Map<ComponentArtifactIdentifier, ResolvedArtifactResult>> entry : results.defaults().entrySet()) {
            VariantIdentifier variantId = entry.getKey();
            Map<ComponentArtifactIdentifier, ResolvedArtifactResult> artifactsById = entry.getValue();

            boolean testOnly = results.testOnlyVariants().contains(variantId);
            Set<ResolvedArtifactResult> variantSources = valuesAsSet(results.sources().get(variantId));
            Set<ResolvedArtifactResult> variantJavadoc = valuesAsSet(results.javadoc().get(variantId));

            for (ResolvedArtifactResult artifact : artifactsById.values()) {
                ComponentIdentifier componentIdentifier = artifact.getId().getComponentIdentifier();
                boolean asModule = isModule(testOnly, artifact.getFile(), inferModulePath);
                if (componentIdentifier instanceof ProjectComponentIdentifier) {
                    visitor.visitProjectDependency(artifact, testOnly, asModule);
                } else if (componentIdentifier instanceof ModuleComponentIdentifier) {
                    Set<ResolvedArtifactResult> moduleSources = variantSources;
                    Set<ResolvedArtifactResult> moduleJavadoc = variantJavadoc;
                    AdditionalDocs fallback = results.additionalDocs().get(componentIdentifier);
                    if (fallback != null) {
                        if (moduleSources.isEmpty()) {
                            moduleSources = fallback.sources();
                        }
                        if (moduleJavadoc.isEmpty()) {
                            moduleJavadoc = fallback.javadoc();
                        }
                    }
                    visitor.visitModuleDependency(artifact, moduleSources, moduleJavadoc, testOnly, asModule);
                } else if (isLocalGroovyDependency(artifact)) {
                    File localGroovySources = shouldDownloadSources(visitor) ? gradleApiSourcesResolver.resolveLocalGroovySources(artifact.getFile().getName()) : null;
                    visitor.visitGradleApiDependency(artifact, localGroovySources, testOnly);
                } else {
                    visitor.visitFileDependency(artifact, testOnly);
                }
            }
        }

        if (!visitor.isOffline()) {
            for (UnresolvedArtifactEdge edge : results.unresolvedEdges().values()) {
                visitor.visitUnresolvedDependency(edge.getRequested(), edge.getFailure());
            }
        }
    }

    private static Set<ResolvedArtifactResult> valuesAsSet(@Nullable Map<ComponentArtifactIdentifier, ResolvedArtifactResult> map) {
        return (map == null || map.isEmpty()) ? Collections.emptySet() : new LinkedHashSet<>(map.values());
    }

    private boolean isModule(boolean testOnly, File artifact, boolean inferModulePath) {
        // Test code is not treated as modules, as Eclipse does not support compiling two modules in one project anyway.
        // See also: https://bugs.eclipse.org/bugs/show_bug.cgi?id=520667
        //
        // We assume that a test-only dependency is not a module, which corresponds to how Eclipse does test running for modules:
        // It patches the main module with the tests and expects test dependencies to be part of the unnamed module (classpath).
        return javaModuleDetector.isModule(inferModulePath && !testOnly, artifact);
    }

    private static boolean isLocalGroovyDependency(ResolvedArtifactResult artifact) {
        String artifactFileName = artifact.getFile().getName();
        String componentIdentifier = artifact.getId().getComponentIdentifier().getDisplayName();
        return (componentIdentifier.equals(GRADLE_API.displayName)
                || componentIdentifier.equals(GRADLE_TEST_KIT.displayName)
                || componentIdentifier.equals(LOCAL_GROOVY.displayName))
            && artifactFileName.startsWith("groovy-");
    }

    private static boolean shouldDownloadSources(IdeDependencyVisitor visitor) {
        return !visitor.isOffline() && visitor.downloadSources();
    }

}
