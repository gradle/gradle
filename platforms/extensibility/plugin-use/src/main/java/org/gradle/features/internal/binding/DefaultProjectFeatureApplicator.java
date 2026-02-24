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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;
import org.gradle.features.binding.ProjectFeatureApplyAction;
import org.gradle.features.internal.file.DefaultProjectFeatureLayout;
import org.gradle.features.file.ProjectFeatureLayout;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.model.ObjectFactoryFactory;
import org.gradle.features.binding.BuildModel;
import org.gradle.features.binding.Definition;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.features.binding.ProjectFeatureApplicationContext;
import org.gradle.features.registration.ConfigurationRegistrar;
import org.gradle.features.internal.registration.DefaultConfigurationRegistrar;
import org.gradle.features.internal.registration.DefaultTaskRegistrar;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.features.registration.TaskRegistrar;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.api.problems.internal.InternalProblemReporter;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.Cast;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.instantiation.managed.ManagedObjectRegistry;
import org.gradle.internal.logging.text.TreeFormatter;

import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata;
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadataStore;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.UnknownServiceException;
import org.gradle.features.internal.binding.ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

/**
 * Applies project features to a target object by registering the feature application as a child of the target object (unless
 * configured otherwise) and performing validations.  Application returns the public model object of the feature.  Features
 * are applied only once per target object and always return the same public model object for a given target/feature
 * combination.
 */
abstract public class DefaultProjectFeatureApplicator implements ProjectFeatureApplicator {
    private final ClassLoaderScope classLoaderScope;
    private final ObjectFactory projectObjectFactory;
    private final InternalProblemReporter problemReporter;
    private final ServiceLookup allServices;
    private final PropertyWalker propertyWalker = new DefaultPropertyWalker(getTypeAnnotationMetadataStore());

    @Inject
    public DefaultProjectFeatureApplicator(
        ClassLoaderScope classLoaderScope,
        ObjectFactory projectObjectFactory,
        InternalProblemReporter problemReporter,
        ServiceLookup allServices
    ) {
        this.classLoaderScope = classLoaderScope;
        this.projectObjectFactory = projectObjectFactory;
        this.problemReporter = problemReporter;
        this.allServices = allServices;
    }

    private <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> ProjectFeatureDefinitionContext.ChildDefinitionAdditionResult<OwnDefinition, OwnBuildModel> instantiateDefinition(DynamicObjectAware parentDefinition, ProjectFeatureImplementation<OwnDefinition, OwnBuildModel> projectFeature, ProjectFeatureDefinitionContext parentDefinitionContext) {
        return parentDefinitionContext.getOrAddChildDefinition(projectFeature, () -> {
            if (parentDefinition instanceof Project) {
                checkSingleProjectTypeApplication(parentDefinitionContext, projectFeature);
            }

            getPluginManager().apply(projectFeature.getPluginClass());

            return instantiateBoundFeatureObjects(parentDefinition, projectFeature);
        });
    }

    @Override
    public <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> FeatureApplication<OwnDefinition, OwnBuildModel> createFeatureApplicationFor(DynamicObjectAware parentDefinition, ProjectFeatureImplementation<OwnDefinition, OwnBuildModel> projectFeature) {
        ProjectFeatureDefinitionContext parentDefinitionContext = ProjectFeatureSupportInternal.getContext(parentDefinition);

        ProjectFeatureDefinitionContext.ChildDefinitionAdditionResult<OwnDefinition, OwnBuildModel> result = instantiateDefinition(parentDefinition, projectFeature, parentDefinitionContext);

        if (result.isNew) {
            Plugin<Project> plugin = getPluginManager().getPluginContainer().getPlugin(projectFeature.getPluginClass());
            getModelDefaultsApplicator().applyDefaultsTo(parentDefinition, result.featureApplication.getDefinitionInstance(), new ClassLoaderContextFromScope(classLoaderScope), plugin, projectFeature);
        }

        return result.featureApplication;
    }

    private static <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> void checkSingleProjectTypeApplication(ProjectFeatureDefinitionContext context, ProjectFeatureImplementation<OwnDefinition, OwnBuildModel> projectFeature) {
        context.childFeatures().keySet().stream().findFirst().ifPresent(projectTypeAlreadyApplied -> {
            throw new IllegalStateException(
                "The project has already applied the '" +
                    projectTypeAlreadyApplied.getFeatureName() +
                    "' project type and is also attempting to apply the '" +
                    projectFeature.getFeatureName() +
                    "' project type.  Only one project type can be applied to a project."
            );
        });
    }

    private <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> FeatureApplication<OwnDefinition, OwnBuildModel> instantiateBoundFeatureObjects(Object parentDefinition, ProjectFeatureImplementation<OwnDefinition, OwnBuildModel> projectFeature) {
        // Context-specific services for this feature binding
        ServiceLookup featureServices = getContextSpecificServiceLookup(projectFeature);
        ObjectFactory featureObjectFactory = getObjectFactoryFactory().createObjectFactory(featureServices);

        // Instantiate the definition and build model objects with the feature-specific object factory
        OwnDefinition definition = featureObjectFactory.newInstance(projectFeature.getDefinitionImplementationType());
        OwnBuildModel buildModelInstance = ProjectFeatureSupportInternal.createBuildModelInstance(featureObjectFactory, projectFeature);
        ProjectFeatureSupportInternal.attachDefinitionContext(definition, buildModelInstance, this, getProjectFeatureDeclarations(), featureObjectFactory);

        // Construct an apply action context with the feature-specific object factory
        ProjectFeatureApplicationContextInternal applyActionContext =
            projectObjectFactory.newInstance(DefaultProjectFeatureApplicationContextInternal.class, featureObjectFactory);

        // bind any nested definitions to build model instances
        bindNestedDefinitions(projectFeature.getDefinitionPublicType(), Cast.uncheckedCast(definition), applyActionContext, projectFeature);

        return new DefaultFeatureApplication<>(
            projectFeature.getDefinitionImplementationType(),
            definition,
            buildModelInstance,
            parentDefinition,
            applyActionContext,
            projectFeature.getApplyActionFactory().create(featureObjectFactory),
            propertyWalker
        );
    }

    private <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> void bindNestedDefinitions(Class<?> publicType, DynamicObjectAware parent, ProjectFeatureApplicationContextInternal applyActionContext, ProjectFeatureImplementation<OwnDefinition, OwnBuildModel> projectFeature) {
        ProjectFeatureDefinitionContext rootDefinitionContext = ProjectFeatureSupportInternal.getContext(parent);
        propertyWalker.walkProperties(publicType, parent, (propertyMetadata, propertyValue) -> {
            if (Definition.class.isAssignableFrom(propertyMetadata.getDeclaredReturnType().getRawType())) {
                bindNestedDefinition(propertyValue, rootDefinitionContext, applyActionContext, projectFeature);
            }
            if (NamedDomainObjectContainer.class.isAssignableFrom(propertyMetadata.getDeclaredReturnType().getRawType())) {
                NamedDomainObjectContainer<?> ndoc = Cast.uncheckedCast(propertyValue);
                Class<?> elementType = ((AbstractNamedDomainObjectContainer<?>) ndoc).getType();
                if (Definition.class.isAssignableFrom(elementType)) {
                    ndoc.configureEach(element -> bindNestedDefinition(element, rootDefinitionContext, applyActionContext, projectFeature));
                }
            }
        });
    }

    private static <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> void bindNestedDefinition(Object propertyValue, ProjectFeatureDefinitionContext rootDefinitionContext, ProjectFeatureApplicationContextInternal applyActionContext, ProjectFeatureImplementation<OwnDefinition, OwnBuildModel> projectFeature) {
        Definition<?> nestedDefinition = Cast.uncheckedCast(propertyValue);
        applyActionContext.registerBuildModel(nestedDefinition, projectFeature.getNestedBuildModelTypes());
        rootDefinitionContext.addNestedDefinition(nestedDefinition);
    }

    private <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> ServiceLookup getContextSpecificServiceLookup(ProjectFeatureImplementation<OwnDefinition, OwnBuildModel> projectFeature) {
        TaskRegistrar taskRegistrar = new DefaultTaskRegistrar(getTaskContainer());
        ProjectFeatureLayout projectFeatureLayout = new DefaultProjectFeatureLayout(getProjectLayout());
        ConfigurationRegistrar configurationRegistrar = new DefaultConfigurationRegistrar(getConfigurationContainer());

        // Construct an object factory that provides the appropriate services during apply action execution
        return projectFeature.getApplyActionSafety() == ProjectFeatureBindingDeclaration.Safety.SAFE
            ? new SafeServicesForApplyAction(allServices, taskRegistrar, projectFeatureLayout, configurationRegistrar, projectFeature.getFeatureName(), problemReporter)
            : new UnsafeServicesForApplyAction(allServices, taskRegistrar, projectFeatureLayout, configurationRegistrar, projectFeature.getFeatureName(), problemReporter);
    }

    @Inject
    abstract protected TaskContainer getTaskContainer();

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @Inject
    abstract protected ConfigurationContainer getConfigurationContainer();

    @Inject
    abstract protected PluginManagerInternal getPluginManager();

    @Inject
    abstract protected ModelDefaultsApplicator getModelDefaultsApplicator();

    @Inject
    abstract protected ObjectFactoryFactory getObjectFactoryFactory();

    @Inject
    abstract protected ProjectFeatureDeclarations getProjectFeatureDeclarations();

    @Inject
    abstract protected TypeAnnotationMetadataStore getTypeAnnotationMetadataStore();

    /**
     * Walks the public properties of a given object, visiting each property and descending into any properties that are annotated with {@link Nested}.
     *
     * Ignores any properties that are null, although properties whose value is null will be visited (e.g. ignores getFoo() == null, but will visit
     * getFoo().getOrNull() == null)
     */
    private static class DefaultPropertyWalker implements PropertyWalker {
        private final TypeAnnotationMetadataStore typeAnnotationMetadataStore;

        private DefaultPropertyWalker(TypeAnnotationMetadataStore typeAnnotationMetadataStore) {
            this.typeAnnotationMetadataStore = typeAnnotationMetadataStore;
        }

        @Override
        public void walkProperties(Class<?> publicType, Object parent, PropertyWalker.Visitor visitor) {
            typeAnnotationMetadataStore.getTypeAnnotationMetadata(publicType).getPropertiesAnnotationMetadata().forEach(propertyMetadata -> {
                Object propertyValue = propertyMetadata.getPropertyValue(parent);
                if (propertyValue != null) {
                    visitor.visit(propertyMetadata, propertyValue);

                    if (propertyMetadata.isAnnotationPresent(Nested.class)) {
                        walkProperties(propertyMetadata.getDeclaredReturnType().getRawType(), Cast.uncheckedCast(propertyValue), visitor);
                    }
                }
            });
        }
    }

    private interface PropertyWalker {
        void walkProperties(Class<?> publicType, Object parent, Visitor visitor);
        interface Visitor {
            void visit(PropertyAnnotationMetadata propertyMetadata, Object propertyValue);
        }
    }

    private static class DefaultFeatureApplication<OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> implements FeatureApplication<OwnDefinition, OwnBuildModel> {
        private final Class<? extends OwnDefinition> definitionImplementationType;
        private final OwnDefinition definitionInstance;
        private final OwnBuildModel buildModelInstance;
        private final Object parentDefinition;
        private final ProjectFeatureApplicationContext context;
        private final ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, ?> applyAction;
        private final PropertyWalker propertyWalker;
        private boolean applied;

        public DefaultFeatureApplication(Class<? extends OwnDefinition> definitionImplementationType, OwnDefinition definitionInstance, OwnBuildModel buildModelInstance, Object parentDefinition, ProjectFeatureApplicationContext context, ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, ?> applyAction, PropertyWalker propertyWalker) {
            this.definitionImplementationType = definitionImplementationType;
            this.definitionInstance = definitionInstance;
            this.buildModelInstance = buildModelInstance;
            this.parentDefinition = parentDefinition;
            this.context = context;
            this.applyAction = applyAction;
            this.propertyWalker = propertyWalker;
        }

        @Override
        public OwnDefinition getDefinitionInstance() {
            return definitionInstance;
        }

        @Override
        public boolean isProjectType() {
            return parentDefinition instanceof Project;
        }

        @Override
        public void apply() {
            if (!applied) {
                finalizeProperties();
                applyAction.apply(context, definitionInstance, buildModelInstance, Cast.uncheckedCast(parentDefinition));
                applied = true;
            }
        }

        private void finalizeProperties() {
            propertyWalker.walkProperties(definitionImplementationType, definitionInstance, (propertyMetadata, propertyValue) -> {
                if (Property.class.isAssignableFrom(propertyMetadata.getDeclaredReturnType().getRawType())) {
                    ((Property<?>) propertyValue).finalizeValue();
                }
            });
        }
    }

    /**
     * The internal implementation of the context passed to project feature apply actions, exposing an object factory
     * appropriate for the configured safety of the apply action.
     */
    abstract static class DefaultProjectFeatureApplicationContextInternal implements org.gradle.features.internal.binding.ProjectFeatureApplicationContextInternal {
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
     * The set of services that are considered safe for use during safe apply actions.
     */
    private static final Set<Class<?>> SAFE_APPLY_ACTION_SERVICES = ImmutableSet.of(
        TaskRegistrar.class,
        ProjectFeatureLayout.class,
        ConfigurationRegistrar.class,
        ObjectFactory.class,
        ProviderFactory.class,
        DependencyFactory.class
    );

    /**
     * A base class for limited service lookups for use during feature apply actions.  Provides context-specific services
     * unique to the feature binding as well as a small set of services used by Gradle internals.
     */
    private abstract static class ServicesForApplyAction implements ServiceLookup {

        protected final ServiceLookup allServices;
        protected final TaskRegistrar taskRegistrar;
        protected final ProjectFeatureLayout projectFeatureLayout;
        protected final ConfigurationRegistrar configurationRegistrar;

        private ServicesForApplyAction(ServiceLookup allServices, TaskRegistrar taskRegistrar, ProjectFeatureLayout projectFeatureLayout, ConfigurationRegistrar configurationRegistrar) {
            this.allServices = allServices;
            this.taskRegistrar = taskRegistrar;
            this.projectFeatureLayout = projectFeatureLayout;
            this.configurationRegistrar = configurationRegistrar;
        }

        @Override
        public @Nullable Object find(Type serviceType) throws ServiceLookupException {
            if (serviceType instanceof Class) {
                Class<?> serviceClass = Cast.uncheckedNonnullCast(serviceType);
                // Context-specific services unique to this feature binding
                if (serviceClass.isAssignableFrom(TaskRegistrar.class)) {
                    return taskRegistrar;
                }
                if (serviceClass.isAssignableFrom(ProjectFeatureLayout.class)) {
                    return projectFeatureLayout;
                }
                if (serviceClass.isAssignableFrom(ConfigurationRegistrar.class)) {
                    return configurationRegistrar;
                }
                // Services used by Gradle internals
                if (serviceClass.isAssignableFrom(ManagedObjectRegistry.class)) {
                    return allServices.find(ManagedObjectRegistry.class);
                }
                if (serviceClass.isAssignableFrom(ProjectFeatureDeclarations.class)) {
                    return allServices.find(ProjectFeatureDeclarations.class);
                }
                if (serviceClass.isAssignableFrom(ProjectFeatureApplicator.class)) {
                    return allServices.find(ProjectFeatureApplicator.class);
                }
                if (serviceClass.isAssignableFrom(TaskDependencyFactory.class)) {
                    return allServices.find(TaskDependencyFactory.class);
                }
                if (serviceClass.isAssignableFrom(InstantiatorFactory.class)) {
                    return allServices.find(InstantiatorFactory.class);
                }
                // None of the above
                return null;
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

        protected abstract Object notFound(Type serviceType);
    }

    /**
     * A limited service lookup for use during feature apply actions, exposing both safe and unsafe services.
     */
    private static class UnsafeServicesForApplyAction extends ServicesForApplyAction {
        private final String featureName;
        private final InternalProblemReporter problemReporter; // Not used in this class

        public UnsafeServicesForApplyAction(ServiceLookup allServices, TaskRegistrar taskRegistrar, ProjectFeatureLayout projectFeatureLayout, ConfigurationRegistrar configurationRegistrar, String featureName, InternalProblemReporter problemReporter) {
            super(allServices, taskRegistrar, projectFeatureLayout, configurationRegistrar);
            this.featureName = featureName;
            this.problemReporter = problemReporter;
        }

        @Override
        public @Nullable Object find(Type serviceType) throws ServiceLookupException {
            Object found = super.find(serviceType);
            if (found != null) {
                return found;
            }

            // If not found in the safe/limited set, allow lookup from all services
            return allServices.find(serviceType);
        }

        @Override
        protected Object notFound(Type serviceType) {
            InternalProblem problem = problemReporter.internalCreate(builder -> builder
                .id("unsafe-apply-action-uses-unknown-service", "An unsafe apply action is attempting to use an unknown service", GradleCoreProblemGroup.configurationUsage())
                .contextualLabel("Project feature '" + featureName + "' has an apply action that attempts to inject an unknown service with type '" + serviceType.getTypeName() + "'.")
                .details("Services of type " + serviceType.getTypeName() + " are not available for injection into project feature apply actions.")
                .solution("Remove the '" + serviceType.getTypeName() + "' injection from the apply action.")
                .severity(Severity.ERROR)
            );
            problemReporter.report(problem);
            throw new UnknownServiceException(serviceType, TypeValidationProblemRenderer.renderMinimalInformationAbout(problem));
        }
    }

    /**
     * A limited service lookup for use during safe feature apply actions, exposing only a small set of safe services.
     */
    private static class SafeServicesForApplyAction extends ServicesForApplyAction {
        private final String featureName;
        private final InternalProblemReporter problemReporter;

        public SafeServicesForApplyAction(ServiceLookup allServices, TaskRegistrar taskRegistrar, ProjectFeatureLayout projectFeatureLayout, ConfigurationRegistrar configurationRegistrar, String featureName, InternalProblemReporter problemReporter) {
            super(allServices, taskRegistrar, projectFeatureLayout, configurationRegistrar);
            this.featureName = featureName;
            this.problemReporter = problemReporter;
        }

        @Override
        public @Nullable Object find(Type serviceType) throws ServiceLookupException {
            Object found = super.find(serviceType);
            if (found != null) {
                return found;
            }

            // Only allow access to a small set of safe services from the parent services
            Class<?> serviceClass = Cast.uncheckedNonnullCast(serviceType);
            for (Class<?> safeService : SAFE_APPLY_ACTION_SERVICES) {
                if (serviceClass.isAssignableFrom(safeService)) {
                    return allServices.find(serviceType);
                }
            }

            return null;
        }

        private static String getSafeServicesListExplanation() {
            TreeFormatter formatter = new TreeFormatter(true)
                .node("Only the following services are available in safe apply actions");
            formatter.startChildren();
            for (Class<?> safeService : SAFE_APPLY_ACTION_SERVICES) {
                formatter.node("").appendType(safeService);
            }
            formatter.endChildren();
            return formatter.toString();
        }

        @Override
        protected Object notFound(Type serviceType) {
            InternalProblem problem = problemReporter.internalCreate(builder -> builder
                .id("safe-apply-action-uses-unsafe-service", "A safe apply action is attempting to use an unsafe service", GradleCoreProblemGroup.configurationUsage())
                .contextualLabel("Project feature '" + featureName + "' has a safe apply action that attempts to inject an unsafe service with type '" + serviceType.getTypeName() + "'.")
                .details(getSafeServicesListExplanation())
                .solution("Mark the apply action as unsafe.")
                .solution("Remove the '" + serviceType.getTypeName() + "' injection from the apply action.")
                .severity(Severity.ERROR)
            );
            problemReporter.report(problem);
            throw new UnknownServiceException(serviceType, TypeValidationProblemRenderer.renderMinimalInformationAbout(problem));
        }
    }
}
