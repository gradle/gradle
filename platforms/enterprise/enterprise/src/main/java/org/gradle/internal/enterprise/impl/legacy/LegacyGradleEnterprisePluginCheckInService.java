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
import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.buildtree.BuildActionModelRequirements;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.scan.config.BuildScanConfig;
import org.gradle.internal.scan.config.BuildScanConfigProvider;
import org.gradle.internal.scan.config.BuildScanPluginMetadata;
import org.gradle.internal.scan.eob.BuildScanEndOfBuildNotifier;
import org.gradle.util.internal.VersionNumber;

import javax.inject.Inject;

/**
 * A check-in service used by the Gradle Enterprise plugin versions until 3.4, none of which are supported anymore.
 * <p>
 * We keep this service, because for the plugin versions 3.0+ we can gracefully avoid plugin application and report an unsupported message.
 * <p>
 * More modern versions of the plugin use {@link org.gradle.internal.enterprise.GradleEnterprisePluginCheckInService}.
 */
public class LegacyGradleEnterprisePluginCheckInService implements BuildScanConfigProvider, BuildScanEndOfBuildNotifier {

    public static final String FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION_DISPLAY = "3.0";
    public static final VersionNumber FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION = VersionNumber.parse(FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION_DISPLAY);

    private static final VersionNumber FIRST_VERSION_AWARE_OF_UNSUPPORTED = VersionNumber.parse("1.11");

    private final GradleInternal gradle;
    private final GradleEnterprisePluginManager manager;
    private final boolean taskExecutingBuild;

    @Inject
    public LegacyGradleEnterprisePluginCheckInService(
        GradleInternal gradle,
        GradleEnterprisePluginManager manager,
        BuildActionModelRequirements requirements
    ) {
        this.gradle = gradle;
        this.manager = manager;
        this.taskExecutingBuild = requirements.isRunsTasks();
    }

    @Override
    public BuildScanConfig collect(BuildScanPluginMetadata pluginMetadata) {
        if (manager.isPresent()) {
            throw new IllegalStateException("Configuration has already been collected.");
        }

        String pluginVersion = pluginMetadata.getVersion();
        VersionNumber pluginBaseVersion = VersionNumber.parse(pluginVersion).getBaseVersion();
        if (pluginBaseVersion.compareTo(FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION) < 0) {
            throw new UnsupportedBuildScanPluginVersionException(GradleEnterprisePluginManager.OLD_SCAN_PLUGIN_VERSION_MESSAGE);
        }

        String unsupportedReason = DevelocityPluginCompatibility.getUnsupportedPluginMessage(pluginVersion);
        manager.unsupported();
        if (!isPluginAwareOfUnsupported(pluginBaseVersion)) {
            throw new UnsupportedBuildScanPluginVersionException(unsupportedReason);
        }

        return new Config(
            Requestedness.from(gradle),
            new AttributesImpl(taskExecutingBuild),
            unsupportedReason
        );
    }

    @Override
    public void notify(BuildScanEndOfBuildNotifier.Listener listener) {
        // Should not get here, since none of the plugin versions using this service are supported
    }

    private static boolean isPluginAwareOfUnsupported(VersionNumber pluginVersion) {
        return pluginVersion.compareTo(FIRST_VERSION_AWARE_OF_UNSUPPORTED) >= 0;
    }

    private static class Config implements BuildScanConfig {
        private final Requestedness requestedness;
        private final String unsupported;
        private final Attributes attributes;

        public Config(Requestedness requestedness, AttributesImpl attributes, String unsupported) {
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
        public BuildScanConfig.Attributes getAttributes() {
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

    private static class AttributesImpl implements BuildScanConfig.Attributes {
        private final boolean taskExecutingBuild;

        private AttributesImpl(boolean taskExecutingBuild) {
            this.taskExecutingBuild = taskExecutingBuild;
        }

        @Override
        public boolean isRootProjectHasVcsMappings() {
            return false;
        }

        @Override
        public boolean isTaskExecutingBuild() {
            return taskExecutingBuild;
        }
    }

}
