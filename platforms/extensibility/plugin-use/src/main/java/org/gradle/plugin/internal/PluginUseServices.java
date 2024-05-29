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

package org.gradle.plugin.internal;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.dsl.dependencies.UnknownProjectFinder;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext;
import org.gradle.api.internal.initialization.ScriptClassPathResolver;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.api.internal.plugins.software.SoftwareType;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.internal.tasks.properties.InspectionSchemeFactory;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.Factory;
import org.gradle.internal.build.BuildIncluder;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.properties.annotations.MissingPropertyAnnotationHandler;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.plugin.management.PluginManagementSpec;
import org.gradle.plugin.management.internal.DefaultPluginManagementSpec;
import org.gradle.plugin.management.internal.DefaultPluginResolutionStrategy;
import org.gradle.plugin.management.internal.PluginResolutionStrategyInternal;
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler;
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginRegistry;
import org.gradle.plugin.management.internal.autoapply.CompositeAutoAppliedPluginRegistry;
import org.gradle.plugin.management.internal.autoapply.DefaultAutoAppliedPluginHandler;
import org.gradle.plugin.management.internal.autoapply.InjectedAutoAppliedPluginRegistry;
import org.gradle.plugin.software.internal.DefaultSoftwareTypeRegistry;
import org.gradle.plugin.software.internal.PluginScheme;
import org.gradle.plugin.software.internal.SoftwareTypeAnnotationHandler;
import org.gradle.plugin.software.internal.SoftwareTypeRegistry;
import org.gradle.plugin.use.internal.DefaultPluginRequestApplicator;
import org.gradle.plugin.use.internal.InjectedPluginClasspath;
import org.gradle.plugin.use.internal.PluginDependencyResolutionServices;
import org.gradle.plugin.use.internal.PluginRepositoryHandlerProvider;
import org.gradle.plugin.use.internal.PluginResolverFactory;
import org.gradle.plugin.use.resolve.service.internal.ClientInjectedClasspathPluginResolver;
import org.gradle.plugin.use.resolve.service.internal.DefaultInjectedClasspathPluginResolver;
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathInstrumentationStrategy;
import org.gradle.plugin.use.tracker.internal.PluginVersionTracker;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

public class PluginUseServices extends AbstractGradleModuleServices {

    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new GlobalScopeServices());
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeServices());
    }

    @Override
    public void registerSettingsServices(ServiceRegistration registration) {
        registration.addProvider(new SettingsScopeServices());
    }

    @NonNullApi
    private static class GlobalScopeServices implements ServiceRegistrationProvider {
        @Provides
        SoftwareTypeAnnotationHandler createSoftwareTypeAnnotationHandler() {
            return new SoftwareTypeAnnotationHandler();
        }
    }

    private static class SettingsScopeServices implements ServiceRegistrationProvider {
        @Provides
        protected PluginManagementSpec createPluginManagementSpec(
            Instantiator instantiator,
            PluginRepositoryHandlerProvider pluginRepositoryHandlerProvider,
            PluginResolutionStrategyInternal internalPluginResolutionStrategy,
            FileResolver fileResolver,
            BuildIncluder buildIncluder
        ) {
            return instantiator.newInstance(DefaultPluginManagementSpec.class, pluginRepositoryHandlerProvider, internalPluginResolutionStrategy, fileResolver, buildIncluder);
        }
    }

    private static class BuildScopeServices implements ServiceRegistrationProvider {
        void configure(ServiceRegistration registration) {
            registration.add(PluginResolverFactory.class);
            registration.add(DefaultPluginRequestApplicator.class);
            registration.add(PluginVersionTracker.class);
        }

        @Provides
        AutoAppliedPluginRegistry createInjectedAutoAppliedPluginRegistry(BuildDefinition buildDefinition) {
            return new InjectedAutoAppliedPluginRegistry(buildDefinition);
        }

        @Provides
        AutoAppliedPluginHandler createAutoAppliedPluginHandler(List<AutoAppliedPluginRegistry> registries) {
            return new DefaultAutoAppliedPluginHandler(new CompositeAutoAppliedPluginRegistry(registries));
        }

        @Provides
        SoftwareTypeRegistry createSoftwareTypeRegistry(PluginScheme pluginScheme) {
            return new DefaultSoftwareTypeRegistry(pluginScheme.getInspectionScheme());
        }

        @Provides
        PluginScheme createPluginScheme(InstantiatorFactory instantiatorFactory, InspectionSchemeFactory inspectionSchemeFactory) {
            InstantiationScheme instantiationScheme = instantiatorFactory.decorateScheme();
            ImmutableSet.Builder<Class<? extends Annotation>> allPropertyTypes = ImmutableSet.builder();
            allPropertyTypes.addAll(ImmutableSet.of(
                SoftwareType.class
            ));
            InspectionScheme inspectionScheme = inspectionSchemeFactory.inspectionScheme(
                allPropertyTypes.build(),
                Collections.emptySet(),
                instantiationScheme,
                MissingPropertyAnnotationHandler.DO_NOTHING
            );
            return new PluginScheme(instantiationScheme, inspectionScheme);
        }

        @Provides
        ClientInjectedClasspathPluginResolver createInjectedClassPathPluginResolver(
            FileResolver fileResolver,
            DependencyManagementServices dependencyManagementServices,
            DependencyMetaDataProvider dependencyMetaDataProvider,
            ClassLoaderScopeRegistry classLoaderScopeRegistry,
            PluginInspector pluginInspector,
            InjectedPluginClasspath injectedPluginClasspath,
            ScriptClassPathResolver scriptClassPathResolver,
            FileCollectionFactory fileCollectionFactory,
            InjectedClasspathInstrumentationStrategy instrumentationStrategy
        ) {
            if (injectedPluginClasspath.getClasspath().isEmpty()) {
                return ClientInjectedClasspathPluginResolver.EMPTY;
            }
            Factory<DependencyResolutionServices> dependencyResolutionServicesFactory = makeDependencyResolutionServicesFactory(
                fileResolver,
                fileCollectionFactory,
                dependencyManagementServices,
                dependencyMetaDataProvider
            );
            return new DefaultInjectedClasspathPluginResolver(
                classLoaderScopeRegistry.getCoreAndPluginsScope(),
                scriptClassPathResolver,
                fileCollectionFactory,
                pluginInspector,
                injectedPluginClasspath.getClasspath(),
                instrumentationStrategy,
                dependencyResolutionServicesFactory
            );
        }

        @Provides
        PluginResolutionStrategyInternal createPluginResolutionStrategy(Instantiator instantiator, ListenerManager listenerManager) {
            return instantiator.newInstance(DefaultPluginResolutionStrategy.class, listenerManager);
        }

        @Provides
        PluginDependencyResolutionServices createPluginDependencyResolutionServices(
            FileResolver fileResolver, FileCollectionFactory fileCollectionFactory,
            DependencyManagementServices dependencyManagementServices, DependencyMetaDataProvider dependencyMetaDataProvider
        ) {
            return new PluginDependencyResolutionServices(
                makeDependencyResolutionServicesFactory(fileResolver, fileCollectionFactory, dependencyManagementServices, dependencyMetaDataProvider));
        }

        private static Factory<DependencyResolutionServices> makeDependencyResolutionServicesFactory(
            final FileResolver fileResolver,
            final FileCollectionFactory fileCollectionFactory,
            final DependencyManagementServices dependencyManagementServices,
            final DependencyMetaDataProvider dependencyMetaDataProvider
        ) {
            return new Factory<DependencyResolutionServices>() {
                @Override
                public DependencyResolutionServices create() {
                    return dependencyManagementServices.create(fileResolver, fileCollectionFactory, dependencyMetaDataProvider, makeUnknownProjectFinder(), RootScriptDomainObjectContext.PLUGINS);
                }
            };
        }

        private static ProjectFinder makeUnknownProjectFinder() {
            return new UnknownProjectFinder("Cannot use project dependencies in a plugin resolution definition.");
        }
    }
}
