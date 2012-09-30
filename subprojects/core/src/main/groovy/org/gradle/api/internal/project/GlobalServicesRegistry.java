/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.StartParameter;
import org.gradle.api.internal.*;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.classpath.DefaultPluginModuleRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.classpath.PluginModuleRegistry;
import org.gradle.cache.internal.*;
import org.gradle.cli.CommandLineConverter;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.DefaultClassLoaderRegistry;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.internal.Factory;
import org.gradle.internal.nativeplatform.ProcessEnvironment;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.internal.MessagingServices;
import org.gradle.util.ClassLoaderFactory;
import org.gradle.util.DefaultClassLoaderFactory;

/**
 * Contains the services shared by all builds in a given process.
 */
public class GlobalServicesRegistry extends DefaultServiceRegistry {
    public GlobalServicesRegistry() {
        this(LoggingServiceRegistry.newCommandLineProcessLogging());
    }

    public GlobalServicesRegistry(ServiceRegistry loggingServices) {
        super(loggingServices);
        add(NativeServices.getInstance());
    }

    protected CommandLineConverter<StartParameter> createCommandLine2StartParameterConverter() {
        return new DefaultCommandLineConverter();
    }

    protected ClassPathRegistry createClassPathRegistry() {
        return new DefaultClassPathRegistry(new DefaultClassPathProvider(get(ModuleRegistry.class)), new DynamicModulesClassPathProvider(get(ModuleRegistry.class), get(PluginModuleRegistry.class)));
    }

    protected DefaultModuleRegistry createModuleRegistry() {
        return new DefaultModuleRegistry();
    }

    protected DocumentationRegistry createDocumentationRegistry() {
        return new DocumentationRegistry(get(GradleDistributionLocator.class));
    }

    protected PluginModuleRegistry createPluginModuleRegistry() {
        return new DefaultPluginModuleRegistry(get(ModuleRegistry.class));
    }

    protected Factory<CacheFactory> createCacheFactory() {
        return new DefaultCacheFactory(get(FileLockManager.class));
    }

    protected ClassLoaderRegistry createClassLoaderRegistry() {
        return new DefaultClassLoaderRegistry(get(ClassPathRegistry.class), get(ClassLoaderFactory.class));
    }

    protected ListenerManager createListenerManager() {
        return new DefaultListenerManager();
    }
   
    protected ClassLoaderFactory createClassLoaderFactory() {
        return new DefaultClassLoaderFactory();
    }

    protected MessagingServices createMessagingServices() {
        return new MessagingServices(get(ClassLoaderRegistry.class).getPluginsClassLoader());
    }

    protected MessagingServer createMessagingServer() {
        return get(MessagingServices.class).get(MessagingServer.class);
    }

    protected ClassGenerator createClassGenerator() {
        return new AsmBackedClassGenerator();
    }

    protected Instantiator createInstantiator() {
        return new ClassGeneratorBackedInstantiator(get(ClassGenerator.class), new DirectInstantiator());
    }

    protected FileLockManager createFileLockManager() {
        return new DefaultFileLockManager(new DefaultProcessMetaDataProvider(get(ProcessEnvironment.class)));
    }
}
