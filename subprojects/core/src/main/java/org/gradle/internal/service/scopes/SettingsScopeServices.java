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
import org.gradle.api.internal.plugins.SoftwareTypeRegistrationPluginTarget;
import org.gradle.api.model.ObjectFactory;
import org.gradle.cache.internal.LegacyCacheCleanupEnablement;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.initialization.DefaultProjectDescriptorRegistry;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugin.software.internal.PluginScheme;
import org.gradle.plugin.software.internal.SoftwareTypeRegistry;

public class SettingsScopeServices extends DefaultServiceRegistry {
    private final SettingsInternal settings;

    public SettingsScopeServices(final ServiceRegistry parent, final SettingsInternal settings) {
        super(parent);
        this.settings = settings;
        register(registration -> {
            for (GradleModuleServices services : parent.getAll(GradleModuleServices.class)) {
                services.registerSettingsServices(registration);
            }
            registration.add(DefaultProjectDescriptorRegistry.class);
        });
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
        PluginRegistry pluginRegistry,
        InstantiatorFactory instantiatorFactory,
        BuildOperationRunner buildOperationRunner,
        UserCodeApplicationContext userCodeApplicationContext,
        CollectionCallbackActionDecorator decorator,
        DomainObjectCollectionFactory domainObjectCollectionFactory,
        PluginScheme pluginScheme,
        SoftwareTypeRegistry softwareTypeRegistry
        ) {
        PluginTarget target = new SoftwareTypeRegistrationPluginTarget(
            new ImperativeOnlyPluginTarget<>(settings),
            softwareTypeRegistry,
            pluginScheme.getInspectionScheme()
        );
        return instantiator.newInstance(DefaultPluginManager.class, pluginRegistry, instantiatorFactory.inject(this), target, buildOperationRunner, userCodeApplicationContext, decorator, domainObjectCollectionFactory);
    }

    @Provides
    protected ConfigurationTargetIdentifier createConfigurationTargetIdentifier() {
        return ConfigurationTargetIdentifier.of(settings);
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
