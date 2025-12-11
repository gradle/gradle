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

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ArtifactCollection;
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
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.Artifact;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.query.ArtifactResolutionQueryFactory;
import org.gradle.api.internal.artifacts.result.ResolvedArtifactResultInternal;
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.component.model.VariantIdentifier;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implements artifact resolution for the IDE plugins.
 * <p>
 * The interface of the IDE plugins are based off the concept of "plus" and "minus" configurations.
 * This resolver will resolve all artifacts from the plus configurations, minus the artifacts from the
 * minus configurations. Then, if requested, will additionally download sources and javadoc for the
 * resulting set of resolved artifacts.
 * <p>
 * This resolver attempts to use {@link ArtifactView} to download artifacts, sources, and javadoc in
 * parallel. In some cases, like for Ivy repos, flat-dir repos, and maven repos where a variant
 * derivation strategy has not been configured, an artifact view will not be able to discover sources
 * and javadoc. In that case, this resolver will fall back to {@link org.gradle.api.artifacts.query.ArtifactResolutionQuery},
 * which downloads artifacts serially.
 */
@NullMarked
@ServiceScope(Scope.Project.class)
public class IdeDependencySet {

    private static final Spec<ComponentIdentifier> EXCLUDE_MODULE_COMPONENTS = id -> !(id instanceof ModuleComponentIdentifier);

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
        this.gradleApiSourcesResolver = new DefaultGradleApiSourcesResolver(
            dependencyManagementServices.newDetachedResolver(StandaloneDomainObjectContext.ANONYMOUS)
        );
    }

    /**
     * Resolve the given plus configurations, subtracting all results from the minus configurations,
     * and marking all artifacts only present in the test configurations as test artifacts. The
     * resulting artifacts are provided to the given visitor.
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

        // Download the primary artifacts
        ResolvedArtifacts resolvedArtifacts = resolveArtifacts(
            plusConfigurations,
            minusConfigurations,
            testConfigurations,
            attributes -> {},
            visitor.isOffline()
        );

        // Attempt to download sources and javadoc if requested, in parallel.
        ResolvedArtifacts sources = visitor.downloadSources() ? downloadDocumentationArtifacts(
            plusConfigurations,
            minusConfigurations,
            testConfigurations,
            DocsType.SOURCES,
            visitor.isOffline()
        ) : null;
        ResolvedArtifacts javadoc = visitor.downloadJavaDoc() ? downloadDocumentationArtifacts(
            plusConfigurations,
            minusConfigurations,
            testConfigurations,
            DocsType.JAVADOC,
            visitor.isOffline()
        ) : null;

        // Download any additional sources and javadoc that we did not already download in parallel.
        LegacyArtifacts legacyArtifacts = resolveMissingArtifactsSerially(visitor, resolvedArtifacts, sources, javadoc);

        // For each main artifact, call the visitor with any associated sources and javadoc.
        Set<ComponentArtifactIdentifier> visited = new LinkedHashSet<>();
        resolvedArtifacts.artifacts.forEach((sourceVariantId, artifacts) -> {
            ComponentIdentifier componentIdentifier = sourceVariantId.getComponentId();
            boolean testOnly = resolvedArtifacts.testOnlyVariantIds.contains(sourceVariantId);

            if (componentIdentifier instanceof ModuleComponentIdentifier) {
                Set<ResolvedArtifactResult> sourcesArtifacts = Collections.emptySet();
                if (sources != null) {
                    sourcesArtifacts = sources.artifacts.get(sourceVariantId);
                    if (sourcesArtifacts == null) {
                        sourcesArtifacts = orEmpty(legacyArtifacts.sources.get(componentIdentifier));
                    }
                }

                Set<ResolvedArtifactResult> javadocArtifacts = Collections.emptySet();
                if  (javadoc != null) {
                    javadocArtifacts = javadoc.artifacts.get(sourceVariantId);
                    if (javadocArtifacts == null) {
                        javadocArtifacts = orEmpty(legacyArtifacts.javadoc.get(componentIdentifier));
                    }
                }

                for (ResolvedArtifactResult artifact : artifacts) {
                    if (visited.add(artifact.getId())) {
                        boolean asModule = isModule(testOnly, inferModulePath, artifact.getFile());
                        visitor.visitModuleDependency(artifact, sourcesArtifacts, javadocArtifacts, testOnly, asModule);
                    }
                }
            } else {
                for (ResolvedArtifactResult artifact : artifacts) {
                    if (!visited.add(artifact.getId())) {
                        continue;
                    }
                    // We use the artifact's component ID to determine if a given artifact is a project
                    // dependency or not, as currently the VariantIdentifier component ID and the artifact's
                    // component ID currently differs for FileCollection dependencies.
                    // After https://github.com/gradle/gradle/issues/1629 is fixed by assigning the root component
                    // RootComponentIdentifier, these two should become consistent and we can use the variant ID's
                    // component ID.
                    ComponentIdentifier artifactComponentId = artifact.getId().getComponentIdentifier();
                    if (artifactComponentId instanceof ProjectComponentIdentifier) {
                        boolean asModule = isModule(testOnly, inferModulePath, artifact.getFile());
                        visitor.visitProjectDependency(artifact, testOnly, asModule);
                    } else {
                        if (gradleApiSourcesResolver.isLocalGroovyDependency(artifact)) {
                            File localGroovySources = downloadGroovySources(visitor, artifact);
                            visitor.visitGradleApiDependency(artifact, localGroovySources, testOnly);
                        } else {
                            visitor.visitFileDependency(artifact, testOnly);
                        }
                    }
                }
            }
        });

        for (UnresolvedDependencyResult unresolvedDependency : resolvedArtifacts.unresolvedDependencies) {
            visitor.visitUnresolvedDependency(unresolvedDependency);
        }
    }

    /**
     * Resolve all artifacts of the plus configurations, subtracting the artifacts of the minus configurations.
     * All artifacts only in test configurations are marked as test-only. {@code viewAction} can optionally
     * configure artifact selection to select supplemental artifacts like sources and javadoc.
     */
    private static ResolvedArtifacts resolveArtifacts(
        Collection<Configuration> plusConfigurations,
        Collection<Configuration> minusConfigurations,
        Collection<Configuration> testConfigurations,
        Action<? super ArtifactView.ViewConfiguration> viewAction,
        boolean offline
    ) {
        Map<VariantIdentifier, Set<ResolvedArtifactResult>> allArtifacts = new LinkedHashMap<>();
        Set<VariantIdentifier> mainVariantIds = new LinkedHashSet<>();
        Set<VariantIdentifier> testVariantIds = new LinkedHashSet<>();
        Map<ComponentSelector, UnresolvedDependencyResult> unresolvedDependencies = new LinkedHashMap<>();

        for (Configuration plusConfiguration : plusConfigurations) {
            boolean isTestConfiguration = testConfigurations.contains(plusConfiguration);
            ArtifactCollection artifacts = resolveConfiguration(plusConfiguration, offline, viewAction);
            for (ResolvedArtifactResult artifact : artifacts.getArtifacts()) {
                VariantIdentifier sourceVariantId = ((ResolvedArtifactResultInternal) artifact).getSourceVariantId();
                allArtifacts.computeIfAbsent(sourceVariantId, k -> new LinkedHashSet<>()).add(artifact);
                if (isTestConfiguration) {
                    testVariantIds.add(sourceVariantId);
                } else {
                    mainVariantIds.add(sourceVariantId);
                }
            }
            for (UnresolvedDependencyResult unresolvedDependency : getUnresolvedDependencies(plusConfiguration, offline)) {
                unresolvedDependencies.put(unresolvedDependency.getAttempted(), unresolvedDependency);
            }
        }

        for (Configuration minusConfiguration : minusConfigurations) {
            ArtifactCollection artifacts = resolveConfiguration(minusConfiguration, offline, viewAction);
            for (ResolvedArtifactResult artifact : artifacts.getArtifacts()) {
                VariantIdentifier sourceVariantId = ((ResolvedArtifactResultInternal) artifact).getSourceVariantId();
                allArtifacts.computeIfAbsent(sourceVariantId, k -> new LinkedHashSet<>()).remove(artifact);
                testVariantIds.remove(sourceVariantId);
                mainVariantIds.remove(sourceVariantId);
            }
            for (UnresolvedDependencyResult unresolvedDependency : getUnresolvedDependencies(minusConfiguration, offline)) {
                unresolvedDependencies.remove(unresolvedDependency.getAttempted());
            }
        }

        return new ResolvedArtifacts(
            allArtifacts,
            Sets.difference(testVariantIds, mainVariantIds),
            unresolvedDependencies
        );
    }

    /**
     * Perform artifact selection on the given configuration.
     */
    private static ArtifactCollection resolveConfiguration(Configuration configuration, boolean excludeExternalDependencies, Action<? super ArtifactView.ViewConfiguration> viewAction) {
        return configuration.getIncoming().artifactView(viewConfiguration -> {
            viewConfiguration.lenient(true);
            viewConfiguration.componentFilter(externalComponentFilter(excludeExternalDependencies));
            viewAction.execute(viewConfiguration);
        }).getArtifacts();
    }

    private static Spec<ComponentIdentifier> externalComponentFilter(boolean excludeExternalDependencies) {
        if (excludeExternalDependencies) {
            return EXCLUDE_MODULE_COMPONENTS;
        }
        return Specs.satisfyAll();
    }

    private boolean isModule(boolean testOnly, boolean inferModulePath, File artifact) {
        // Test code is not treated as modules, as Eclipse does not support compiling two modules in one project anyway.
        // See also: https://bugs.eclipse.org/bugs/show_bug.cgi?id=520667
        //
        // We assume that a test-only dependency is not a module, which corresponds to how Eclipse does test running for modules:
        // It patches the main module with the tests and expects test dependencies to be part of the unnamed module (classpath).
        return javaModuleDetector.isModule(inferModulePath && !testOnly, artifact);
    }

    private @Nullable File downloadGroovySources(IdeDependencyVisitor visitor, ResolvedArtifactResult artifact) {
        if (visitor.isOffline() || !visitor.downloadSources()) {
            return null;
        }

        return gradleApiSourcesResolver.resolveLocalGroovySources(artifact.getFile().getName());
    }

    private static Iterable<UnresolvedDependencyResult> getUnresolvedDependencies(Configuration configuration, boolean offline) {
        if (offline) {
            return Collections.emptySet();
        }
        // TODO: It would be nice if we could access failures from an ArtifactCollection,
        // so we don't have to deserialize the resolution result.
        return Iterables.filter(configuration.getIncoming().getResolutionResult().getRoot().getDependencies(), UnresolvedDependencyResult.class);
    }

    /**
     * Perform artifact resolution oer {@link #resolveArtifacts(Collection, Collection, Collection, Action, boolean)},
     * selecting the documentation artifacts with the given type.
     */
    private static ResolvedArtifacts downloadDocumentationArtifacts(
        Collection<Configuration> plusConfigurations,
        Collection<Configuration> minusConfigurations,
        Collection<Configuration> testConfigurations,
        String docsType,
        boolean offline
    ) {
        return resolveArtifacts(
            plusConfigurations,
            minusConfigurations,
            testConfigurations,
            view -> {
                view.attributes(attributes -> {
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, attributes.named(Usage.class, Usage.JAVA_RUNTIME));
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, attributes.named(Category.class, Category.DOCUMENTATION));
                    attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, attributes.named(Bundling.class, Bundling.EXTERNAL));
                    attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, attributes.named(DocsType.class, docsType));
                });
                view.withVariantReselection();
            },
            offline
        );
    }

    /**
     * Serially download sources and javadoc artifacts not already present in {@code sources}
     * and {@code artifacts}.
     */
    private LegacyArtifacts resolveMissingArtifactsSerially(
        IdeDependencyVisitor visitor,
        ResolvedArtifacts resolvedArtifacts,
        @Nullable ResolvedArtifacts sources,
        @Nullable ResolvedArtifacts javadoc
    ) {
        Set<ComponentIdentifier> missingArtifactComponents = new LinkedHashSet<>();
        resolvedArtifacts.artifacts.forEach((sourceVariantId, artifacts) -> {
            ComponentIdentifier componentIdentifier = sourceVariantId.getComponentId();
            if (componentIdentifier instanceof ModuleComponentIdentifier) {
                if (sources != null && sources.artifacts.get(sourceVariantId) == null) {
                    missingArtifactComponents.add(componentIdentifier);
                }
                if (javadoc != null && javadoc.artifacts.get(sourceVariantId) == null) {
                    missingArtifactComponents.add(componentIdentifier);
                }
            }
        });

        if (missingArtifactComponents.isEmpty()) {
            return new LegacyArtifacts(Collections.emptyMap(), Collections.emptyMap());
        }

        List<Class<? extends Artifact>> types = new ArrayList<>(2);
        if (visitor.downloadSources()) {
            types.add(SourcesArtifact.class);
        }
        if (visitor.downloadJavaDoc()) {
            types.add(JavadocArtifact.class);
        }
        ArtifactResolutionResult result = resolutionQueryFactory.createArtifactResolutionQuery()
            .forComponents(missingArtifactComponents)
            .withArtifacts(JvmLibrary.class, types)
            .execute();

        Map<ComponentIdentifier, Set<ResolvedArtifactResult>> resolvedSources = new HashMap<>();
        Map<ComponentIdentifier, Set<ResolvedArtifactResult>> resolvedJavadoc = new HashMap<>();
        for (ComponentArtifactsResult artifactsResult : result.getResolvedComponents()) {
            for (ArtifactResult artifact : artifactsResult.getArtifacts(SourcesArtifact.class)) {
                if (artifact instanceof ResolvedArtifactResult) {
                    resolvedSources.computeIfAbsent(artifact.getId().getComponentIdentifier(), cid -> new LinkedHashSet<>()).add((ResolvedArtifactResult) artifact);
                }
            }
            for (ArtifactResult artifact : artifactsResult.getArtifacts(JavadocArtifact.class)) {
                if  (artifact instanceof ResolvedArtifactResult) {
                    resolvedJavadoc.computeIfAbsent(artifact.getId().getComponentIdentifier(), cid -> new LinkedHashSet<>()).add((ResolvedArtifactResult) artifact);
                }
            }
        }
        return new LegacyArtifacts(resolvedSources, resolvedJavadoc);
    }

    private static Set<ResolvedArtifactResult> orEmpty(@Nullable Set<ResolvedArtifactResult> sourcesArtifacts) {
        return sourcesArtifacts != null ? sourcesArtifacts : Collections.emptySet();
    }

    @NullMarked
    private static class LegacyArtifacts {
        private final Map<ComponentIdentifier, Set<ResolvedArtifactResult>> sources;
        private final Map<ComponentIdentifier, Set<ResolvedArtifactResult>> javadoc;

        public LegacyArtifacts(
            Map<ComponentIdentifier, Set<ResolvedArtifactResult>> sources,
            Map<ComponentIdentifier, Set<ResolvedArtifactResult>> javadoc
        ) {
            this.sources = sources;
            this.javadoc = javadoc;
        }
    }

    @NullMarked
    private static class ResolvedArtifacts {

        private final Map<VariantIdentifier, Set<ResolvedArtifactResult>> artifacts;
        private final Set<VariantIdentifier> testOnlyVariantIds;
        private final Collection<UnresolvedDependencyResult> unresolvedDependencies;

        public ResolvedArtifacts(
            Map<VariantIdentifier, Set<ResolvedArtifactResult>> artifacts,
            Set<VariantIdentifier> testOnlyVariantIds,
            Map<ComponentSelector, UnresolvedDependencyResult> unresolvedDependencies
        ) {
            this.testOnlyVariantIds = testOnlyVariantIds;
            this.unresolvedDependencies = unresolvedDependencies.values();
            this.artifacts = artifacts;
        }

    }

}
