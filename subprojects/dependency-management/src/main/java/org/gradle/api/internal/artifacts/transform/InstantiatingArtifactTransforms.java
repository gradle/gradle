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

import org.gradle.api.AttributeContainer;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformException;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.internal.reflect.DirectInstantiator;

import java.io.File;
import java.io.FileNotFoundException;

class InstantiatingArtifactTransforms implements ArtifactTransforms {
    private final ResolutionStrategyInternal resolutionStrategy;
    private final ArtifactAttributeMatcher attributeMatcher;

    public InstantiatingArtifactTransforms(ResolutionStrategyInternal resolutionStrategy, ArtifactAttributeMatcher attributeMatcher) {
        this.resolutionStrategy = resolutionStrategy;
        this.attributeMatcher = attributeMatcher;
    }

    @Override
    public Transformer<File, File> getTransform(AttributeContainer from, AttributeContainer to) {
        for (ArtifactTransformRegistrations.ArtifactTransformRegistration transformReg : resolutionStrategy.getTransforms()) {
            if (attributeMatcher.attributesMatch(from, transformReg.from)
                && attributeMatcher.attributesMatch(to, transformReg.to)) {
                return createArtifactTransformer(transformReg);
            }
        }
        return null;
    }

    private Transformer<File, File> createArtifactTransformer(ArtifactTransformRegistrations.ArtifactTransformRegistration registration) {
        ArtifactTransform artifactTransform = DirectInstantiator.INSTANCE.newInstance(registration.type);
        registration.config.execute(artifactTransform);
        return new ArtifactFileTransformer(artifactTransform, registration.to);
    }

    private static class ArtifactFileTransformer implements Transformer<File, File> {
        private final ArtifactTransform artifactTransform;
        private final AttributeContainer outputAttributes;

        private ArtifactFileTransformer(ArtifactTransform artifactTransform, AttributeContainer outputAttributes) {
            this.artifactTransform = artifactTransform;
            this.outputAttributes = outputAttributes;
        }

        @Override
        public File transform(File input) {
            if (artifactTransform.getOutputDirectory() != null) {
                artifactTransform.getOutputDirectory().mkdirs();
            }
            File output = doTransform(input);
            if (output == null) {
                throw new ArtifactTransformException(input, outputAttributes, artifactTransform, new FileNotFoundException("No output file created"));
            }
            if (!output.exists()) {
                throw new ArtifactTransformException(input, outputAttributes, artifactTransform, new FileNotFoundException("Expected output file '" + output.getPath() + "' was not created"));
            }
            return output;
        }

        private File doTransform(File input) {
            try {
                return artifactTransform.transform(input, outputAttributes);
            } catch (Exception e) {
                throw new ArtifactTransformException(input, outputAttributes, artifactTransform, e);
            }
        }
    }

}
