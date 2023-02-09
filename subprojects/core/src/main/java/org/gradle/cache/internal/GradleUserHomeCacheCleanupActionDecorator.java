/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.cache.CacheConfigurations;
import org.gradle.api.internal.cache.CleanupInternal;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.CleanupProgressMonitor;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.cache.MonitoredCleanupAction;
import org.gradle.internal.cache.MonitoredCleanupActionDecorator;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.util.Properties;

/**
 * Decorates {@link CleanupAction} and {@link MonitoredCleanupAction} instances with a check for whether or not cache cleanup
 * has been disabled for this Gradle user home directory.
 */
public class GradleUserHomeCacheCleanupActionDecorator implements CleanupActionDecorator, MonitoredCleanupActionDecorator {
    public static final String CACHE_CLEANUP_PROPERTY = "org.gradle.cache.cleanup";

    private final GradleUserHomeDirProvider userHomeDirProvider;
    private final CacheConfigurations cacheConfigurations;

    public GradleUserHomeCacheCleanupActionDecorator(GradleUserHomeDirProvider userHomeDirProvider, CacheConfigurations cacheConfigurations) {
        this.userHomeDirProvider = userHomeDirProvider;
        this.cacheConfigurations = cacheConfigurations;
    }

    private boolean isEnabled() {
        File gradleUserHomeDirectory = userHomeDirProvider.getGradleUserHomeDirectory();
        File gradleProperties = new File(gradleUserHomeDirectory, "gradle.properties");
        if (gradleProperties.isFile()) {
            Properties properties = GUtil.loadProperties(gradleProperties);
            String cleanup = properties.getProperty(CACHE_CLEANUP_PROPERTY);
            if (cleanup != null && isNotConfiguredViaDsl()) {
                DeprecationLogger.deprecateAction("Disabling Gradle user home cache cleanup with the '" + CACHE_CLEANUP_PROPERTY + "' property")
                    .willBeRemovedInGradle9()
                    .withUpgradeGuideSection(8, "disabling_user_home_cache_cleanup")
                    .nagUser();
                return !cleanup.equals("false");
            }
            return true;
        }
        return true;
    }

    private boolean isNotConfiguredViaDsl() {
        return cacheConfigurations.getCleanup().get() == CleanupInternal.NOT_SET;
    }

    /**
     * Wraps the provided {@link CleanupAction} in a check so that it only executes if cleanup is not disabled
     *
     * @param cleanup
     * @return the decorated cleanup action
     */
    @Override
    public CleanupAction decorate(CleanupAction cleanup) {
        return (cleanableStore, progressMonitor) -> {
            if (isEnabled()) {
                cleanup.clean(cleanableStore, progressMonitor);
            }
        };
    }

    /**
     * Wraps the provided {@link MonitoredCleanupAction} in a check so that it only executes if cleanup is not disabled
     *
     * @param cleanupAction
     * @return the decorated directory cleanup action
     */
    @Override
    public MonitoredCleanupAction decorate(MonitoredCleanupAction cleanupAction) {
        return new MonitoredCleanupAction() {
            @Override
            public boolean execute(CleanupProgressMonitor progressMonitor) {
                return isEnabled() && cleanupAction.execute(progressMonitor);
            }

            @Override
            public String getDisplayName() {
                return cleanupAction.getDisplayName();
            }
        };
    }
}
