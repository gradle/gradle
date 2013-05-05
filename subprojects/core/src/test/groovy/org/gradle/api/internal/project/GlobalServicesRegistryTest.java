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

import org.gradle.api.internal.*;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.classpath.DefaultPluginModuleRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.classpath.PluginModuleRegistry;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.DefaultCacheFactory;
import org.gradle.cache.internal.DefaultFileLockManager;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.cli.CommandLineConverter;
import org.gradle.initialization.DefaultClassLoaderRegistry;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.internal.nativeplatform.*;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.logging.internal.DefaultLoggingManagerFactory;
import org.gradle.logging.internal.DefaultProgressLoggerFactory;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.util.ClassLoaderFactory;
import org.gradle.util.DefaultClassLoaderFactory;
import org.junit.Test;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

public class GlobalServicesRegistryTest {
    private final GlobalServicesRegistry registry = new GlobalServicesRegistry();

    @Test
    public void providesCommandLineArgsConverter() {
        assertThat(registry.get(CommandLineConverter.class), instanceOf(
                DefaultCommandLineConverter.class));
    }

    @Test
    public void providesACacheFactoryFactory() {
        assertThat(registry.getFactory(CacheFactory.class), instanceOf(DefaultCacheFactory.class));
    }

    @Test
    public void providesAModuleRegistry() {
        assertThat(registry.get(ModuleRegistry.class), instanceOf(DefaultModuleRegistry.class));
    }

    @Test
    public void providesAPluginModuleRegistry() {
        assertThat(registry.get(PluginModuleRegistry.class), instanceOf(DefaultPluginModuleRegistry.class));
    }

    @Test
    public void providesAClassPathRegistry() {
        assertThat(registry.get(ClassPathRegistry.class), instanceOf(DefaultClassPathRegistry.class));
    }

    @Test
    public void providesAClassLoaderRegistry() {
        assertThat(registry.get(ClassLoaderRegistry.class), instanceOf(DefaultClassLoaderRegistry.class));
    }

    @Test
    public void providesALoggingManagerFactory() {
        assertThat(registry.getFactory(LoggingManagerInternal.class), instanceOf(DefaultLoggingManagerFactory.class));
    }
    
    @Test
    public void providesAListenerManager() {
        assertThat(registry.get(ListenerManager.class), instanceOf(DefaultListenerManager.class));
    }
    
    @Test
    public void providesAProgressLoggerFactory() {
        assertThat(registry.get(ProgressLoggerFactory.class), instanceOf(DefaultProgressLoggerFactory.class));
    }
    
    @Test
    public void providesAGradleDistributionLocator() {
        assertThat(registry.get(GradleDistributionLocator.class), instanceOf(DefaultModuleRegistry.class));
    }
    
    @Test
    public void providesAClassLoaderFactory() {
        assertThat(registry.get(ClassLoaderFactory.class), instanceOf(DefaultClassLoaderFactory.class));
    }

    @Test
    public void providesAMessagingServer() {
        assertThat(registry.get(MessagingServer.class), instanceOf(MessagingServer.class));
    }

    @Test
    public void providesAClassGenerator() {
        assertThat(registry.get(ClassGenerator.class), instanceOf(AsmBackedClassGenerator.class));
    }
    
    @Test
    public void providesAnInstantiator() {
        assertThat(registry.get(org.gradle.internal.reflect.Instantiator.class), instanceOf(ClassGeneratorBackedInstantiator.class));
    }

    @Test
    public void providesAFileLockManager() {
        assertThat(registry.get(FileLockManager.class), instanceOf(DefaultFileLockManager.class));
    }

    @Test
    public void providesAProcessEnvironment() {
        assertThat(registry.get(ProcessEnvironment.class), notNullValue());
    }

    @Test
    public void providesAFileSystem() {
        assertThat(registry.get(FileSystem.class), notNullValue());
    }

    @Test
    public void providesADocumentationRegistry() throws Exception {
        assertThat(registry.get(DocumentationRegistry.class), instanceOf(DocumentationRegistry.class));
    }
}
