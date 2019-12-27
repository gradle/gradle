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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapts Gradle's dependency resolution engine to the special needs of the IDE plugins.
 * Allows adding and subtracting {@link Configuration}s, working in offline mode and downloading sources/javadoc.
 */
public class IdeDependencySet {
    private final DependencyHandler dependencyHandler;
    private final Collection<Configuration> plusConfigurations;
    private final Collection<Configuration> minusConfigurations;
    private final GradleApiSourcesResolver gradleApiSourcesResolver;

    public IdeDependencySet(DependencyHandler dependencyHandler, Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations, GradleApiSourcesResolver gradleApiSourcesResolver) {
        this.dependencyHandler = dependencyHandler;
        this.plusConfigurations = plusConfigurations;
        this.minusConfigurations = minusConfigurations;
        this.gradleApiSourcesResolver = gradleApiSourcesResolver;
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
        private final Map<ComponentArtifactIdentifier, ResolvedArtifactResult> resolvedArtifacts = Maps.newLinkedHashMap();
        private final SetMultimap<ComponentArtifactIdentifier, Configuration> configurations = MultimapBuilder.hashKeys().linkedHashSetValues().build();
        private final Map<ComponentSelector, UnresolvedDependencyResult> unresolvedDependencies = Maps.newLinkedHashMap();
        private final Table<ModuleComponentIdentifier, Class<? extends Artifact>, Set<ResolvedArtifactResult>> auxiliaryArtifacts = HashBasedTable.create();

        public void visit(IdeDependencyVisitor visitor) {
            resolvePlusConfigurations(visitor);
            resolveMinusConfigurations(visitor);
            resolveAuxiliaryArtifacts(visitor);
            visitArtifacts(visitor);
            visitUnresolvedDependencies(visitor);
        }

        private void resolvePlusConfigurations(IdeDependencyVisitor visitor) {
            for (Configuration configuration : plusConfigurations) {
                ArtifactCollection artifacts = getResolvedArtifacts(configuration, visitor);
                for (ResolvedArtifactResult resolvedArtifact : artifacts) {
                    resolvedArtifacts.put(resolvedArtifact.getId(), resolvedArtifact);
                    configurations.put(resolvedArtifact.getId(), configuration);
                }
                if (artifacts.getFailures().isEmpty()) {
                    continue;
                }
                for (UnresolvedDependencyResult unresolvedDependency : getUnresolvedDependencies(configuration, visitor)) {
                    unresolvedDependencies.put(unresolvedDependency.getAttempted(), unresolvedDependency);
                }
            }
        }

        private void resolveMinusConfigurations(IdeDependencyVisitor visitor) {
            for (Configuration configuration : minusConfigurations) {
                ArtifactCollection artifacts = getResolvedArtifacts(configuration, visitor);
                for (ResolvedArtifactResult resolvedArtifact : artifacts) {
                    resolvedArtifacts.remove(resolvedArtifact.getId());
                    configurations.removeAll(resolvedArtifact.getId());
                }
                if (artifacts.getFailures().isEmpty()) {
                    continue;
                }
                for (UnresolvedDependencyResult unresolvedDependency : getUnresolvedDependencies(configuration, visitor)) {
                    unresolvedDependencies.remove(unresolvedDependency.getAttempted());
                }
            }
        }

        private ArtifactCollection getResolvedArtifacts(Configuration configuration, final IdeDependencyVisitor visitor) {
            return configuration.getIncoming().artifactView(new Action<ArtifactView.ViewConfiguration>() {
                @Override
                public void execute(ArtifactView.ViewConfiguration viewConfiguration) {
                    viewConfiguration.lenient(true);
                    viewConfiguration.componentFilter(getComponentFilter(visitor));
                }
            }).getArtifacts();
        }

        private Spec<ComponentIdentifier> getComponentFilter(IdeDependencyVisitor visitor) {
            return visitor.isOffline() ? NOT_A_MODULE : Specs.<ComponentIdentifier>satisfyAll();
        }

        private Iterable<UnresolvedDependencyResult> getUnresolvedDependencies(Configuration configuration, IdeDependencyVisitor visitor) {
            if (visitor.isOffline()) {
                return Collections.emptySet();
            }
            return Iterables.filter(configuration.getIncoming().getResolutionResult().getRoot().getDependencies(), UnresolvedDependencyResult.class);
        }

        private void resolveAuxiliaryArtifacts(IdeDependencyVisitor visitor) {
            if (visitor.isOffline()) {
                return;
            }

            Set<ModuleComponentIdentifier> componentIdentifiers = getModuleComponentIdentifiers();
            if (componentIdentifiers.isEmpty()) {
                return;
            }

            List<Class<? extends Artifact>> types = getAuxiliaryArtifactTypes(visitor);
            if (types.isEmpty()) {
                return;
            }

            ArtifactResolutionResult result = dependencyHandler.createArtifactResolutionQuery()
                .forComponents(componentIdentifiers)
                .withArtifacts(JvmLibrary.class, types)
                .execute();

            for (ComponentArtifactsResult artifactsResult : result.getResolvedComponents()) {
                for (Class<? extends Artifact> type : types) {
                    Set<ResolvedArtifactResult> resolvedArtifactResults = Sets.newLinkedHashSet();

                    for (ArtifactResult artifactResult : artifactsResult.getArtifacts(type)) {
                        if (artifactResult instanceof ResolvedArtifactResult) {
                            resolvedArtifactResults.add((ResolvedArtifactResult) artifactResult);
                        }
                    }
                    auxiliaryArtifacts.put((ModuleComponentIdentifier) artifactsResult.getId(), type, resolvedArtifactResults);
                }
            }
        }

        private Set<ModuleComponentIdentifier> getModuleComponentIdentifiers() {
            Set<ModuleComponentIdentifier> componentIdentifiers = Sets.newLinkedHashSet();
            for (ComponentArtifactIdentifier identifier : resolvedArtifacts.keySet()) {
                ComponentIdentifier componentIdentifier = identifier.getComponentIdentifier();
                if (componentIdentifier instanceof ModuleComponentIdentifier) {
                    componentIdentifiers.add((ModuleComponentIdentifier) componentIdentifier);
                }
            }
            return componentIdentifiers;
        }

        private List<Class<? extends Artifact>> getAuxiliaryArtifactTypes(IdeDependencyVisitor visitor) {
            List<Class<? extends Artifact>> types = Lists.newArrayListWithCapacity(2);
            if (visitor.downloadSources()) {
                types.add(SourcesArtifact.class);
            }
            if (visitor.downloadJavaDoc()) {
                types.add(JavadocArtifact.class);
            }
            return types;
        }

        private void visitArtifacts(IdeDependencyVisitor visitor) {
            for (ResolvedArtifactResult artifact : resolvedArtifacts.values()) {
                ComponentIdentifier componentIdentifier = artifact.getId().getComponentIdentifier();
                ComponentArtifactIdentifier artifactIdentifier = artifact.getId();
                if (componentIdentifier instanceof ProjectComponentIdentifier) {
                    visitor.visitProjectDependency(artifact);
                } else if (componentIdentifier instanceof ModuleComponentIdentifier) {
                    Set<ResolvedArtifactResult> sources = auxiliaryArtifacts.get(componentIdentifier, SourcesArtifact.class);
                    sources = sources != null ? sources : Collections.<ResolvedArtifactResult>emptySet();
                    Set<ResolvedArtifactResult> javaDoc = auxiliaryArtifacts.get(componentIdentifier, JavadocArtifact.class);
                    javaDoc = javaDoc != null ? javaDoc : Collections.<ResolvedArtifactResult>emptySet();
                    visitor.visitModuleDependency(artifact, sources, javaDoc, isTestConfiguration(configurations.get(artifactIdentifier)));
                } else if (isGradleApiDependency(artifact)) {
                    visitor.visitGradleApiDependency(artifact, gradleApiSourcesResolver.resolveGradleApiSources(artifact.getFile()), isTestConfiguration(configurations.get(artifactIdentifier)));
                } else {
                    visitor.visitFileDependency(artifact, isTestConfiguration(configurations.get(artifactIdentifier)));
                }
            }
        }

        private boolean isGradleApiDependency(ResolvedArtifactResult artifact) {
            String artifactFileName = artifact.getFile().getName();
            return artifactFileName.startsWith("gradle-api") || artifactFileName.startsWith("gradle-test-kit");
        }

        private boolean isTestConfiguration(Set<Configuration> configurations) {
            for (Configuration c : configurations) {
                if (!c.getName().toLowerCase().contains("test")) {
                    return false;
                }
            }
            return true;
        }

        private void visitUnresolvedDependencies(IdeDependencyVisitor visitor) {
            for (UnresolvedDependencyResult unresolvedDependency : unresolvedDependencies.values()) {
                visitor.visitUnresolvedDependency(unresolvedDependency);
            }
        }
    }

    private static final Spec<ComponentIdentifier> NOT_A_MODULE = new Spec<ComponentIdentifier>() {
        @Override
        public boolean isSatisfiedBy(ComponentIdentifier id) {
            return !(id instanceof ModuleComponentIdentifier);
        }
    };

}
