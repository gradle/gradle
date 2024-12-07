/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.service.scopes;

import org.gradle.api.file.BuildLayout;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.api.internal.cache.DefaultCacheConfigurations;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.DefaultBuildLayout;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.DefaultPluginManager;
import org.gradle.api.internal.plugins.ImperativeOnlyPluginTarget;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.plugins.PluginTarget;
import org.gradle.api.internal.plugins.PluginTargetType;
import org.gradle.api.internal.plugins.SoftwareTypeRegistrationPluginTarget;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.cache.internal.LegacyCacheCleanupEnablement;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.configuration.ConfigurationTargetIdentifiers;
import org.gradle.initialization.DefaultProjectDescriptorRegistry;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.CloseableServiceRegistry;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.plugin.software.internal.PluginScheme;
import org.gradle.plugin.software.internal.SoftwareTypeRegistry;

import java.util.List;

public class SettingsScopeServices implements ServiceRegistrationProvider {

    public static CloseableServiceRegistry create(ServiceRegistry parent, SettingsInternal settings) {
        return ServiceRegistryBuilder.builder()
            .scope(Scope.Settings.class)
            .displayName("settings services")
            .parent(parent)
            .provider(new SettingsScopeServices(settings))
            .build();
    }

    private final SettingsInternal settings;

    private SettingsScopeServices(SettingsInternal settings) {
        this.settings = settings;
    }

    @Provides
    protected void configure(ServiceRegistration registration, List<GradleModuleServices> gradleModuleServiceProviders) {
        for (GradleModuleServices services : gradleModuleServiceProviders) {
            services.registerSettingsServices(registration);
        }
        registration.add(DefaultProjectDescriptorRegistry.class);
    }

    @Provides
    protected BuildLayout createBuildLayout(FileFactory fileFactory) {
        return new DefaultBuildLayout(settings, fileFactory);
    }

    @Provides
    protected FileResolver createFileResolver(FileLookup fileLookup) {
        return fileLookup.getFileResolver(settings.getSettingsDir());
    }

    @Provides
    protected PluginRegistry createPluginRegistry(PluginRegistry parentRegistry) {
        return parentRegistry.createChild(settings.getClassLoaderScope());
    }

    @Provides
    protected PluginManagerInternal createPluginManager(
        Instantiator instantiator,
        ServiceRegistry settingsScopeServiceRegistry,
        PluginRegistry pluginRegistry,
        InstantiatorFactory instantiatorFactory,
        BuildOperationRunner buildOperationRunner,
        UserCodeApplicationContext userCodeApplicationContext,
        CollectionCallbackActionDecorator decorator,
        DomainObjectCollectionFactory domainObjectCollectionFactory,
        PluginScheme pluginScheme,
        SoftwareTypeRegistry softwareTypeRegistry,
        InternalProblems problems
    ) {
        PluginTarget target = new SoftwareTypeRegistrationPluginTarget(
            new ImperativeOnlyPluginTarget<>(PluginTargetType.SETTINGS, settings, problems),
            softwareTypeRegistry,
            pluginScheme.getInspectionScheme(),
            problems
        );
        return instantiator.newInstance(DefaultPluginManager.class, pluginRegistry, instantiatorFactory.inject(settingsScopeServiceRegistry), target, buildOperationRunner, userCodeApplicationContext, decorator, domainObjectCollectionFactory);
    }

    @Provides
    protected ConfigurationTargetIdentifier createConfigurationTargetIdentifier() {
        return ConfigurationTargetIdentifiers.of(settings);
    }

    @Provides
    protected GradleInternal createGradleInternal() {
        return settings.getGradle();
    }

    @Provides
    protected CacheConfigurationsInternal createCacheConfigurations(ObjectFactory objectFactory, CacheConfigurationsInternal persistentCacheConfigurations, GradleInternal gradleInternal, LegacyCacheCleanupEnablement legacyCacheCleanupEnablement) {
        CacheConfigurationsInternal cacheConfigurations = objectFactory.newInstance(DefaultCacheConfigurations.class, legacyCacheCleanupEnablement);
        if (gradleInternal.isRootBuild()) {
            cacheConfigurations.synchronize(persistentCacheConfigurations);
            persistentCacheConfigurations.setCleanupHasBeenConfigured(false);
        }
        return cacheConfigurations;
    }
}
