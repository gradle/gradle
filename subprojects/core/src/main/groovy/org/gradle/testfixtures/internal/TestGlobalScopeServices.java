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

import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.Factory;
import org.gradle.internal.TrueTimeProvider;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.logging.internal.DefaultProgressLoggerFactory;
import org.gradle.logging.internal.DefaultStyledTextOutputFactory;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.logging.internal.ProgressListener;

public class TestGlobalScopeServices extends GlobalScopeServices {
    public TestGlobalScopeServices() {
        super(false);
    }

    @Override
    protected CacheFactory createCacheFactory(FileLockManager fileLockManager) {
        return new InMemoryCacheFactory();
    }

    public static class TestLoggingServices extends DefaultServiceRegistry {
        final ListenerManager listenerManager = new DefaultListenerManager();

        protected ProgressLoggerFactory createProgressLoggerFactory() {
            return new DefaultProgressLoggerFactory(listenerManager.getBroadcaster(ProgressListener.class), new TrueTimeProvider());
        }

        protected Factory<LoggingManagerInternal> createLoggingManagerFactory() {
            return new Factory<LoggingManagerInternal>() {
                public LoggingManagerInternal create() {
                    return new NoOpLoggingManager();
                }
            };
        }

        protected StyledTextOutputFactory createStyledTextOutputFactory() {
            return new DefaultStyledTextOutputFactory(listenerManager.getBroadcaster(OutputEventListener.class), new TrueTimeProvider());
        }

        protected TestOutputEventListener createStubOutputEventListener() {
            return new TestOutputEventListener();
        }
    }
}
