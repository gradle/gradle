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
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformConfiguration;
import org.gradle.api.artifacts.transform.VariantTransform;
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.changedetection.state.GenericFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.ValueSnapshotter;
import org.gradle.internal.reflect.Instantiator;

import java.util.Collections;
import java.util.List;

public class DefaultVariantTransformRegistry implements VariantTransformRegistry {
    private static final Object[] NO_PARAMETERS = new Object[0];
    private final List<Registration> transforms = Lists.newArrayList();
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final TransformedFileCache transformedFileCache;
    private final ArtifactCacheMetaData artifactCacheMetaData;
    private final ValueSnapshotter valueSnapshotter;
    private final GenericFileCollectionSnapshotter fileCollectionSnapshotter;
    private final Instantiator instantiator;

    public DefaultVariantTransformRegistry(Instantiator instantiator, ImmutableAttributesFactory immutableAttributesFactory, TransformedFileCache transformedFileCache, ArtifactCacheMetaData artifactCacheMetaData, ValueSnapshotter valueSnapshotter, GenericFileCollectionSnapshotter fileCollectionSnapshotter) {
        this.instantiator = instantiator;
        this.immutableAttributesFactory = immutableAttributesFactory;
        this.transformedFileCache = transformedFileCache;
        this.artifactCacheMetaData = artifactCacheMetaData;
        this.valueSnapshotter = valueSnapshotter;
        this.fileCollectionSnapshotter = fileCollectionSnapshotter;
    }

    @Override
    public void registerTransform(Action<? super VariantTransform> registrationAction) {
        RecordingRegistration reg = instantiator.newInstance(RecordingRegistration.class, immutableAttributesFactory);
        registrationAction.execute(reg);
        if (reg.type == null) {
            throw new VariantTransformConfigurationException("Could not register transform: ArtifactTransform must be provided for registration.");
        }

        // TODO - should calculate this lazily
        Object[] parameters = getTransformParameters(reg.config);

        Registration registration = new DefaultVariantTransformRegistration(ImmutableAttributes.of(reg.from), ImmutableAttributes.of(reg.to), reg.type, parameters,  transformedFileCache, artifactCacheMetaData, valueSnapshotter, fileCollectionSnapshotter);
        transforms.add(registration);
    }

    public Iterable<Registration> getTransforms() {
        return transforms;
    }

    private Object[] getTransformParameters(Action<ArtifactTransformConfiguration> configAction) {
        if (configAction == null) {
            return NO_PARAMETERS;
        }
        ArtifactTransformConfiguration config = new DefaultArtifactTransformConfiguration();
        configAction.execute(config);
        return config.getParams();
    }

    public static class RecordingRegistration implements VariantTransform {
        final AttributeContainerInternal from;
        final AttributeContainerInternal to;
        private Class<? extends ArtifactTransform> type;
        private Action<ArtifactTransformConfiguration> config;

        public RecordingRegistration(ImmutableAttributesFactory immutableAttributesFactory) {
            from = new DefaultMutableAttributeContainer(immutableAttributesFactory);
            to = new DefaultMutableAttributeContainer(immutableAttributesFactory);
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
            if (this.type != null) {
                throw new VariantTransformConfigurationException("Could not register transform: only one ArtifactTransform may be provided for registration.");
            }
            this.type = type;
            this.config = config;
        }
    }

    private static class DefaultArtifactTransformConfiguration implements ArtifactTransformConfiguration {
        private final List<Object> params = Lists.newArrayList();

        @Override
        public void params(Object... params) {
            Collections.addAll(this.params, params);
        }

        @Override
        public void setParams(Object... params) {
            this.params.clear();
            Collections.addAll(this.params, params);
        }

        @Override
        public Object[] getParams() {
            return this.params.toArray();
        }
    }

}
