/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.scan.config;

import org.gradle.BuildAdapter;
import org.gradle.StartParameter;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.event.ListenerManager;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * This is the meeting point between Gradle and the build scan plugin during initialization.
 * This is effectively build scoped.
 */
class BuildScanConfigManager implements BuildScanConfigInit, BuildScanConfigProvider {

    private static final Logger LOGGER = Logging.getLogger(BuildScanConfigManager.class);

    private static final String HELP_LINK = "https://gradle.com/scans/help/gradle-cli";
    private static final String SYSPROP_KEY = "scan";
    private static final List<String> ENABLED_SYS_PROP_VALUES = Arrays.asList(null, "", "yes", "true");

    private final StartParameter startParameter;
    private final ListenerManager listenerManager;
    private final BuildScanPluginCompatibilityEnforcer compatibilityEnforcer;

    private State state = State.DEFAULTED;
    private boolean collected;

    BuildScanConfigManager(StartParameter startParameter, ListenerManager listenerManager, BuildScanPluginCompatibilityEnforcer compatibilityEnforcer) {
        this.startParameter = startParameter;
        this.listenerManager = listenerManager;
        this.compatibilityEnforcer = compatibilityEnforcer;
    }

    @Override
    public void init() {
        boolean checkForPlugin = false;
        if (startParameter.isBuildScan()) {
            checkForPlugin = true;
            state = State.ENABLED;
        } else if (startParameter.isNoBuildScan()) {
            state = State.DISABLED;
        } else {
            // Before there was --scan, there was -Dscan or -Dscan=true or -Dscan=yes
            Map<String, String> sysProps = startParameter.getSystemPropertiesArgs();
            if (sysProps.containsKey(SYSPROP_KEY)) {
                String sysProp = sysProps.get(SYSPROP_KEY);
                checkForPlugin = ENABLED_SYS_PROP_VALUES.contains(sysProp);
            }
        }

        if (checkForPlugin) {
            warnIfBuildScanPluginNotApplied();
        }
    }

    private void warnIfBuildScanPluginNotApplied() {
        // Note: this listener manager is scoped to the root Gradle object.
        listenerManager.addListener(new BuildAdapter() {
            @Override
            public void projectsEvaluated(Gradle gradle) {
                if (!collected) {
                    LOGGER.warn(
                        "Build scan cannot be created because the build scan plugin was not applied.\n"
                            + "For more information on how to apply the build scan plugin, please visit " + HELP_LINK + "."
                    );
                }
            }
        });
    }

    @Override
    public BuildScanConfig collect(BuildScanPluginMetadata pluginMetadata) {
        if (collected) {
            throw new IllegalStateException("Configuration has already been collected.");
        }

        collected = true;
        compatibilityEnforcer.assertSupported(pluginMetadata.getVersion());
        return state.configuration;
    }

    private enum State {
        DEFAULTED(new Config(false, false)),
        ENABLED(new Config(true, false)),
        DISABLED(new Config(false, true));

        private final BuildScanConfig configuration;

        State(BuildScanConfig configuration) {
            this.configuration = configuration;
        }
    }

    private static class Config implements BuildScanConfig {

        private final boolean enabled;
        private final boolean disabled;

        private Config(boolean enabled, boolean disabled) {
            this.enabled = enabled;
            this.disabled = disabled;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public boolean isDisabled() {
            return disabled;
        }

    }
}
