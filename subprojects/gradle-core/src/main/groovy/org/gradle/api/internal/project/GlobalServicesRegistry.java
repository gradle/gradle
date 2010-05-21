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
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.cache.AutoCloseCacheFactory;
import org.gradle.cache.CacheFactory;
import org.gradle.cache.DefaultCacheFactory;
import org.gradle.initialization.ClassLoaderFactory;
import org.gradle.initialization.CommandLine2StartParameterConverter;
import org.gradle.initialization.DefaultClassLoaderFactory;
import org.gradle.initialization.DefaultCommandLine2StartParameterConverter;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.DefaultProgressLoggerFactory;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.ProgressLoggerFactory;

/**
 * Contains the services shared by all builds in a given process.
 */
public class GlobalServicesRegistry extends DefaultServiceRegistry {
    public GlobalServicesRegistry() {
        super(new LoggingServiceRegistry());
    }

    protected CommandLine2StartParameterConverter createCommandLine2StartParameterConverter() {
        return new DefaultCommandLine2StartParameterConverter();
    }

    protected ClassPathRegistry createClassPathRegistry() {
        return new DefaultClassPathRegistry();
    }

    protected CacheFactory createCacheFactory() {
        return new AutoCloseCacheFactory(new DefaultCacheFactory());
    }

    protected ClassLoaderFactory createClassLoaderFactory() {
        return new DefaultClassLoaderFactory(get(ClassPathRegistry.class));
    }

    protected ListenerManager createListenerManager() {
        return new DefaultListenerManager();
    }

    protected ProgressLoggerFactory createProgressLoggerFactory() {
        return new DefaultProgressLoggerFactory(get(ListenerManager.class));
    }
}
