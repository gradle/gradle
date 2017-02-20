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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformException;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Factory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

class DefaultVariantTransformRegistration implements VariantTransformRegistry.Registration {
    private final ImmutableAttributes from;
    private final ImmutableAttributes to;
    private final Transformer<List<File>, File> transform;

    DefaultVariantTransformRegistration(AttributeContainerInternal from, AttributeContainerInternal to, Factory<ArtifactTransform> artifactTransformFactory) {
        this.from = from.asImmutable();
        this.to = to.asImmutable();
        // TODO:DAZ Maybe create on demand
        this.transform = createArtifactTransformer(artifactTransformFactory);
    }

    public AttributeContainerInternal getFrom() {
        return from;
    }

    public AttributeContainerInternal getTo() {
        return to;
    }

    public Transformer<List<File>, File> getArtifactTransform() {
        return transform;
    }

    private Transformer<List<File>, File> createArtifactTransformer(Factory<ArtifactTransform> artifactTransformFactory) {
        return new ArtifactFileTransformer(artifactTransformFactory.create(), to);
    }

    private static class ArtifactFileTransformer implements Transformer<List<File>, File> {
        private final ArtifactTransform artifactTransform;
        private final AttributeContainer outputAttributes;

        private ArtifactFileTransformer(ArtifactTransform artifactTransform, AttributeContainer outputAttributes) {
            this.artifactTransform = artifactTransform;
            this.outputAttributes = outputAttributes;
        }

        @Override
        public List<File> transform(File input) {
            if (artifactTransform.getOutputDirectory() != null) {
                artifactTransform.getOutputDirectory().mkdirs();
            }
            List<File> outputs = doTransform(input);
            if (outputs == null) {
                throw new ArtifactTransformException(input, outputAttributes, artifactTransform, new NullPointerException("Illegal null output from ArtifactTransform"));
            }
            for (File output : outputs) {
                if (!output.exists()) {
                    throw new ArtifactTransformException(input, outputAttributes, artifactTransform, new FileNotFoundException("ArtifactTransform output '" + output.getPath() + "' does not exist"));
                }
            }
            return outputs;
        }

        private List<File> doTransform(File input) {
            try {
                return artifactTransform.transform(input);
            } catch (Exception e) {
                throw new ArtifactTransformException(input, outputAttributes, artifactTransform, e);
            }
        }
    }
}
