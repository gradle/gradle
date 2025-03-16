/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.util.Properties;

@ServiceScope(Scope.UserHome.class)
public class LegacyCacheCleanupEnablement {
    public static final String CACHE_CLEANUP_PROPERTY = "org.gradle.cache.cleanup";

    private final GradleUserHomeDirProvider userHomeDirProvider;

    public LegacyCacheCleanupEnablement(GradleUserHomeDirProvider userHomeDirProvider) {
        this.userHomeDirProvider = userHomeDirProvider;
    }

    public boolean isDisabledByProperty() {
        File gradleUserHomeDirectory = userHomeDirProvider.getGradleUserHomeDirectory();
        File gradleProperties = new File(gradleUserHomeDirectory, "gradle.properties");
        if (gradleProperties.isFile()) {
            Properties properties = GUtil.loadProperties(gradleProperties);
            String cleanup = properties.getProperty(CACHE_CLEANUP_PROPERTY);
            if (cleanup != null) {
                DeprecationLogger.deprecateAction("Disabling Gradle user home cache cleanup with the '" + CACHE_CLEANUP_PROPERTY + "' property")
                    .willBeRemovedInGradle9()
                    .withUpgradeGuideSection(8, "disabling_user_home_cache_cleanup")
                    .nagUser();
                return cleanup.equals("false");
            }
        }
        return false;
    }
}
