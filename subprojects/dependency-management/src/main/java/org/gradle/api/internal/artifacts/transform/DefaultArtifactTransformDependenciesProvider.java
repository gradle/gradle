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
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
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

import java.io.File;
import java.util.Set;

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

    static final ArtifactTransformDependenciesProvider EMPTY = new ArtifactTransformDependenciesProvider() {
        @Override
        public ArtifactTransformDependenciesInternal forAttributes(ImmutableAttributes attributes) {
            return EMPTY_DEPENDENCIES;
        }
    };

    private final ComponentArtifactIdentifier artifactId;
    private final ResolvableDependencies resolvableDependencies;

    DefaultArtifactTransformDependenciesProvider(ComponentArtifactIdentifier artifactId, ResolvableDependencies resolvableDependencies) {
        this.artifactId = artifactId;
        this.resolvableDependencies = resolvableDependencies;
    }

    public static ArtifactTransformDependenciesProvider create(Transformation transformation, ComponentArtifactIdentifier artifactId, ResolvableDependencies resolvableDependencies) {
        return transformation.requiresDependencies()
            ? new DefaultArtifactTransformDependenciesProvider(artifactId, resolvableDependencies)
            : EMPTY;
    }

    @Override
    public ArtifactTransformDependenciesInternal forAttributes(ImmutableAttributes attributes) {
        ResolutionResult resolutionResult = resolvableDependencies.getResolutionResult();
        Set<ComponentIdentifier> dependenciesIdentifiers = Sets.newHashSet();
        for (ResolvedComponentResult component : resolutionResult.getAllComponents()) {
            if (component.getId().equals(artifactId.getComponentIdentifier())) {
                getDependenciesIdentifiers(dependenciesIdentifiers, component.getDependencies());
            }
        }
        FileCollection files = resolvableDependencies.artifactView(conf -> {
            conf.componentFilter(element -> {
                return dependenciesIdentifiers.contains(element);
            });
            if (!attributes.isEmpty()) {
                conf.attributes(container -> {
                    for (Attribute<?> attribute : attributes.keySet()) {
                        copyAttribute(attributes, container, attribute);
                    }
                });
            }
        }).getArtifacts().getArtifactFiles();

        // Also ensure that the file collection is resolved
        if (files.isEmpty()) {
            return EMPTY_DEPENDENCIES;
        }

        return new DefaultArtifactTransformDependencies(files);
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
