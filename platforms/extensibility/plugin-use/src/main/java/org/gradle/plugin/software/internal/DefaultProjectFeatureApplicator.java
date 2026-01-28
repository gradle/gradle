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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.file.DefaultProjectFeatureLayout;
import org.gradle.api.internal.file.ProjectFeatureLayout;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.model.ObjectFactoryFactory;
import org.gradle.api.internal.plugins.BuildModel;
import org.gradle.api.internal.plugins.Definition;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.plugins.ProjectFeatureApplicationContext;
import org.gradle.api.internal.registration.DefaultTaskRegistrar;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.internal.registration.TaskRegistrar;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.Cast;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.UnknownServiceException;
import org.gradle.plugin.software.internal.ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

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
    private final ObjectFactory projectObjectFactory;
    private final ObjectFactoryFactory objectFactoryFactory;
    private final TaskContainer taskContainer;
    private final ProjectLayout projectLayout;
    private final ServiceLookup allServices;

    public DefaultProjectFeatureApplicator(ProjectFeatureDeclarations projectFeatureDeclarations, ModelDefaultsApplicator modelDefaultsApplicator, PluginManagerInternal pluginManager, ClassLoaderScope classLoaderScope, ObjectFactory projectObjectFactory, ObjectFactoryFactory objectFactoryFactory, TaskContainer taskContainer, ProjectLayout projectLayout, ServiceLookup allServices) {
        this.projectFeatureDeclarations = projectFeatureDeclarations;
        this.modelDefaultsApplicator = modelDefaultsApplicator;
        this.pluginManager = pluginManager;
        this.classLoaderScope = classLoaderScope;
        this.projectObjectFactory = projectObjectFactory;
        this.objectFactoryFactory = objectFactoryFactory;
        this.taskContainer = taskContainer;
        this.projectLayout = projectLayout;
        this.allServices = allServices;
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
        T definition = instantiateDefinitionObject(projectFeature);
        V buildModelInstance = ProjectFeatureSupportInternal.createBuildModelInstance(projectObjectFactory, projectFeature);
        ProjectFeatureSupportInternal.attachDefinitionContext(definition, buildModelInstance, this, projectFeatureDeclarations, projectObjectFactory);

        // Context-specific services for this feature binding
        TaskRegistrar taskRegistrar = new DefaultTaskRegistrar(taskContainer);
        ProjectFeatureLayout projectFeatureLayout = new DefaultProjectFeatureLayout(projectLayout);

        // Construct an object factory that provides these services during apply action execution
        ObjectFactory featureObjectFactory = objectFactoryFactory.createObjectFactory(new UnsafeServicesForApplyAction(allServices, taskRegistrar, projectFeatureLayout));

        ProjectFeatureApplicationContext applyActionContext =
            projectObjectFactory.newInstance(DefaultProjectFeatureApplicationContextInternal.class, featureObjectFactory);

        projectFeature.getBindingTransform().transform(applyActionContext, definition, buildModelInstance, Cast.uncheckedCast(parentDefinition));

        return definition;
    }

    private <T extends Definition<V>, V extends BuildModel> T instantiateDefinitionObject(ProjectFeatureImplementation<T, V> projectFeature) {
        return projectObjectFactory.newInstance(projectFeature.getDefinitionImplementationType());
    }

    /**
     * The internal implementation of the context passed to project feature apply actions, exposing an object factory
     * appropriate for the configured safety of the apply action.
     */
    abstract static class DefaultProjectFeatureApplicationContextInternal implements ProjectFeatureApplicationContextInternal {
        private final ObjectFactory objectFactory;

        @Inject
        @SuppressWarnings("Unused")
        public DefaultProjectFeatureApplicationContextInternal(ObjectFactory objectFactory) {
            this.objectFactory = objectFactory;
        }

        @Override
        public ObjectFactory getObjectFactory() {
            return objectFactory;
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

    /**
     * A limited service lookup for use during feature apply actions, exposing both safe and unsafe services.
     */
    private static class UnsafeServicesForApplyAction implements ServiceLookup {
        private final ServiceLookup allServices;
        private final TaskRegistrar taskRegistrar;
        private final ProjectFeatureLayout projectFeatureLayout;

        public UnsafeServicesForApplyAction(ServiceLookup allServices, TaskRegistrar taskRegistrar, ProjectFeatureLayout projectFeatureLayout) {
            this.allServices = allServices;
            this.taskRegistrar = taskRegistrar;
            this.projectFeatureLayout = projectFeatureLayout;
        }

        @Override
        public @Nullable Object find(Type serviceType) throws ServiceLookupException {
            if (serviceType instanceof Class) {
                Class<?> serviceClass = Cast.uncheckedNonnullCast(serviceType);
                if (serviceClass.isAssignableFrom(TaskRegistrar.class)) {
                    return taskRegistrar;
                }
                if (serviceClass.isAssignableFrom(ProjectFeatureLayout.class)) {
                    return projectFeatureLayout;
                }
                return allServices.find(serviceType);
            }
            return null;
        }

        @Override
        public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
            Object result = find(serviceType);
            if (result == null) {
                return notFound(serviceType);
            }
            return result;
        }

        @Override
        public Object get(Type serviceType, Class<? extends Annotation> annotatedWith) throws UnknownServiceException, ServiceLookupException {
            return notFound(serviceType);
        }

        private Object notFound(Type serviceType) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Services of type ");
            formatter.appendType(serviceType);
            formatter.append(" are not available for injection into project feature apply actions.");
            throw new UnknownServiceException(serviceType, formatter.toString());
        }
    }
}
