/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.Buildable;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.attributes.DefaultArtifactAttributes;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Pair;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ArtifactTransformer {
    private final ArtifactTransforms artifactTransforms;
    private final ArtifactAttributeMatcher attributeMatcher;
    private final Map<Pair<File, AttributeContainer>, List<File>> transformedFiles = Maps.newHashMap();
    private final Map<Pair<ResolvedArtifact, AttributeContainer>, List<ResolvedArtifact>> transformedArtifacts = Maps.newHashMap();

    public ArtifactTransformer(ArtifactTransforms artifactTransforms, ArtifactAttributeMatcher attributeMatcher) {
        this.artifactTransforms = artifactTransforms;
        this.attributeMatcher = attributeMatcher;
    }

    public ArtifactTransformer(ResolutionStrategyInternal resolutionStrategy, AttributesSchema attributesSchema) {
        this.attributeMatcher = new ArtifactAttributeMatcher(attributesSchema);
        this.artifactTransforms = new InstantiatingArtifactTransforms(resolutionStrategy, this.attributeMatcher);
    }

    private boolean matchArtifactsAttributes(HasAttributes artifact, AttributeContainer configuration) {
        return attributeMatcher.attributesMatch(artifact, configuration);
    }

    private Transformer<List<File>, File> getTransform(HasAttributes from, AttributeContainer to) {
        return artifactTransforms.getTransform(from.getAttributes(), to);
    }

    /**
     * Returns a spec that selects artifacts matching the supplied attributes, or which can be transformed to match.
     */
    public Spec<ResolvedArtifact> select(final AttributeContainer attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Specs.satisfyAll();
        }
        return new Spec<ResolvedArtifact>() {
            @Override
            public boolean isSatisfiedBy(ResolvedArtifact artifact) {
                return matchArtifactsAttributes(artifact, attributes)
                    || getTransform(artifact, attributes) != null;
            }
        };
    }

    /**
     * Returns a visitor that transforms files and artifacts to match the requested attributes
     * and then forwards the results to the given visitor.
     */
    public ArtifactVisitor visitor(final ArtifactVisitor visitor, @Nullable AttributeContainer attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return visitor;
        }
        final AttributeContainer immutableAttributes = ((AttributeContainerInternal) attributes).asImmutable();
        return new ArtifactVisitor() {
            @Override
            public void visitArtifact(final ResolvedArtifact artifact) {
                if (matchArtifactsAttributes(artifact, immutableAttributes)) {
                    visitor.visitArtifact(artifact);
                    return;
                }
                List<ResolvedArtifact> transformResults = transformedArtifacts.get(Pair.of(artifact, immutableAttributes));
                if (transformResults != null) {
                    for (ResolvedArtifact resolvedArtifact : transformResults) {
                        visitor.visitArtifact(resolvedArtifact);
                    }
                    return;
                }
                final Transformer<List<File>, File> transform = getTransform(artifact, immutableAttributes);
                if (transform == null) {
                    return;
                }
                TaskDependency buildDependencies = ((Buildable) artifact).getBuildDependencies();

                transformResults = Lists.newArrayList();
                List<File> transformedFiles = transform.transform(artifact.getFile());
                for (final File output : transformedFiles) {
                    ComponentArtifactIdentifier newId = new ComponentFileArtifactIdentifier(artifact.getId().getComponentIdentifier(), output.getName());
                    IvyArtifactName artifactName = DefaultIvyArtifactName.forAttributeContainer(output.getName(), immutableAttributes);
                    ResolvedArtifact resolvedArtifact = new DefaultResolvedArtifact(artifact.getModuleVersion().getId(), artifactName, newId, buildDependencies, output);
                    transformResults.add(resolvedArtifact);
                    visitor.visitArtifact(resolvedArtifact);
                }
                transformedArtifacts.put(Pair.of(artifact, immutableAttributes), transformResults);
            }

            @Override
            public boolean includeFiles() {
                return visitor.includeFiles();
            }

            @Override
            public void visitFiles(@Nullable ComponentIdentifier componentIdentifier, Iterable<File> files) {
                List<File> result = new ArrayList<File>();
                RuntimeException transformException = null;
                try {
                    for (File file : files) {
                        try {
                            HasAttributes fileWithAttributes = DefaultArtifactAttributes.forFile(file);
                            if (matchArtifactsAttributes(fileWithAttributes, immutableAttributes)) {
                                result.add(file);
                                continue;
                            }
                            List<File> transformResults = transformedFiles.get(Pair.of(file, immutableAttributes));
                            if (transformResults != null) {
                                result.addAll(transformResults);
                                continue;
                            }
                            Transformer<List<File>, File> transform = getTransform(fileWithAttributes, immutableAttributes);
                            if (transform == null) {
                                continue;
                            }
                            transformResults = transform.transform(file);
                            transformedFiles.put(Pair.of(file, immutableAttributes), transformResults);
                            result.addAll(transformResults);
                        } catch (RuntimeException e) {
                            transformException = e;
                            break;
                        }
                    }
                } catch (Throwable t) {
                    //TODO JJ: this lets the wrapped visitor report issues during file access
                    visitor.visitFiles(componentIdentifier, files);
                }
                if (transformException != null) {
                    throw transformException;
                }
                if (!result.isEmpty()) {
                    visitor.visitFiles(componentIdentifier, result);
                }
            }
        };
    }
}
