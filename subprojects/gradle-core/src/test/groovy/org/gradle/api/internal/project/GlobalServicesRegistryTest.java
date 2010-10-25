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

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.GradleDistributionLocator;
import org.gradle.cache.AutoCloseCacheFactory;
import org.gradle.cache.CacheFactory;
import org.gradle.initialization.ClassLoaderFactory;
import org.gradle.initialization.CommandLineConverter;
import org.gradle.initialization.DefaultClassLoaderFactory;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.*;
import org.gradle.logging.internal.DefaultLoggingManagerFactory;
import org.gradle.logging.internal.DefaultProgressLoggerFactory;
import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class GlobalServicesRegistryTest {
    private final GlobalServicesRegistry registry = new GlobalServicesRegistry();

    @Test
    public void providesCommandLineArgsConverter() {
        assertThat(registry.get(CommandLineConverter.class), instanceOf(
                DefaultCommandLineConverter.class));
    }

    @Test
    public void providesACacheFactory() {
        assertThat(registry.get(CacheFactory.class), instanceOf(AutoCloseCacheFactory.class));
    }

    @Test
    public void providesAClassPathRegistry() {
        assertThat(registry.get(ClassPathRegistry.class), instanceOf(DefaultClassPathRegistry.class));
    }

    @Test
    public void providesAClassLoaderFactory() {
        assertThat(registry.get(ClassLoaderFactory.class), instanceOf(DefaultClassLoaderFactory.class));
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
        assertThat(registry.get(GradleDistributionLocator.class), instanceOf(DefaultClassPathProvider.class));
    }
    
    @Test
    public void providesAnIsolatedAntBuilder() {
        assertThat(registry.get(IsolatedAntBuilder.class), instanceOf(DefaultIsolatedAntBuilder.class));
    }
}
