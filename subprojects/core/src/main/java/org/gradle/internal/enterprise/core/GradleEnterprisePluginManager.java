/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise.core;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.StartParameter;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.InternalBuildAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class GradleEnterprisePluginManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(GradleEnterprisePluginManager.class);

    @VisibleForTesting
    public static final String NO_SCAN_PLUGIN_MSG = "An internal error occurred that prevented a build scan from being created.\n" +
        "Please report this via https://github.com/gradle/gradle/issues";

    public static final String OLD_SCAN_PLUGIN_VERSION_MESSAGE =
        "The build scan plugin is not compatible with this version of Gradle.\n"
            + "Please see https://gradle.com/help/gradle-6-build-scan-plugin for more information.";

    @Nullable
    private GradleEnterprisePluginAdapter adapter;

    // Indicates plugin checked in, but was unsupported
    private boolean unsupported;

    @Nullable
    public GradleEnterprisePluginAdapter getAdapter() {
        return adapter;
    }

    public void registerAdapter(GradleEnterprisePluginAdapter adapter) {
        if (unsupported) {
            throw new IllegalStateException("plugin already noted as unsupported");
        }
        this.adapter = adapter;
    }

    public void unsupported() {
        if (adapter != null) {
            throw new IllegalStateException("plugin already noted as supported");
        }
        this.unsupported = true;
    }

    public boolean isPresent() {
        return adapter != null;
    }

    public void buildFinished(@Nullable Throwable buildFailure) {
        if (adapter != null) {
            adapter.buildFinished(buildFailure);
        }
    }

    /**
     * This should never happen due to the auto apply behavior.
     * It's only here as a kind of safeguard or fallback.
     */
    public void registerMissingPluginWarning(GradleInternal gradle) {
        if (gradle.isRootBuild()) {
            StartParameter startParameter = gradle.getStartParameter();
            boolean requested = !startParameter.isNoBuildScan() && startParameter.isBuildScan();
            if (requested) {
                gradle.addListener(new InternalBuildAdapter() {
                    @Override
                    public void settingsEvaluated(@Nonnull Settings settings) {
                        if (!isPresent() && !unsupported) {
                            LOGGER.warn(NO_SCAN_PLUGIN_MSG);
                        }
                    }
                });
            }
        }
    }

}
