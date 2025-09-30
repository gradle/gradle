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
import org.gradle.api.internal.plugins.Definition;
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

public class ProjectFeatureSupportInternal {

    public interface ProjectFeatureDefinitionContext {
        Object getBuildModel();

        Map<ProjectFeatureImplementation<?, ?>, Object> childrenDefinitions();

        ChildDefinitionAdditionResult getOrAddChildDefinition(ProjectFeatureImplementation<?, ?> feature, Supplier<Object> definition);

        final class ChildDefinitionAdditionResult {
            public final boolean isNew;
            public final Object definition;

            public ChildDefinitionAdditionResult(boolean isNew, Object definition) {
                this.isNew = isNew;
                this.definition = definition;
            }
        }

        ProjectFeatureApplicator getProjectFeatureApplicator();

        ProjectFeatureRegistry getProjectFeatureRegistry();

        ObjectFactory objectFactory();
    }

    public static class DefaultProjectFeatureDefinitionContext implements ProjectFeatureDefinitionContext {
        private final ProjectFeatureApplicator projectFeatureApplicator;
        private final ProjectFeatureRegistry projectFeatureRegistry;
        private final ObjectFactory objectFactory;
        protected final Object buildModel;
        private final Map<ProjectFeatureImplementation<?, ?>, Object> childrenDefinitions = new LinkedHashMap<>();

        public static class Factory {
            private final ProjectFeatureApplicator projectFeatureApplicator;
            private final ProjectFeatureRegistry projectFeatureRegistry;
            private final ObjectFactory objectFactory;

            @Inject
            public Factory(ProjectFeatureApplicator projectFeatureApplicator, ProjectFeatureRegistry projectFeatureRegistry, ObjectFactory objectFactory) {
                this.projectFeatureApplicator = projectFeatureApplicator;
                this.projectFeatureRegistry = projectFeatureRegistry;
                this.objectFactory = objectFactory;
            }

            public DefaultProjectFeatureDefinitionContext create(Object buildModel) {
                return new DefaultProjectFeatureDefinitionContext(
                    projectFeatureApplicator,
                    projectFeatureRegistry,
                    objectFactory,
                    buildModel
                );
            }
        }

        public DefaultProjectFeatureDefinitionContext(
            ProjectFeatureApplicator projectFeatureApplicator,
            ProjectFeatureRegistry projectFeatureRegistry,
            ObjectFactory objectFactory,
            Object buildModel
        ) {
            this.projectFeatureApplicator = projectFeatureApplicator;
            this.projectFeatureRegistry = projectFeatureRegistry;
            this.objectFactory = objectFactory;
            this.buildModel = buildModel;
        }

        @Override
        public Object getBuildModel() {
            return buildModel;
        }

        @Override
        public Map<ProjectFeatureImplementation<?, ?>, Object> childrenDefinitions() {
            return Collections.unmodifiableMap(childrenDefinitions);
        }

        @Override
        public ChildDefinitionAdditionResult getOrAddChildDefinition(ProjectFeatureImplementation<?, ?> feature, Supplier<Object> computeDefinition) {
            if (childrenDefinitions.containsKey(feature)) {
                return new ChildDefinitionAdditionResult(false, childrenDefinitions.get(feature));
            }
            Object definition = computeDefinition.get();
            childrenDefinitions.put(feature, definition);
            return new ChildDefinitionAdditionResult(true, definition);
        }

        @Override
        public ProjectFeatureApplicator getProjectFeatureApplicator() {
            return projectFeatureApplicator;
        }

        @Override
        public ProjectFeatureRegistry getProjectFeatureRegistry() {
            return projectFeatureRegistry;
        }

        @Override
        public ObjectFactory objectFactory() {
            return objectFactory;
        }
    }

    public static @Nullable ProjectFeatureDefinitionContext tryGetContext(Object definition) {
        DynamicInvokeResult result = ((DynamicObjectAware) definition).getAsDynamicObject().tryInvokeMethod(ProjectFeaturesDynamicObject.CONTEXT_METHOD_NAME);
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
        ProjectFeatureApplicator projectFeatureApplicator,
        ProjectFeatureRegistry projectFeatureRegistry,
        ObjectFactory objectFactory
    ) {
        DynamicObjectAware targetDynamicObjectAware = (DynamicObjectAware) target;
        DefaultProjectFeatureDefinitionContext context = new DefaultProjectFeatureDefinitionContext(projectFeatureApplicator, projectFeatureRegistry, objectFactory, buildModel);
        addProjectFeatureDynamicObjectToDefinition(objectFactory, targetDynamicObjectAware, context);
    }

    public static void attachLegacyDefinitionContext(
        Object target,
        ProjectFeatureApplicator projectFeatureApplicator,
        ProjectFeatureRegistry projectFeatureRegistry,
        ObjectFactory objectFactory
    ) {
        DefaultProjectFeatureDefinitionContext.Factory factory = new DefaultProjectFeatureDefinitionContext.Factory(projectFeatureApplicator, projectFeatureRegistry, objectFactory);
        DefaultProjectFeatureDefinitionContext context = factory.create(target);
        addProjectFeatureDynamicObjectToDefinition(objectFactory, (DynamicObjectAware) target, context);
    }


    public static <T extends Definition<V>, V extends BuildModel> V createBuildModelInstance(ObjectFactory objectFactory, T definition, ProjectFeatureImplementation<T, V> projectFeature) {
        return createBuildModelInstance(objectFactory, definition, projectFeature.getBuildModelImplementationType());
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

    private static void addProjectFeatureDynamicObjectToDefinition(
        ObjectFactory objectFactory,
        DynamicObjectAware dslObjectToInitialize,
        ProjectFeatureDefinitionContext context
    ) {
        ((ExtensibleDynamicObject) dslObjectToInitialize.getAsDynamicObject()).addObject(
            objectFactory.newInstance(ProjectFeaturesDynamicObject.class, dslObjectToInitialize, context),
            ExtensibleDynamicObject.Location.BeforeConvention
        );
    }
}
