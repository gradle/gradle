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

package org.gradle.internal.enterprise.impl.legacy;

import org.gradle.StartParameter;
import org.gradle.api.internal.BuildType;
import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginAdapter;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.scan.config.BuildScanConfig;
import org.gradle.internal.scan.config.BuildScanConfigProvider;
import org.gradle.internal.scan.config.BuildScanPluginMetadata;
import org.gradle.internal.scan.eob.BuildScanEndOfBuildNotifier;
import org.gradle.util.VersionNumber;

import javax.annotation.Nullable;
import javax.inject.Inject;

public class LegacyGradleEnterprisePluginCheckInService implements BuildScanConfigProvider, BuildScanEndOfBuildNotifier {

    public static final String FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION_DISPLAY = "3.0";
    public static final VersionNumber FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION = VersionNumber.parse(FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION_DISPLAY);

    // Used just to test the mechanism
    public static final String UNSUPPORTED_TOGGLE = "org.gradle.internal.unsupported-scan-plugin";
    public static final String UNSUPPORTED_TOGGLE_MESSAGE = "Build scan support disabled by secret toggle";

    private static final VersionNumber FIRST_VERSION_AWARE_OF_UNSUPPORTED = VersionNumber.parse("1.11");

    private final GradleInternal gradle;
    private final GradleEnterprisePluginManager manager;
    private final BuildType buildType;

    private BuildScanEndOfBuildNotifier.Listener listener;

    @Inject
    public LegacyGradleEnterprisePluginCheckInService(
        GradleInternal gradle,
        GradleEnterprisePluginManager manager,
        BuildType buildType
    ) {
        this.gradle = gradle;
        this.manager = manager;
        this.buildType = buildType;
    }

    @Nullable
    private String unsupportedReason(VersionNumber pluginVersion) {
        if (Boolean.getBoolean(UNSUPPORTED_TOGGLE)) {
            return UNSUPPORTED_TOGGLE_MESSAGE;
        } else if (gradle.getStartParameter().isConfigurationCache()) {
            return "Build scans have been disabled due to incompatibility between your Gradle Enterprise plugin version (" + pluginVersion.toString() + ") and configuration caching. " +
                "Please use Gradle Enterprise plugin version 3.4 or later for compatibility with configuration caching.";
        } else {
            return null;
        }
    }

    @Override
    public BuildScanConfig collect(BuildScanPluginMetadata pluginMetadata) {
        if (manager.isPresent()) {
            throw new IllegalStateException("Configuration has already been collected.");
        }

        VersionNumber pluginVersion = VersionNumber.parse(pluginMetadata.getVersion()).getBaseVersion();
        if (pluginVersion.compareTo(FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION) < 0) {
            throw new UnsupportedBuildScanPluginVersionException(GradleEnterprisePluginManager.OLD_SCAN_PLUGIN_VERSION_MESSAGE);
        }

        String unsupportedReason = unsupportedReason(pluginVersion);
        if (unsupportedReason == null) {
            manager.registerAdapter(new Adapter());
        } else {
            manager.unsupported();
            if (!isPluginAwareOfUnsupported(pluginVersion)) {
                throw new UnsupportedBuildScanPluginVersionException(unsupportedReason);
            }
        }

        return new Config(
            Requestedness.from(gradle),
            new Attributes(buildType),
            unsupportedReason
        );
    }

    @Override
    public void notify(BuildScanEndOfBuildNotifier.Listener listener) {
        if (this.listener != null) {
            throw new IllegalStateException("listener already set to " + this.listener);
        }
        this.listener = listener;
    }

    private boolean isPluginAwareOfUnsupported(VersionNumber pluginVersion) {
        return pluginVersion.compareTo(FIRST_VERSION_AWARE_OF_UNSUPPORTED) >= 0;
    }

    private static class Config implements BuildScanConfig {
        private final Requestedness requestedness;
        private final String unsupported;
        private final Attributes attributes;

        public Config(Requestedness requestedness, Attributes attributes, String unsupported) {
            this.requestedness = requestedness;
            this.unsupported = unsupported;
            this.attributes = attributes;
        }

        @Override
        public boolean isEnabled() {
            return requestedness.enabled;
        }

        @Override
        public boolean isDisabled() {
            return requestedness.disabled;
        }

        @Override
        public String getUnsupportedMessage() {
            return unsupported;
        }

        @Override
        public Attributes getAttributes() {
            return attributes;
        }
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

        private static Requestedness from(GradleInternal gradle) {
            StartParameter startParameter = gradle.getStartParameter();
            if (startParameter.isNoBuildScan()) {
                return DISABLED;
            } else if (startParameter.isBuildScan()) {
                return ENABLED;
            } else {
                return DEFAULTED;
            }
        }
    }

    private static class Attributes implements BuildScanConfig.Attributes {
        private final BuildType buildType;

        public Attributes(BuildType buildType) {
            this.buildType = buildType;
        }

        @Override
        public boolean isRootProjectHasVcsMappings() {
            return false;
        }

        @Override
        public boolean isTaskExecutingBuild() {
            boolean forceTaskExecutingBuild = System.getProperty("org.gradle.internal.ide.scan") != null;
            return forceTaskExecutingBuild || buildType == BuildType.TASKS;
        }
    }

    private class Adapter implements GradleEnterprisePluginAdapter {
        @Override
        public boolean shouldSaveToConfigurationCache() {
            return false;
        }

        @Override
        public void onLoadFromConfigurationCache() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void buildFinished(@Nullable Throwable buildFailure) {
            if (listener != null) {
                listener.execute(new BuildResult() {
                    @Nullable
                    @Override
                    public Throwable getFailure() {
                        return buildFailure;
                    }
                });
            }
        }
    }
}
