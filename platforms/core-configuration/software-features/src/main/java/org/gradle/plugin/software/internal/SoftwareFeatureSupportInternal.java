/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugin.software.internal;

import org.gradle.api.Named;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.plugins.BuildModel;
import org.gradle.api.internal.plugins.HasBuildModel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.extensibility.ExtensibleDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class SoftwareFeatureSupportInternal {

    public interface ProjectFeatureDefinitionContext {
        Object getBuildModel();

        Map<SoftwareFeatureImplementation<?, ?>, Object> childrenDefinitions();

        ChildDefinitionAdditionResult getOrAddChildDefinition(SoftwareFeatureImplementation<?, ?> feature, Supplier<Object> definition);

        final class ChildDefinitionAdditionResult {
            public final boolean isNew;
            public final Object definition;

            public ChildDefinitionAdditionResult(boolean isNew, Object definition) {
                this.isNew = isNew;
                this.definition = definition;
            }
        }

        SoftwareFeatureApplicator getSoftwareFeatureApplicator();

        SoftwareFeatureRegistry getSoftwareFeatureRegistry();

        ObjectFactory objectFactory();
    }

    public static class DefaultProjectFeatureDefinitionContext implements ProjectFeatureDefinitionContext {
        private final SoftwareFeatureApplicator softwareFeatureApplicator;
        private final SoftwareFeatureRegistry softwareFeatureRegistry;
        private final ObjectFactory objectFactory;
        protected final Object buildModel;
        private final Map<SoftwareFeatureImplementation<?, ?>, Object> childrenDefinitions = new LinkedHashMap<>();

        public static class Factory {
            private final SoftwareFeatureApplicator softwareFeatureApplicator;
            private final SoftwareFeatureRegistry softwareFeatureRegistry;
            private final ObjectFactory objectFactory;

            @Inject
            public Factory(SoftwareFeatureApplicator softwareFeatureApplicator, SoftwareFeatureRegistry softwareFeatureRegistry, ObjectFactory objectFactory) {
                this.softwareFeatureApplicator = softwareFeatureApplicator;
                this.softwareFeatureRegistry = softwareFeatureRegistry;
                this.objectFactory = objectFactory;
            }

            public DefaultProjectFeatureDefinitionContext create(Object buildModel) {
                return new DefaultProjectFeatureDefinitionContext(
                    softwareFeatureApplicator,
                    softwareFeatureRegistry,
                    objectFactory,
                    buildModel
                );
            }
        }

        public DefaultProjectFeatureDefinitionContext(
            SoftwareFeatureApplicator softwareFeatureApplicator,
            SoftwareFeatureRegistry softwareFeatureRegistry,
            ObjectFactory objectFactory,
            Object buildModel
        ) {
            this.softwareFeatureApplicator = softwareFeatureApplicator;
            this.softwareFeatureRegistry = softwareFeatureRegistry;
            this.objectFactory = objectFactory;
            this.buildModel = buildModel;
        }

        @Override
        public Object getBuildModel() {
            return buildModel;
        }

        @Override
        public Map<SoftwareFeatureImplementation<?, ?>, Object> childrenDefinitions() {
            return Collections.unmodifiableMap(childrenDefinitions);
        }

        @Override
        public ChildDefinitionAdditionResult getOrAddChildDefinition(SoftwareFeatureImplementation<?, ?> feature, Supplier<Object> computeDefinition) {
            if (childrenDefinitions.containsKey(feature)) {
                return new ChildDefinitionAdditionResult(false, childrenDefinitions.get(feature));
            }
            Object definition = computeDefinition.get();
            childrenDefinitions.put(feature, definition);
            return new ChildDefinitionAdditionResult(true, definition);
        }

        @Override
        public SoftwareFeatureApplicator getSoftwareFeatureApplicator() {
            return softwareFeatureApplicator;
        }

        @Override
        public SoftwareFeatureRegistry getSoftwareFeatureRegistry() {
            return softwareFeatureRegistry;
        }

        @Override
        public ObjectFactory objectFactory() {
            return objectFactory;
        }
    }

    public static @Nullable ProjectFeatureDefinitionContext tryGetContext(Object definition) {
        DynamicInvokeResult result = ((DynamicObjectAware) definition).getAsDynamicObject().tryInvokeMethod(SoftwareFeaturesDynamicObject.CONTEXT_METHOD_NAME);
        if (result.isFound()) {
            return (ProjectFeatureDefinitionContext) Objects.requireNonNull(result.getValue());
        } else {
            return null;
        }
    }

    public static ProjectFeatureDefinitionContext getContext(DynamicObjectAware definition) {
        Optional<ProjectFeatureDefinitionContext> maybeContext = Optional.ofNullable(tryGetContext(definition));

        return maybeContext.orElseThrow(() ->
            new IllegalStateException("Incorrect lifecycle state for definition '" + definition +
                "'. Expected it to have the context and build model attached already, got none. " +
                "Check that the feature's apply action registers the build model for this definition.")
        );
    }

    public static <V extends BuildModel> void attachDefinitionContext(
        Object target,
        V buildModel,
        SoftwareFeatureApplicator softwareFeatureApplicator,
        SoftwareFeatureRegistry softwareFeatureRegistry,
        ObjectFactory objectFactory
    ) {
        DynamicObjectAware targetDynamicObjectAware = (DynamicObjectAware) target;
        DefaultProjectFeatureDefinitionContext context = new DefaultProjectFeatureDefinitionContext(softwareFeatureApplicator, softwareFeatureRegistry, objectFactory, buildModel);
        addSoftwareFeatureDynamicObjectToDefinition(objectFactory, targetDynamicObjectAware, context);
    }

    public static void attachLegacyDefinitionContext(
        Object target,
        SoftwareFeatureApplicator softwareFeatureApplicator,
        SoftwareFeatureRegistry softwareFeatureRegistry,
        ObjectFactory objectFactory
    ) {
        DefaultProjectFeatureDefinitionContext.Factory factory = new DefaultProjectFeatureDefinitionContext.Factory(softwareFeatureApplicator, softwareFeatureRegistry, objectFactory);
        DefaultProjectFeatureDefinitionContext context = factory.create(target);
        addSoftwareFeatureDynamicObjectToDefinition(objectFactory, (DynamicObjectAware) target, context);
    }


    public static <T extends HasBuildModel<V>, V extends BuildModel> V createBuildModelInstance(ObjectFactory objectFactory, T definition, SoftwareFeatureImplementation<T, V> softwareFeature) {
        return createBuildModelInstance(objectFactory, definition, softwareFeature.getBuildModelImplementationType());
    }

    public static <V> V createBuildModelInstance(ObjectFactory factory, Object definition, Class<? extends V> buildModelType) {
        if (Named.class.isAssignableFrom(buildModelType)) {
            if (Named.class.isAssignableFrom(definition.getClass())) {
                return factory.newInstance(buildModelType, ((Named) definition).getName());
            } else {
                throw new IllegalArgumentException("Cannot infer a name for " + buildModelType.getSimpleName() + " because the parent object of type " + definition.getClass().getSimpleName() + " does not implement Named.");
            }
        } else {
            return factory.newInstance(buildModelType);
        }
    }

    private static void addSoftwareFeatureDynamicObjectToDefinition(
        ObjectFactory objectFactory,
        DynamicObjectAware dslObjectToInitialize,
        ProjectFeatureDefinitionContext context
    ) {
        ((ExtensibleDynamicObject) dslObjectToInitialize.getAsDynamicObject()).addObject(
            objectFactory.newInstance(SoftwareFeaturesDynamicObject.class, dslObjectToInitialize, context),
            ExtensibleDynamicObject.Location.BeforeConvention
        );
    }
}
