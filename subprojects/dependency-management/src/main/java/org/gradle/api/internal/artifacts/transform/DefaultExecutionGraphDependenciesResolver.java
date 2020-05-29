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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.internal.Factory;
import org.gradle.internal.Try;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DefaultExecutionGraphDependenciesResolver implements ExecutionGraphDependenciesResolver {
    public static final ArtifactTransformDependencies MISSING_DEPENDENCIES = new ArtifactTransformDependencies() {
        @Override
        public FileCollection getFiles() {
            throw new IllegalStateException("Transform does not use artifact dependencies.");
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
    private final FilteredResultFactory filteredResultFactory;
    private Set<ComponentIdentifier> buildDependencies;
    private Set<ComponentIdentifier> dependencies;

    public DefaultExecutionGraphDependenciesResolver(ComponentIdentifier componentIdentifier, Factory<ResolverResults> graphResults, Factory<ResolverResults> artifactResults, WorkNodeAction graphResolveAction, FilteredResultFactory filteredResultFactory) {
        this.componentIdentifier = componentIdentifier;
        this.graphResults = graphResults;
        this.artifactResults = artifactResults;
        this.graphResolveAction = graphResolveAction;
        this.filteredResultFactory = filteredResultFactory;
    }

    @Override
    public FileCollection selectedArtifacts(Transformer transformer) {
        if (!transformer.requiresDependencies()) {
            return MISSING_DEPENDENCIES.getFiles();
        }
        ResolverResults results = artifactResults.create();
        if (dependencies == null) {
            dependencies = computeDependencies(componentIdentifier, ComponentIdentifier.class, results.getResolutionResult().getAllComponents(), false);
        }
        return filteredResultFactory.resultsMatching(transformer.getFromAttributes(), element -> dependencies.contains(element));
    }

    @Override
    public Try<ArtifactTransformDependencies> computeArtifacts(Transformer transformer) {
        if (!transformer.requiresDependencies()) {
            return Try.successful(MISSING_DEPENDENCIES);
        }
        try {
            FileCollection files = selectedArtifacts(transformer);
            // Trigger resolution failure
            files.getFiles();
            return Try.successful(new DefaultArtifactTransformDependencies(files));
        } catch (Exception e) {
            return Try.failure(e);
        }
    }

    @Override
    public TaskDependencyContainer computeDependencyNodes(TransformationStep transformationStep) {
        if (!transformationStep.requiresDependencies()) {
            return TaskDependencyContainer.EMPTY;
        }
        return context -> {
            ResolverResults results = graphResults.create();
            if (buildDependencies == null) {
                buildDependencies = computeDependencies(componentIdentifier, ProjectComponentIdentifier.class, results.getResolutionResult().getAllComponents(), true);
            }
            context.add(filteredResultFactory.resultsMatching(transformationStep.getFromAttributes(), element -> buildDependencies.contains(element)));
            context.add(graphResolveAction);
        };
    }

    private static Set<ComponentIdentifier> computeDependencies(ComponentIdentifier componentIdentifier, Class<? extends ComponentIdentifier> type, Set<ResolvedComponentResult> componentResults, boolean strict) {
        ResolvedComponentResult targetComponent = null;
        for (ResolvedComponentResult component : componentResults) {
            if (component.getId().equals(componentIdentifier)) {
                targetComponent = component;
                break;
            }
        }
        if (targetComponent == null) {
            if (strict) {
                throw new AssertionError("Could not find component " + componentIdentifier + " in provided results.");
            } else {
                return Collections.emptySet();
            }
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
