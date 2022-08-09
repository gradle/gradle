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

import org.gradle.cache.scopes.GlobalScopedCache;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.service.ServiceRegistration;

public class GradleUserHomeCleanupServices {

    public void configure(
        ServiceRegistration registration,
        GlobalScopedCache globalScopedCache,
        Deleter deleter,
        GradleUserHomeDirProvider gradleUserHomeDirProvider,
        ProgressLoggerFactory progressLoggerFactory
    ) {
        UsedGradleVersions usedGradleVersions = new UsedGradleVersionsFromGradleUserHomeCaches(globalScopedCache);
        registration.add(UsedGradleVersions.class, usedGradleVersions);
        // register eagerly so stop() is triggered when services are being stopped
        registration.add(
            GradleUserHomeCleanupService.class,
            new GradleUserHomeCleanupService(deleter, gradleUserHomeDirProvider, globalScopedCache, usedGradleVersions, progressLoggerFactory)
        );
    }

}
