/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.cache.internal;

import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.session.BuildSessionLifecycleListener;

public class GradleUserHomeCleanupServices {

    public void configure(
        ServiceRegistration registration,
        GlobalScopedCacheBuilderFactory cacheBuilderFactory,
        Deleter deleter,
        GradleUserHomeDirProvider gradleUserHomeDirProvider,
        ProgressLoggerFactory progressLoggerFactory,
        CacheConfigurationsInternal cacheConfigurations,
        ListenerManager listenerManager,
        CacheFactory cacheFactory
    ) {
        UsedGradleVersions usedGradleVersions = new UsedGradleVersionsFromGradleUserHomeCaches(cacheBuilderFactory);
        registration.add(UsedGradleVersions.class, usedGradleVersions);

        // register eagerly so stop() is triggered when services are being stopped
        GradleUserHomeCleanupService gradleUserHomeCleanupService = new GradleUserHomeCleanupService(deleter, gradleUserHomeDirProvider, cacheBuilderFactory, usedGradleVersions, progressLoggerFactory, cacheConfigurations);
        registration.add(
            GradleUserHomeCleanupService.class,
            gradleUserHomeCleanupService
        );

        listenerManager.addListener(new BuildSessionLifecycleListener() {
            @Override
            public void beforeComplete() {
                if (cacheConfigurations.getCleanupFrequency().get().shouldCleanupOnEndOfSession()) {
                    gradleUserHomeCleanupService.cleanup();
                    cacheFactory.visitCaches(PersistentCache::cleanup);
                }
            }
        });
    }

}
