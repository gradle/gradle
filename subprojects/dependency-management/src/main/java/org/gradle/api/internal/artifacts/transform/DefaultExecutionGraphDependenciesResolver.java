/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.ResolvedFilesCollectingVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Factory;
import org.gradle.internal.Try;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class DefaultExecutionGraphDependenciesResolver implements ExecutionGraphDependenciesResolver {
    private static final ArtifactTransformDependenciesInternal EMPTY_DEPENDENCIES = new ArtifactTransformDependenciesInternal() {
        @Override
        public Iterable<File> getFiles() {
            return ImmutableSet.of();
        }

        @Override
        public CurrentFileCollectionFingerprint fingerprint(FileCollectionFingerprinter fingerprinter) {
            return fingerprinter.empty();
        }
    };

    private final ComponentIdentifier componentIdentifier;
    private final Factory<ResolverResults> graphResults;
    private final Factory<ResolverResults> artifactResults;
    private final WorkNodeAction graphResolveAction;
    private Set<ComponentIdentifier> buildDependencies;
    private Set<ComponentIdentifier> dependencies;

    public DefaultExecutionGraphDependenciesResolver(ComponentIdentifier componentIdentifier, Factory<ResolverResults> graphResults, Factory<ResolverResults> artifactResults, WorkNodeAction graphResolveAction) {
        this.componentIdentifier = componentIdentifier;
        this.graphResults = graphResults;
        this.artifactResults = artifactResults;
        this.graphResolveAction = graphResolveAction;
    }

    @Override
    public Try<ArtifactTransformDependenciesInternal> forTransformer(Transformer transformer) {
        if (!transformer.requiresDependencies()) {
            return Try.successful(EMPTY_DEPENDENCIES);
        }
        ResolverResults results = artifactResults.create();
        if (dependencies == null) {
            dependencies = computeProjectDependencies(componentIdentifier, ComponentIdentifier.class, results.getResolutionResult().getAllComponents());
        }
        VisitedArtifactSet visitedArtifacts = results.getVisitedArtifacts();
        SelectedArtifactSet artifacts = visitedArtifacts.select(Specs.satisfyAll(), transformer.getFromAttributes(), element -> {
            return dependencies.contains(element);
        }, false);
        ResolvedFilesCollectingVisitor visitor = new ResolvedFilesCollectingVisitor();
        artifacts.visitArtifacts(visitor, false);
        if (!visitor.getFailures().isEmpty()) {
            if (visitor.getFailures().size() == 1) {
                return Try.failure(visitor.getFailures().iterator().next());
            }
            return Try.failure(new DefaultLenientConfiguration.ArtifactResolveException("transform dependencies", transformer.getDisplayName(), "artifact transform dependencies", visitor.getFailures()));
        }
        return Try.successful(new DefaultArtifactTransformDependencies(ImmutableFileCollection.of(visitor.getFiles())));
    }

    @Override
    public TaskDependencyContainer computeDependencyNodes(TransformationStep transformationStep) {
        if (!transformationStep.requiresDependencies()) {
            return TaskDependencyContainer.EMPTY;
        } else {
            return new TaskDependencyContainer() {
                @Override
                public void visitDependencies(TaskDependencyResolveContext context) {
                    ResolverResults results = graphResults.create();
                    if (buildDependencies == null) {
                        buildDependencies = computeProjectDependencies(componentIdentifier, ProjectComponentIdentifier.class, results.getResolutionResult().getAllComponents());
                    }
                    VisitedArtifactSet visitedArtifacts = results.getVisitedArtifacts();
                    if (!buildDependencies.isEmpty()) {
                        SelectedArtifactSet projectArtifacts = visitedArtifacts.select(Specs.satisfyAll(), transformationStep.getFromAttributes(), element -> {
                            return buildDependencies.contains(element);
                        }, true);
                        context.add(projectArtifacts);
                    }
                    context.add(graphResolveAction);
                }
            };
        }
    }

    private static Set<ComponentIdentifier> computeProjectDependencies(ComponentIdentifier componentIdentifier, Class<? extends ComponentIdentifier> type, Set<ResolvedComponentResult> componentResults) {
        ResolvedComponentResult targetComponent = null;
        for (ResolvedComponentResult component : componentResults) {
            if (component.getId().equals(componentIdentifier)) {
                targetComponent = component;
                break;
            }
        }
        if (targetComponent == null) {
            throw new AssertionError("Could not find component " + componentIdentifier + " in provided results.");
        }
        Set<ComponentIdentifier> buildDependencies = new HashSet<>();
        collectDependenciesIdentifiers(buildDependencies, type, new HashSet<>(), targetComponent.getDependencies());
        return buildDependencies;
    }

    private static void collectDependenciesIdentifiers(Set<ComponentIdentifier> dependenciesIdentifiers, Class<? extends ComponentIdentifier> type, Set<ComponentIdentifier> visited, Set<? extends DependencyResult> dependencies) {
        for (DependencyResult dependency : dependencies) {
            if (dependency instanceof ResolvedDependencyResult) {
                ResolvedDependencyResult resolvedDependency = (ResolvedDependencyResult) dependency;
                ResolvedComponentResult selected = resolvedDependency.getSelected();
                if (type.isInstance(selected.getId())) {
                    dependenciesIdentifiers.add(selected.getId());
                }
                if (visited.add(selected.getId())) {
                    // Do not traverse if seen already
                    collectDependenciesIdentifiers(dependenciesIdentifiers, type, visited, selected.getDependencies());
                }
            }
        }
    }

}
