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

package org.gradle.internal.scan.impl;

import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginEndOfBuildNotifier;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginPresence;
import org.gradle.internal.scan.config.BuildScanConfig;
import org.gradle.internal.scan.config.BuildScanConfigProvider;
import org.gradle.internal.scan.config.BuildScanPluginMetadata;
import org.gradle.internal.scan.eob.BuildScanEndOfBuildNotifier;
import org.gradle.util.VersionNumber;

import javax.annotation.Nullable;
import javax.inject.Inject;

public class BuildScanPluginManager implements BuildScanConfigProvider, BuildScanEndOfBuildNotifier {

    public static final String FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION_DISPLAY = "3.0";
    public static final VersionNumber FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION = VersionNumber.parse(FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION_DISPLAY);

    // Used just to test the mechanism
    public static final String UNSUPPORTED_TOGGLE = "org.gradle.internal.unsupported-scan-plugin";
    public static final String UNSUPPORTED_TOGGLE_MESSAGE = "Build scan support disabled by secret toggle";

    private static final VersionNumber FIRST_VERSION_AWARE_OF_UNSUPPORTED = VersionNumber.parse("1.11");

    private final GradleInternal gradle;
    private final GradleEnterprisePluginPresence presence;
    private final GradleEnterprisePluginEndOfBuildNotifier endOfBuildNotifier;

    @Inject
    public BuildScanPluginManager(
        GradleInternal gradle,
        GradleEnterprisePluginPresence presence,
        GradleEnterprisePluginEndOfBuildNotifier endOfBuildNotifier
    ) {
        this.gradle = gradle;
        this.presence = presence;
        this.endOfBuildNotifier = endOfBuildNotifier;
    }

    public static String unsupportedReason() {
        if (Boolean.getBoolean(UNSUPPORTED_TOGGLE)) {
            return UNSUPPORTED_TOGGLE_MESSAGE;
        }
        return null;
    }

    @Override
    public BuildScanConfig collect(BuildScanPluginMetadata pluginMetadata) {
        if (presence.isPresent()) {
            throw new IllegalStateException("Configuration has already been collected.");
        }

        VersionNumber pluginVersion = VersionNumber.parse(pluginMetadata.getVersion()).getBaseVersion();
        if (pluginVersion.compareTo(FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION) < 0) {
            throw new UnsupportedBuildScanPluginVersionException(GradleEnterprisePluginPresence.OLD_SCAN_PLUGIN_VERSION_MESSAGE);
        }

        String unsupportedReason = unsupportedReason();
        if (unsupportedReason == null) {
            presence.markPresent();
        }

        if (unsupportedReason == null || isPluginAwareOfUnsupported(pluginVersion)) {
            BuildScanConfig.Attributes configAttributes = configAttributes(gradle);
            return requestedness(gradle).toConfig(unsupportedReason, configAttributes);
        } else {
            throw new UnsupportedBuildScanPluginVersionException(unsupportedReason);
        }
    }

    @Override
    public void notify(Listener listener) {
        endOfBuildNotifier.registerOnlyListener(buildFailure ->
            listener.execute(new BuildResult() {
                @Nullable
                @Override
                public Throwable getFailure() {
                    return buildFailure;
                }
            })
        );
    }

    private static Requestedness requestedness(GradleInternal gradle) {
        StartParameter startParameter = gradle.getStartParameter();
        if (startParameter.isNoBuildScan()) {
            return Requestedness.DISABLED;
        } else if (startParameter.isBuildScan()) {
            return Requestedness.ENABLED;
        } else {
            return Requestedness.DEFAULTED;
        }
    }

    private static BuildScanConfig.Attributes configAttributes(GradleInternal gradle) {
        return new BuildScanConfig.Attributes() {
            @Override
            public boolean isRootProjectHasVcsMappings() {
                return false;
            }

            @Override
            public boolean isTaskExecutingBuild() {
                boolean forceTaskExecutingBuild = System.getProperty("org.gradle.internal.ide.scan") != null;
                return forceTaskExecutingBuild || gradle.getBuildType() == GradleInternal.BuildType.TASKS;
            }
        };
    }

    private boolean isPluginAwareOfUnsupported(VersionNumber pluginVersion) {
        return pluginVersion.compareTo(FIRST_VERSION_AWARE_OF_UNSUPPORTED) >= 0;
    }

    private enum Requestedness {

        DEFAULTED(false, false),
        ENABLED(true, false),
        DISABLED(false, true);

        private final boolean enabled;
        private final boolean disabled;

        Requestedness(boolean enabled, boolean disabled) {
            this.enabled = enabled;
            this.disabled = disabled;
        }

        BuildScanConfig toConfig(final String unsupported, final BuildScanConfig.Attributes attributes) {
            return new BuildScanConfig() {
                @Override
                public boolean isEnabled() {
                    return enabled;
                }

                @Override
                public boolean isDisabled() {
                    return disabled;
                }

                @Override
                public String getUnsupportedMessage() {
                    return unsupported;
                }

                @Override
                public Attributes getAttributes() {
                    return attributes;
                }
            };
        }
    }

}
