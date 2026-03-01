/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal.binding;

import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.features.binding.BuildModel;
import org.gradle.features.binding.Definition;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.extensibility.ExtensibleDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.gradle.internal.Cast.uncheckedCast;

public class ProjectFeatureSupportInternal {
    private static final BuildModel.None NONE = new BuildModel.None();

    public interface ProjectFeatureDefinitionContext {
        Object getBuildModel();

        // Child features that have been bound to this definition
        Map<ProjectFeatureImplementation<?, ?>, ProjectFeatureApplicator.FeatureApplication<?, ?>> childFeatures();
        // Implicit nested definitions discovered while inspecting the definition
        List<Definition<?>> nestedDefinitions();

        <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> ChildDefinitionAdditionResult<OwnDefinition, OwnBuildModel> getOrAddChildDefinition(ProjectFeatureImplementation<OwnDefinition, OwnBuildModel> feature, Supplier<ProjectFeatureApplicator.FeatureApplication<OwnDefinition, OwnBuildModel>> definition);

        void addNestedDefinition(Definition<?> definition);

        final class ChildDefinitionAdditionResult<OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> {
            public final boolean isNew;
            public final ProjectFeatureApplicator.FeatureApplication<OwnDefinition, OwnBuildModel> featureApplication;

            public ChildDefinitionAdditionResult(boolean isNew, ProjectFeatureApplicator.FeatureApplication<OwnDefinition, OwnBuildModel> featureApplication) {
                this.isNew = isNew;
                this.featureApplication = featureApplication;
            }
        }

        ProjectFeatureApplicator getProjectFeatureApplicator();

        ProjectFeatureDeclarations getProjectFeatureRegistry();

        ObjectFactory objectFactory();
    }

    public static class DefaultProjectFeatureDefinitionContext implements ProjectFeatureDefinitionContext {
        private final ProjectFeatureApplicator projectFeatureApplicator;
        private final ProjectFeatureDeclarations projectFeatureDeclarations;
        private final ObjectFactory objectFactory;
        protected final Object buildModel;
        private final Map<ProjectFeatureImplementation<?, ?>, ProjectFeatureApplicator.FeatureApplication<?, ?>> childFeatures = new LinkedHashMap<>();
        private final List<Definition<?>> nestedDefinitions = new ArrayList<>();

        public static class Factory {
            private final ProjectFeatureApplicator projectFeatureApplicator;
            private final ProjectFeatureDeclarations projectFeatureDeclarations;
            private final ObjectFactory objectFactory;

            @Inject
            public Factory(ProjectFeatureApplicator projectFeatureApplicator, ProjectFeatureDeclarations projectFeatureDeclarations, ObjectFactory objectFactory) {
                this.projectFeatureApplicator = projectFeatureApplicator;
                this.projectFeatureDeclarations = projectFeatureDeclarations;
                this.objectFactory = objectFactory;
            }

            public DefaultProjectFeatureDefinitionContext create(Object buildModel) {
                return new DefaultProjectFeatureDefinitionContext(
                    projectFeatureApplicator,
                    projectFeatureDeclarations,
                    objectFactory,
                    buildModel
                );
            }
        }

        public DefaultProjectFeatureDefinitionContext(
            ProjectFeatureApplicator projectFeatureApplicator,
            ProjectFeatureDeclarations projectFeatureDeclarations,
            ObjectFactory objectFactory,
            Object buildModel
        ) {
            this.projectFeatureApplicator = projectFeatureApplicator;
            this.projectFeatureDeclarations = projectFeatureDeclarations;
            this.objectFactory = objectFactory;
            this.buildModel = buildModel;
        }

        @Override
        public Object getBuildModel() {
            return buildModel;
        }

        @Override
        public Map<ProjectFeatureImplementation<?, ?>, ProjectFeatureApplicator.FeatureApplication<?, ?>> childFeatures() {
            return Collections.unmodifiableMap(childFeatures);
        }

        @Override
        public List<Definition<?>> nestedDefinitions() {
            return Collections.unmodifiableList(nestedDefinitions);
        }

        @Override
        public void addNestedDefinition(Definition<?> definition) {
            nestedDefinitions.add(definition);
        }

        @Override
        public <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel>
        ChildDefinitionAdditionResult<OwnDefinition, OwnBuildModel> getOrAddChildDefinition(
            ProjectFeatureImplementation<OwnDefinition, OwnBuildModel> feature,
            Supplier<ProjectFeatureApplicator.FeatureApplication<OwnDefinition, OwnBuildModel>> featureApplicationSupplier
        ) {
            if (childFeatures.containsKey(feature)) {
                return new ChildDefinitionAdditionResult<>(false, Cast.uncheckedCast(childFeatures.get(feature)));
            }
            ProjectFeatureApplicator.FeatureApplication<OwnDefinition, OwnBuildModel> featureApplication = featureApplicationSupplier.get();
            childFeatures.put(feature, featureApplication);
            return new ChildDefinitionAdditionResult<>(true, featureApplication);
        }

        @Override
        public ProjectFeatureApplicator getProjectFeatureApplicator() {
            return projectFeatureApplicator;
        }

        @Override
        public ProjectFeatureDeclarations getProjectFeatureRegistry() {
            return projectFeatureDeclarations;
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
        ProjectFeatureDeclarations projectFeatureDeclarations,
        ObjectFactory objectFactory
    ) {
        DynamicObjectAware targetDynamicObjectAware = (DynamicObjectAware) target;
        DefaultProjectFeatureDefinitionContext context = new DefaultProjectFeatureDefinitionContext(projectFeatureApplicator, projectFeatureDeclarations, objectFactory, buildModel);
        addProjectFeatureDynamicObjectToDefinition(objectFactory, targetDynamicObjectAware, context);
    }

    public static void attachLegacyDefinitionContext(
        Object target,
        ProjectFeatureApplicator projectFeatureApplicator,
        ProjectFeatureDeclarations projectFeatureDeclarations,
        ObjectFactory objectFactory
    ) {
        DefaultProjectFeatureDefinitionContext.Factory factory = new DefaultProjectFeatureDefinitionContext.Factory(projectFeatureApplicator, projectFeatureDeclarations, objectFactory);
        DefaultProjectFeatureDefinitionContext context = factory.create(target);
        addProjectFeatureDynamicObjectToDefinition(objectFactory, (DynamicObjectAware) target, context);
    }

    public static <T extends Definition<V>, V extends BuildModel> V createBuildModelInstance(ObjectFactory objectFactory, ProjectFeatureImplementation<T, V> projectFeature) {
        return createBuildModelInstance(objectFactory, projectFeature.getBuildModelImplementationType());
    }

    public static <V> V createBuildModelInstance(ObjectFactory factory, Class<? extends V> buildModelType) {
        if (buildModelType == BuildModel.None.class) {
            return uncheckedCast(NONE);
        }

        return factory.newInstance(buildModelType);
    }

    private static void addProjectFeatureDynamicObjectToDefinition(
        ObjectFactory objectFactory,
        DynamicObjectAware dslObjectToInitialize,
        ProjectFeatureDefinitionContext context
    ) {
        ((ExtensibleDynamicObject) dslObjectToInitialize.getAsDynamicObject()).addObject(
            objectFactory.newInstance(ProjectFeaturesDynamicObject.class, dslObjectToInitialize, context),
            ExtensibleDynamicObject.Location.BeforeConventionNotInherited
        );
    }

    /**
     * Walks the feature graph of an object and applies all features that are bound to it or any of its nested definitions.
     */
    public static void walkAndApplyFeatures(Object definition) {
        walkAndApplyFeatures(definition, new HashSet<>());
    }

    private static void walkAndApplyFeatures(Object definition, Set<Object> seen) {
        if (seen.add(definition)) {
            ProjectFeatureDefinitionContext context = ProjectFeatureSupportInternal.tryGetContext(definition);

            if (context != null) {
                // Apply any features directly applied to this definition
                context.childFeatures().values().forEach(child -> {
                    child.apply();
                    walkAndApplyFeatures(child.getDefinitionInstance(), seen);
                });
                // Apply any features applied to implicitly nested definitions discovered by inspecting this definition
                context.nestedDefinitions().forEach(nested -> walkAndApplyFeatures(nested, seen));
            }
        }
    }
}
