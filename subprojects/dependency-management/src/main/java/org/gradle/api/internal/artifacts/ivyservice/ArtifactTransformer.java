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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.AttributeContainer;
import org.gradle.api.AttributesSchema;
import org.gradle.api.Buildable;
import org.gradle.api.HasAttributes;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.transform.internal.ArtifactTransforms;
import org.gradle.api.internal.AttributeContainerInternal;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.attributes.DefaultArtifactAttributes;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Factory;
import org.gradle.internal.component.model.ComponentAttributeMatcher;
import org.gradle.internal.component.model.DefaultIvyArtifactName;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArtifactTransformer {
    private final ResolutionStrategyInternal resolutionStrategy;
    private final AttributesSchema attributesSchema;
    private final Map<File, File> transformed = new HashMap<File, File>();

    public ArtifactTransformer(ResolutionStrategyInternal resolutionStrategy, AttributesSchema attributesSchema) {
        this.resolutionStrategy = resolutionStrategy;
        this.attributesSchema = attributesSchema;
    }

    private boolean matchArtifactsAttributes(HasAttributes artifact, AttributeContainer configuration) {
        ComponentAttributeMatcher matcher = new ComponentAttributeMatcher(attributesSchema, null,
            Collections.singleton(artifact), configuration);
        return !matcher.hasFailingMatches();
    }

    private Transformer<File, File> getTransform(HasAttributes from, AttributeContainer to) {
        for (ArtifactTransforms.DependencyTransformRegistration transformReg : resolutionStrategy.getTransforms()) {
            if (matchArtifactsAttributes(from, transformReg.getFrom())
                && matchArtifactsAttributes(to, transformReg.getTo())) {
                return transformReg.getTransformer();
            }
        }
        return null;
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
    public ArtifactVisitor visitor(final ArtifactVisitor visitor, @Nullable final AttributeContainer attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return visitor;
        }
        return new ArtifactVisitor() {
            @Override
            public void visitArtifact(final ResolvedArtifact artifact) {
                if (matchArtifactsAttributes(artifact, attributes)) {
                    visitor.visitArtifact(artifact);
                    return;
                }
                final Transformer<File, File> transform = getTransform(artifact, attributes);
                if (transform == null) {
                    return;
                }
                TaskDependency buildDependencies = ((Buildable) artifact).getBuildDependencies();

                AttributeContainer transformedAttributes = ((AttributeContainerInternal) attributes).copy();

                visitor.visitArtifact(new DefaultResolvedArtifact(artifact.getModuleVersion().getId(),
                    DefaultIvyArtifactName.forAttributeContainer(artifact.getName(), transformedAttributes), artifact.getId(), buildDependencies, new Factory<File>() {
                    @Override
                    public File create() {
                        File file = artifact.getFile();
                        File transformedFile = transformed.get(file);
                        if (transformedFile == null) {
                            transformedFile = transform.transform(file);
                            transformed.put(file, transformedFile);
                        }
                        return transformedFile;
                    }
                }));
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
                            if (matchArtifactsAttributes(fileWithAttributes, attributes)) {
                                result.add(file);
                                continue;
                            }
                            File transformedFile = transformed.get(file);
                            if (transformedFile != null) {
                                result.add(transformedFile);
                                continue;
                            }
                            Transformer<File, File> transform = getTransform(fileWithAttributes, attributes);
                            if (transform == null) {
                                continue;
                            }
                            transformedFile = transform.transform(file);
                            transformed.put(file, transformedFile);
                            result.add(transformedFile);
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
