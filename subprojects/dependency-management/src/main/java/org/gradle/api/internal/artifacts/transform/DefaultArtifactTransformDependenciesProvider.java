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
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.util.Set;

/**
 * Companion object of a {@link TransformationNode} that knows how to compute the artifacts of the dependencies
 * of the artifact undergoing transformation by producing an {@link ArtifactTransformDependenciesInternal}.
 *
 * The implementation is not thread-safe, designed to work with a single {@link Transformation}
 * and thus should not be shared beyond the members of a chain.
 */
class DefaultArtifactTransformDependenciesProvider implements ArtifactTransformDependenciesProvider {

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
    private final ResolvableDependencies resolvableDependencies;
    private ImmutableSet<ComponentIdentifier> dependenciesIdentifiers;

    DefaultArtifactTransformDependenciesProvider(ComponentIdentifier componentIdentifier, ResolvableDependencies resolvableDependencies) {
        this.componentIdentifier = componentIdentifier;
        this.resolvableDependencies = resolvableDependencies;
    }

    @Override
    public ArtifactTransformDependenciesInternal forTransformer(Transformer transformer) {
        if (!transformer.requiresDependencies()) {
            return EMPTY_DEPENDENCIES;
        }

        ImmutableAttributes fromAttributes = transformer.getFromAttributes();
        return DeprecationLogger.whileDisabled(() -> {
            // Temporarily ignore deprecation warning while triggering the configuration and artifact resolution
            // This is unsafe as some other thread may be using the projects state.
            // Should instead replace this with a node that resolves the configuration and on which each transform node depends
            if (dependenciesIdentifiers == null) {
                dependenciesIdentifiers = initializeDependencyIdentifiers();
            }
            FileCollection files = resolvableDependencies.artifactView(conf -> {
                conf.componentFilter(element -> {
                    return dependenciesIdentifiers.contains(element);
                });
                if (!fromAttributes.isEmpty()) {
                    conf.attributes(container -> {
                        for (Attribute<?> attribute : fromAttributes.keySet()) {
                            copyAttribute(fromAttributes, container, attribute);
                        }
                    });
                }
            }).getArtifacts().getArtifactFiles();

            // Also ensure that the file collection is resolved
            if (files.isEmpty()) {
                return EMPTY_DEPENDENCIES;
            }

            return new DefaultArtifactTransformDependencies(files);
        });
    }

    private ImmutableSet<ComponentIdentifier> initializeDependencyIdentifiers() {
        ResolutionResult resolutionResult = resolvableDependencies.getResolutionResult();
        Set<ComponentIdentifier> result = Sets.newHashSet();
        for (ResolvedComponentResult component : resolutionResult.getAllComponents()) {
            if (component.getId().equals(componentIdentifier)) {
                getDependenciesIdentifiers(result, component.getDependencies());
                break;
            }
        }
        return ImmutableSet.copyOf(result);
    }

    private static void getDependenciesIdentifiers(Set<ComponentIdentifier> dependenciesIdentifiers, Set<? extends DependencyResult> dependencies) {
        for (DependencyResult dependency : dependencies) {
            if (dependency instanceof ResolvedDependencyResult) {
                ResolvedDependencyResult resolvedDependency = (ResolvedDependencyResult) dependency;
                ResolvedComponentResult selected = resolvedDependency.getSelected();
                if (dependenciesIdentifiers.add(selected.getId())) {
                    // Do not traverse if seen already
                    getDependenciesIdentifiers(dependenciesIdentifiers, selected.getDependencies());
                }
            }
        }
    }

    private static <T> void copyAttribute(ImmutableAttributes attributes, AttributeContainer container, Attribute<T> attribute) {
        container.attribute(attribute, attributes.findEntry(attribute).get());
    }
}
