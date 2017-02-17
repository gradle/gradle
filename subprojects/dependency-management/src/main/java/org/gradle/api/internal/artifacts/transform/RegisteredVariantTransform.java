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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformConfiguration;
import org.gradle.api.artifacts.transform.ArtifactTransformException;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.reflect.DirectInstantiator;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.List;

class RegisteredVariantTransform {
    private final AttributeContainerInternal from;
    private final AttributeContainerInternal to;
    private final Class<? extends ArtifactTransform> type;
    private final Action<ArtifactTransformConfiguration> configAction;
    private final Transformer<List<File>, File> transform;

    RegisteredVariantTransform(AttributeContainerInternal from, AttributeContainerInternal to, Class<? extends ArtifactTransform> type, Action<ArtifactTransformConfiguration> configAction, File outputDir) {
        this.from = from;
        this.to = to;
        this.type = type;
        this.configAction = configAction;
        this.transform = createArtifactTransformer(outputDir);
    }

    public AttributeContainerInternal getFrom() {
        return from;
    }

    public AttributeContainerInternal getTo() {
        return to;
    }

    public Class<? extends ArtifactTransform> getType() {
        return type;
    }

    public Transformer<List<File>, File> getTransform() {
        return transform;
    }

    private Transformer<List<File>, File> createArtifactTransformer(File outputDir) {
        ArtifactTransformConfiguration config = new DefaultArtifactTransformConfiguration();
        configAction.execute(config);
        Object[] params = config.getParams();
        ArtifactTransform artifactTransform = params.length == 0
            ? DirectInstantiator.INSTANCE.newInstance(type)
            : DirectInstantiator.INSTANCE.newInstance(type, params);
        artifactTransform.setOutputDirectory(outputDir);
        return new ArtifactFileTransformer(artifactTransform, to);
    }

    private static class DefaultArtifactTransformConfiguration implements ArtifactTransformConfiguration {
        private final List<Object> params = Lists.newArrayList();

        @Override
        public void params(Object... params) {
            Collections.addAll(this.params, params);
        }

        @Override
        public Object[] getParams() {
            return this.params.toArray();
        }
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
