/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.StartParameter;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.FileCollectionObservationListener;
import org.gradle.api.internal.initialization.BuildLogicBuildQueue;
import org.gradle.api.internal.initialization.DefaultBuildLogicBuildQueue;
import org.gradle.api.internal.model.DefaultObjectFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.project.DefaultProjectStateRegistry;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.project.taskfactory.TaskIdentityFactory;
import org.gradle.api.internal.properties.DefaultGradlePropertiesController;
import org.gradle.api.internal.properties.GradlePropertiesController;
import org.gradle.api.internal.properties.GradlePropertiesListener;
import org.gradle.api.internal.provider.ConfigurationTimeBarrier;
import org.gradle.api.internal.provider.DefaultConfigurationTimeBarrier;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.tasks.util.internal.PatternSetFactory;
import org.gradle.configuration.project.BuiltInCommand;
import org.gradle.execution.DefaultTaskSelector;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.execution.TaskNameResolver;
import org.gradle.execution.TaskPathProjectEvaluator;
import org.gradle.execution.TaskSelector;
import org.gradle.execution.selection.BuildTaskSelector;
import org.gradle.execution.selection.DefaultBuildTaskSelector;
import org.gradle.initialization.BuildOptionBuildOperationProgressEventsEmitter;
import org.gradle.initialization.Environment;
import org.gradle.initialization.exception.DefaultExceptionAnalyser;
import org.gradle.initialization.exception.ExceptionCollector;
import org.gradle.initialization.exception.MultipleBuildFailuresExceptionAnalyser;
import org.gradle.initialization.exception.StackTraceSanitizingExceptionAnalyser;
import org.gradle.initialization.properties.DefaultGradlePropertiesLoader;
import org.gradle.initialization.properties.DefaultSystemPropertiesInstaller;
import org.gradle.initialization.properties.SystemPropertiesInstaller;
import org.gradle.internal.build.BuildLifecycleControllerFactory;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.DefaultBuildLifecycleControllerFactory;
import org.gradle.internal.buildoption.DefaultFeatureFlags;
import org.gradle.internal.buildoption.DefaultInternalOptions;
import org.gradle.internal.buildoption.FeatureFlags;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.event.ScopedListenerManager;
import org.gradle.internal.exception.ExceptionAnalyser;
import org.gradle.internal.id.ConfigurationCacheableIdFactory;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.instantiation.managed.ManagedObjectRegistry;
import org.gradle.internal.instrumentation.reporting.DefaultMethodInterceptionReportCollector;
import org.gradle.internal.instrumentation.reporting.ErrorReportingMethodInterceptionReportCollector;
import org.gradle.internal.instrumentation.reporting.MethodInterceptionReportCollector;
import org.gradle.internal.instrumentation.reporting.PropertyUpgradeReportConfig;
import org.gradle.internal.problems.DefaultProblemDiagnosticsFactory;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.service.PrivateService;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.GradleModuleServices;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory;
import org.gradle.problems.buildtree.ProblemReporter;

import java.util.List;

/**
 * Contains the singleton services for a single build tree which consists of one or more builds.
 */
public class BuildTreeScopeServices implements ServiceRegistrationProvider {
    private final BuildInvocationScopeId buildInvocationScopeId;
    private final BuildTreeState buildTree;
    private final BuildTreeModelControllerServices.Supplier modelServices;

    public BuildTreeScopeServices(BuildInvocationScopeId buildInvocationScopeId, BuildTreeState buildTree, BuildTreeModelControllerServices.Supplier modelServices) {
        this.buildInvocationScopeId = buildInvocationScopeId;
        this.buildTree = buildTree;
        this.modelServices = modelServices;
    }

    protected void configure(ServiceRegistration registration, List<GradleModuleServices> servicesProviders) {
        for (GradleModuleServices services : servicesProviders) {
            services.registerBuildTreeServices(registration);
        }
        registration.add(BuildInvocationScopeId.class, buildInvocationScopeId);
        registration.add(BuildTreeState.class, buildTree);
        registration.add(GradleEnterprisePluginManager.class);
        registration.add(BuildLifecycleControllerFactory.class, DefaultBuildLifecycleControllerFactory.class);
        registration.add(BuildOptionBuildOperationProgressEventsEmitter.class);
        registration.add(BuildInclusionCoordinator.class);
        registration.add(ProjectStateRegistry.class, DefaultProjectStateRegistry.class);
        registration.add(ConfigurationTimeBarrier.class, DefaultConfigurationTimeBarrier.class);
        registration.add(ProblemReporter.class, DeprecationsReporter.class);
        registration.add(ProjectConfigurer.class, TaskPathProjectEvaluator.class);
        registration.add(FeatureFlags.class, DefaultFeatureFlags.class);
        registration.add(ProblemDiagnosticsFactory.class, DefaultProblemDiagnosticsFactory.class);
        registration.add(ExceptionCollector.class, DefaultExceptionAnalyser.class);
        registration.add(ConfigurationCacheableIdFactory.class);
        registration.add(TaskIdentityFactory.class);
        registration.add(BuildLogicBuildQueue.class, DefaultBuildLogicBuildQueue.class);
        modelServices.applyServicesTo(registration);
    }

    @Provides
    ManagedObjectRegistry decorateManagedObjectRegistry(ManagedObjectRegistry parent) {
        return parent.createChild();
    }

    @Provides
    ObjectFactory createObjectFactory(
        InstantiatorFactory instantiatorFactory, DirectoryFileTreeFactory directoryFileTreeFactory, PatternSetFactory patternSetFactory,
        PropertyFactory propertyFactory, FilePropertyFactory filePropertyFactory, TaskDependencyFactory taskDependencyFactory, FileCollectionFactory fileCollectionFactory,
        DomainObjectCollectionFactory domainObjectCollectionFactory, NamedObjectInstantiator instantiator
    ) {
        return new DefaultObjectFactory(
            instantiatorFactory.decorate(buildTree.getServices()),
            instantiator,
            directoryFileTreeFactory,
            patternSetFactory,
            propertyFactory,
            filePropertyFactory,
            taskDependencyFactory,
            fileCollectionFactory,
            domainObjectCollectionFactory);
    }

    @Provides
    protected InternalOptions createInternalOptions(StartParameter startParameter) {
        return new DefaultInternalOptions(startParameter.getSystemPropertiesArgs());
    }

    @Provides
    protected TaskSelector createTaskSelector(ObjectFactory objectFactory) {
        return objectFactory.newInstance(DefaultTaskSelector.class, new TaskNameResolver());
    }

    @Provides
    protected BuildTaskSelector createBuildTaskSelector(BuildStateRegistry buildRegistry, TaskSelector taskSelector, List<BuiltInCommand> commands, InternalProblems problemsService) {
        return new DefaultBuildTaskSelector(buildRegistry, taskSelector, commands, problemsService);
    }

    @Provides
    protected ScopedListenerManager createListenerManager(ScopedListenerManager parent) {
        return parent.createChild(Scope.BuildTree.class);
    }

    @Provides
    protected ExceptionAnalyser createExceptionAnalyser(LoggingConfiguration loggingConfiguration, ExceptionCollector exceptionCollector) {
        ExceptionAnalyser exceptionAnalyser = new MultipleBuildFailuresExceptionAnalyser(exceptionCollector);
        if (loggingConfiguration.getShowStacktrace() != ShowStacktrace.ALWAYS_FULL) {
            exceptionAnalyser = new StackTraceSanitizingExceptionAnalyser(exceptionAnalyser);
        }
        return exceptionAnalyser;
    }

    @Provides
    protected FileCollectionFactory createFileCollectionFactory(FileCollectionFactory parent, ListenerManager listenerManager) {
        return parent.forChildScope(listenerManager.getBroadcaster(FileCollectionObservationListener.class));
    }

    @Provides
    @PrivateService
    protected MethodInterceptionReportCollector createMethodInterceptionReportCollector(StartParameterInternal startParameter) {
        return startParameter.isPropertyUpgradeReportEnabled()
            ? new DefaultMethodInterceptionReportCollector()
            : new ErrorReportingMethodInterceptionReportCollector();
    }

    @Provides
    protected PropertyUpgradeReportConfig createPropertyUpgradeReportConfig(MethodInterceptionReportCollector reportCollector, StartParameterInternal startParameter) {
        return new PropertyUpgradeReportConfig(
            reportCollector,
            startParameter.isPropertyUpgradeReportEnabled()
        );
    }

    @Provides
    protected SystemPropertiesInstaller createSystemPropertiesInstaller(StartParameterInternal startParameter) {
        return new DefaultSystemPropertiesInstaller(startParameter);
    }

    @Provides
    protected GradlePropertiesController createGradlePropertiesController(
        StartParameterInternal startParameter,
        Environment environment,
        SystemPropertiesInstaller systemPropertiesInstaller,
        ListenerManager listenerManager
    ) {
        return new DefaultGradlePropertiesController(
            new DefaultGradlePropertiesLoader(startParameter, environment),
            systemPropertiesInstaller,
            listenerManager.getBroadcaster(GradlePropertiesListener.class)
        );
    }
}
