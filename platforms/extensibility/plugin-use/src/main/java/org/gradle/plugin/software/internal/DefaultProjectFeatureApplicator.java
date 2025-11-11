/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.BuildModel;
import org.gradle.api.internal.plugins.Definition;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.plugins.ProjectFeatureApplicationContext;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.Cast;
import org.gradle.plugin.software.internal.ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext;

/**
 * Applies project features to a target object by registering the software model as an extension of the target object (unless
 * configured otherwise) and performing validations.  Application returns the public model object of the feature.  Features
 * are applied only once per target object and always return the same public model object for a given target/feature
 * combination.
 */
public class DefaultProjectFeatureApplicator implements ProjectFeatureApplicator {
    private final ProjectFeatureDeclarations projectFeatureDeclarations;
    private final ModelDefaultsApplicator modelDefaultsApplicator;
    private final PluginManagerInternal pluginManager;
    private final ClassLoaderScope classLoaderScope;
    private final ObjectFactory objectFactory;

    public DefaultProjectFeatureApplicator(ProjectFeatureDeclarations projectFeatureDeclarations, ModelDefaultsApplicator modelDefaultsApplicator, PluginManagerInternal pluginManager, ClassLoaderScope classLoaderScope, ObjectFactory objectFactory) {
        this.projectFeatureDeclarations = projectFeatureDeclarations;
        this.modelDefaultsApplicator = modelDefaultsApplicator;
        this.pluginManager = pluginManager;
        this.classLoaderScope = classLoaderScope;
        this.objectFactory = objectFactory;
    }

    @Override
    public <T extends Definition<V>, V extends BuildModel> T applyFeatureTo(DynamicObjectAware parentDefinition, ProjectFeatureImplementation<T, V> projectFeature) {
        ProjectFeatureDefinitionContext parentDefinitionContext = ProjectFeatureSupportInternal.getContext(parentDefinition);

        ProjectFeatureDefinitionContext.ChildDefinitionAdditionResult result = parentDefinitionContext.getOrAddChildDefinition(projectFeature, () -> {
            if (parentDefinition instanceof Project) {
                checkSingleProjectTypeApplication(parentDefinitionContext, projectFeature);
            }

            pluginManager.apply(projectFeature.getPluginClass());

            Object definition = instantiateBoundFeatureObjectsAndApply(parentDefinition, projectFeature);

            return Cast.uncheckedNonnullCast(definition);
        });

        if (result.isNew) {
            Plugin<Project> plugin = pluginManager.getPluginContainer().getPlugin(projectFeature.getPluginClass());
            modelDefaultsApplicator.applyDefaultsTo(parentDefinition, result.definition, new ClassLoaderContextFromScope(classLoaderScope), plugin, projectFeature);
        }

        return Cast.uncheckedNonnullCast(result.definition);
    }

    private static <T extends Definition<V>, V extends BuildModel> void checkSingleProjectTypeApplication(ProjectFeatureDefinitionContext context, ProjectFeatureImplementation<T, V> projectFeature) {
        context.childrenDefinitions().keySet().stream().findFirst().ifPresent(projectTypeAlreadyApplied -> {
            throw new IllegalStateException(
                "The project has already applied the '" +
                    projectTypeAlreadyApplied.getFeatureName() +
                    "' project type and is also attempting to apply the '" +
                    projectFeature.getFeatureName() +
                    "' project type.  Only one project type can be applied to a project."
            );
        });
    }

    private <T extends Definition<V>, V extends BuildModel> T instantiateBoundFeatureObjectsAndApply(Object parentDefinition, ProjectFeatureImplementation<T, V> projectFeature) {
        T definition = instantiateDefinitionObject(parentDefinition, projectFeature);
        V buildModelInstance = ProjectFeatureSupportInternal.createBuildModelInstance(objectFactory, definition, projectFeature);
        ProjectFeatureSupportInternal.attachDefinitionContext(definition, buildModelInstance, this, projectFeatureDeclarations, objectFactory);

        ProjectFeatureApplicationContext applyActionContext =
            objectFactory.newInstance(ProjectFeatureApplicationContextInternal.class);

        projectFeature.getBindingTransform().transform(applyActionContext, definition, buildModelInstance, Cast.uncheckedCast(parentDefinition));

        return definition;
    }

    private <T extends Definition<V>, V extends BuildModel> T instantiateDefinitionObject(Object target, ProjectFeatureImplementation<T, V> projectFeature) {
        Class<? extends T> dslType = projectFeature.getDefinitionImplementationType();

        if (Named.class.isAssignableFrom(dslType)) {
            if (target instanceof Named) {
                return objectFactory.newInstance(projectFeature.getDefinitionPublicType(), ((Named) target).getName());
            } else {
                throw new IllegalArgumentException("Cannot infer a name for definition " + dslType.getSimpleName() +
                    " because the parent definition of type " + target.getClass().getSimpleName() + " does not implement Named.");
            }
        } else {
            return objectFactory.newInstance(dslType);
        }
    }

    private static class ClassLoaderContextFromScope implements ModelDefaultsApplicator.ClassLoaderContext {
        private final ClassLoaderScope scope;

        public ClassLoaderContextFromScope(ClassLoaderScope scope) {
            this.scope = scope;
        }

        @Override
        public ClassLoader getClassLoader() {
            return scope.getLocalClassLoader();
        }

        @Override
        public ClassLoader getParentClassLoader() {
            return scope.getParent().getLocalClassLoader();
        }
    }
}
