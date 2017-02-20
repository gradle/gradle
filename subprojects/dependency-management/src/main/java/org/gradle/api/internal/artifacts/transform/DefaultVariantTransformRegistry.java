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
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformConfiguration;
import org.gradle.api.artifacts.transform.VariantTransform;
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.model.internal.type.ModelType;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class DefaultVariantTransformRegistry implements VariantTransformRegistry {
    private final List<Registration> transforms = Lists.newArrayList();
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final Instantiator instantiator;
    private final Factory<File> outputDirectory;

    public DefaultVariantTransformRegistry(Instantiator instantiator, Factory<File> outputDirectory, ImmutableAttributesFactory immutableAttributesFactory) {
        this.instantiator = instantiator;
        this.outputDirectory = outputDirectory;
        this.immutableAttributesFactory = immutableAttributesFactory;
    }

    @Override
    public void registerTransform(Action<? super VariantTransform> registrationAction) {
        RecordingRegistration reg = instantiator.newInstance(RecordingRegistration.class, immutableAttributesFactory, outputDirectory.create());
        registrationAction.execute(reg);

        if (reg.artifactTransformFactory == null) {
            throw configFailure("Could not register transform: ArtifactTransform must be provided for registration.");
        }

        Registration registration = new DefaultVariantTransformRegistration(ImmutableAttributes.of(reg.from), ImmutableAttributes.of(reg.to), reg.artifactTransformFactory);
        transforms.add(registration);
    }

    public Iterable<Registration> getTransforms() {
        return transforms;
    }

    private static VariantTransformConfigurationException configFailure(String message) {
        return new VariantTransformConfigurationException(message);
    }

    private static VariantTransformConfigurationException configFailure(String message, Throwable cause) {
        return new VariantTransformConfigurationException(message, cause);
    }

    public static class RecordingRegistration implements VariantTransform {
        final AttributeContainerInternal from;
        final AttributeContainerInternal to;
        private final File outputDirectory;
        Factory<ArtifactTransform> artifactTransformFactory;

        public RecordingRegistration(ImmutableAttributesFactory immutableAttributesFactory, File outputDirectory) {
            from = new DefaultMutableAttributeContainer(immutableAttributesFactory);
            to = new DefaultMutableAttributeContainer(immutableAttributesFactory);
            this.outputDirectory = outputDirectory;
        }

        @Override
        public AttributeContainer getFrom() {
            return from;
        }

        @Override
        public AttributeContainer getTo() {
            return to;
        }

        @Override
        public void artifactTransform(Class<? extends ArtifactTransform> type) {
            artifactTransform(type, null);
        }

        @Override
        public void artifactTransform(Class<? extends ArtifactTransform> type, Action<ArtifactTransformConfiguration> config) {
            if (artifactTransformFactory != null) {
                throw configFailure("Could not register transform: only one ArtifactTransform may be provided for registration.");
            }
            artifactTransformFactory = new ArtifactTransformFactory(type, config, outputDirectory);
        }
    }

    private static class ArtifactTransformFactory implements Factory<ArtifactTransform> {
        private final Class<? extends ArtifactTransform> type;
        private final Action<ArtifactTransformConfiguration> configAction;
        private final File outputDir;

        ArtifactTransformFactory(Class<? extends ArtifactTransform> type, @Nullable Action<ArtifactTransformConfiguration> configAction, File outputDir) {
            this.type = type;
            this.configAction = configAction;
            this.outputDir = outputDir;
        }

        @Override
        public ArtifactTransform create() {
            try {
                Object[] params = getTransformParameters();
                ArtifactTransform artifactTransform = null;
                artifactTransform = params.length == 0
                    ? DirectInstantiator.INSTANCE.newInstance(type)
                    : DirectInstantiator.INSTANCE.newInstance(type, params);
                artifactTransform.setOutputDirectory(outputDir);
                return artifactTransform;
            } catch (ObjectInstantiationException e) {
                throw configFailure("Could not create instance of " + ModelType.of(type).getDisplayName() + ".", e.getCause());
            } catch (RuntimeException e) {
                throw new VariantTransformConfigurationException("Could not create instance of " + ModelType.of(type).getDisplayName() + ".", e);
            }
        }

        private Object[] getTransformParameters() {
            if (configAction == null) {
                return new Object[0];
            }
            ArtifactTransformConfiguration config = new DefaultArtifactTransformConfiguration();
            configAction.execute(config);
            return config.getParams();
        }
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

}
