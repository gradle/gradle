/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.testfixtures.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.project.DefaultIsolatedAntBuilder;
import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.cache.AutoCloseCacheFactory;
import org.gradle.cache.CacheFactory;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.DefaultClassLoaderRegistry;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.logging.internal.DefaultProgressLoggerFactory;
import org.gradle.logging.internal.DefaultStyledTextOutputFactory;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.logging.internal.ProgressListener;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectEvent;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.util.ClassLoaderFactory;
import org.gradle.util.DefaultClassLoaderFactory;
import org.gradle.util.TrueTimeProvider;

public class GlobalTestServices extends DefaultServiceRegistry {
    protected ListenerManager createListenerManager() {
        return new DefaultListenerManager();
    }

    protected ClassPathRegistry createClassPathRegistry() {
        return new DefaultClassPathRegistry();
    }

    protected ClassLoaderRegistry createClassLoaderRegistry() {
        return new DefaultClassLoaderRegistry(get(ClassPathRegistry.class), get(ClassLoaderFactory.class));
    }

    protected CacheFactory createCacheFactory() {
        return new AutoCloseCacheFactory(new InMemoryCacheFactory());
    }

    protected ProgressLoggerFactory createProgressLoggerFactory() {
        return new DefaultProgressLoggerFactory(get(ListenerManager.class).getBroadcaster(ProgressListener.class), new TrueTimeProvider());
    }

    protected Factory<LoggingManagerInternal> createLoggingManagerFactory() {
        return new Factory<LoggingManagerInternal>() {
            public LoggingManagerInternal create() {
                return new NoOpLoggingManager();
            }
        };
    }

    protected StyledTextOutputFactory createStyledTextOutputFactory() {
        return new DefaultStyledTextOutputFactory(get(ListenerManager.class).getBroadcaster(OutputEventListener.class), new TrueTimeProvider());
    }

    protected IsolatedAntBuilder createIsolatedAntBuilder() {
        return new DefaultIsolatedAntBuilder(get(ClassPathRegistry.class), get(ClassLoaderFactory.class));
    }

    protected ClassLoaderFactory createClassLoaderFactory() {
        return new DefaultClassLoaderFactory();
    }
    
    protected MessagingServer createMessagingServer() {
        return new MessagingServer() {
            public Address accept(Action<ConnectEvent<ObjectConnection>> action) {
                throw new UnsupportedOperationException();
            }
        };
    }

}
