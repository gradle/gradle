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
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.artifacts.result.ResolvedArtifactResultInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.component.model.VariantIdentifier;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal.ClassPathNotation.GRADLE_API;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal.ClassPathNotation.GRADLE_TEST_KIT;
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY;

/**
 * Adapts Gradle's dependency resolution engine to the special needs of the IDE plugins.
 * Allows adding and subtracting {@link Configuration}s, working in offline mode and downloading sources/javadoc.
 */
public class IdeDependencySet {

    private final JavaModuleDetector javaModuleDetector;
    private final Collection<Configuration> plusConfigurations;
    private final Collection<Configuration> minusConfigurations;
    private final boolean inferModulePath;
    private final GradleApiSourcesResolver gradleApiSourcesResolver;
    private final Collection<Configuration> testConfigurations;

    public IdeDependencySet(
        JavaModuleDetector javaModuleDetector,
        Collection<Configuration> plusConfigurations,
        Collection<Configuration> minusConfigurations,
        boolean inferModulePath,
        GradleApiSourcesResolver gradleApiSourcesResolver,
        Collection<Configuration> testConfigurations
    ) {
        this.javaModuleDetector = javaModuleDetector;
        this.plusConfigurations = plusConfigurations;
        this.minusConfigurations = minusConfigurations;
        this.inferModulePath = inferModulePath;
        this.gradleApiSourcesResolver = gradleApiSourcesResolver;
        this.testConfigurations = testConfigurations;
    }

    public void visit(IdeDependencyVisitor visitor) {
        if (plusConfigurations.isEmpty()) {
            return;
        }
        new IdeDependencyResult().visit(visitor);
    }

    /*
     * Tries to minimize the number of requests to the resolution engine by batching up requests
     * for sources/javadoc.
     *
     * There is still some inefficiency because the ArtifactCollection interface does not provide
     * detailed failure results, so we have to fall back to the more expensive ResolutionResult API.
     * We should fix this, as other IDE vendors will face the same problem.
     */
    private class IdeDependencyResult {

        public void visit(IdeDependencyVisitor visitor) {
            ResolvedArtifacts resolvedArtifacts = resolveArtifacts(attributes -> {}, visitor.isOffline());
            ResolvedArtifacts sources = visitor.downloadSources() ? downloadDocumentationArtifacts(DocsType.SOURCES, visitor.isOffline()) : null;
            ResolvedArtifacts javadoc = visitor.downloadJavaDoc() ? downloadDocumentationArtifacts(DocsType.JAVADOC, visitor.isOffline()) : null;

            resolvedArtifacts.artifacts.forEach((sourceVariantId, artifacts) -> {
                boolean testOnly = resolvedArtifacts.testOnlyVariantIds.contains(sourceVariantId);

                ComponentIdentifier componentIdentifier = sourceVariantId.getComponentId();
                if (componentIdentifier instanceof ProjectComponentIdentifier) {
                    for (ResolvedArtifactResult artifact : artifacts) {
                        boolean asModule = isModule(testOnly, artifact.getFile());
                        visitor.visitProjectDependency(artifact, testOnly, asModule);
                    }
                } else {
                    if (componentIdentifier instanceof ModuleComponentIdentifier) {
                        Set<ResolvedArtifactResult> sourcesArtifacts = orEmpty(sources != null ? sources.artifacts.get(sourceVariantId) : null);
                        Set<ResolvedArtifactResult> javadocArtifacts = orEmpty(javadoc != null ? javadoc.artifacts.get(sourceVariantId) : null);
                        for (ResolvedArtifactResult artifact : artifacts) {
                            boolean asModule = isModule(testOnly, artifact.getFile());
                            visitor.visitModuleDependency(artifact, sourcesArtifacts, javadocArtifacts, testOnly, asModule);
                        }
                    } else {
                        for (ResolvedArtifactResult artifact : artifacts) {
                            if (isLocalGroovyDependency(artifact)) {
                                File localGroovySources = shouldDownloadGroovySources(visitor) ? gradleApiSourcesResolver.resolveLocalGroovySources(artifact.getFile().getName()) : null;
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

        private ResolvedArtifacts resolveArtifacts(Action<? super ArtifactView.ViewConfiguration> viewAction, boolean offline) {
            Map<VariantIdentifier, Set<ResolvedArtifactResult>> allArtifacts = new LinkedHashMap<>();
            Set<VariantIdentifier> productionVariantIds = new LinkedHashSet<>();
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
                        productionVariantIds.add(sourceVariantId);
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
                    productionVariantIds.remove(sourceVariantId);
                }
                for (UnresolvedDependencyResult unresolvedDependency : getUnresolvedDependencies(minusConfiguration, offline)) {
                    unresolvedDependencies.remove(unresolvedDependency.getAttempted());
                }
            }

            return new ResolvedArtifacts(
                allArtifacts,
                Sets.difference(testVariantIds, productionVariantIds),
                unresolvedDependencies
            );
        }

        private ResolvedArtifacts downloadDocumentationArtifacts(String docsType, boolean offline) {
            return resolveArtifacts(view -> {
                view.attributes(attributes -> {
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, attributes.named(Usage.class, Usage.JAVA_RUNTIME));
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, attributes.named(Category.class, Category.DOCUMENTATION));
                    attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, attributes.named(Bundling.class, Bundling.EXTERNAL));
                    attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, attributes.named(DocsType.class, docsType));
                });
                view.withVariantReselection();
            }, offline);
        }

        private Iterable<UnresolvedDependencyResult> getUnresolvedDependencies(Configuration configuration, boolean offline) {
            if (offline) {
                return Collections.emptySet();
            }
            return Iterables.filter(configuration.getIncoming().getResolutionResult().getRoot().getDependencies(), UnresolvedDependencyResult.class);
        }

        private ArtifactCollection resolveConfiguration(Configuration configuration, boolean excludeExternalDependencies, Action<? super ArtifactView.ViewConfiguration> viewAction) {
            return configuration.getIncoming().artifactView(viewConfiguration -> {
                viewConfiguration.lenient(true);
                viewConfiguration.componentFilter(externalComponentFilter(excludeExternalDependencies));
                viewAction.execute(viewConfiguration);
            }).getArtifacts();
        }

        private boolean isModule(boolean testOnly, File artifact) {
            // Test code is not treated as modules, as Eclipse does not support compiling two modules in one project anyway.
            // See also: https://bugs.eclipse.org/bugs/show_bug.cgi?id=520667
            //
            // We assume that a test-only dependency is not a module, which corresponds to how Eclipse does test running for modules:
            // It patches the main module with the tests and expects test dependencies to be part of the unnamed module (classpath).
            return javaModuleDetector.isModule(inferModulePath && !testOnly, artifact);
        }

        private boolean isLocalGroovyDependency(ResolvedArtifactResult artifact) {
            String artifactFileName = artifact.getFile().getName();
            String componentIdentifier = artifact.getId().getComponentIdentifier().getDisplayName();
            return (componentIdentifier.equals(GRADLE_API.displayName)
                    || componentIdentifier.equals(GRADLE_TEST_KIT.displayName)
                    || componentIdentifier.equals(LOCAL_GROOVY.displayName))
                && artifactFileName.startsWith("groovy-");
        }

        private boolean shouldDownloadGroovySources(IdeDependencyVisitor visitor) {
            return !visitor.isOffline() && visitor.downloadSources();
        }
    }

    private static Spec<ComponentIdentifier> externalComponentFilter(boolean excludeExternalDependencies) {
        if (excludeExternalDependencies) {
            return EXCLUDE_MODULE_COMPONENTS;
        }
        return Specs.satisfyAll();
    }

    private Set<ResolvedArtifactResult> orEmpty(@Nullable Set<ResolvedArtifactResult> sourcesArtifacts) {
        return sourcesArtifacts != null ? sourcesArtifacts : Collections.emptySet();
    }

    private static final Spec<ComponentIdentifier> EXCLUDE_MODULE_COMPONENTS = id -> !(id instanceof ModuleComponentIdentifier);

    private static class ResolvedArtifacts {

        private final Map<VariantIdentifier, Set<ResolvedArtifactResult>> artifacts;
        private final Set<VariantIdentifier> testOnlyVariantIds;
        private final Collection<UnresolvedDependencyResult> unresolvedDependencies;

        public ResolvedArtifacts(
            Map<VariantIdentifier, Set<ResolvedArtifactResult>> artifacts,
            Set<VariantIdentifier> testOnlyVariantIds, Map<ComponentSelector, UnresolvedDependencyResult> unresolvedDependencies
        ) {
            this.testOnlyVariantIds = testOnlyVariantIds;
            this.unresolvedDependencies = unresolvedDependencies.values();
            this.artifacts = artifacts;
        }

    }

}
