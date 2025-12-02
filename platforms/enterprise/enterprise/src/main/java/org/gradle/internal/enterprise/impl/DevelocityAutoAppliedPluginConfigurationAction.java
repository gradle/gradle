/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.enterprise.impl;

import org.gradle.api.Action;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.provider.Property;
import org.gradle.internal.Cast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures the Develocity plugin if it was auto-applied.
 * <p>
 * This is a temporary solution until the default applied version understands the added develocity URL configuration option.
 */
public class DevelocityAutoAppliedPluginConfigurationAction implements Action<AppliedPlugin> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevelocityAutoAppliedPluginConfigurationAction.class);

    private final SettingsInternal settings;

    public DevelocityAutoAppliedPluginConfigurationAction(Settings settings) {
        this.settings = (SettingsInternal) settings;
    }

    @Override
    public void execute(AppliedPlugin plugin) {
        GradleEnterprisePluginAutoAppliedStatus autoAppliedStatus = settings.getServices().get(GradleEnterprisePluginAutoAppliedStatus.class);
        String develocityUrl = ((StartParameterInternal) settings.getStartParameter()).getDevelocityUrl();
        if (autoAppliedStatus.isAutoApplied() && develocityUrl != null) {
            Object develocityConfiguration = settings.getExtensions().getByName("develocity");
            try {
                Property<String> server = Cast.uncheckedNonnullCast(develocityConfiguration.getClass().getMethod("getServer").invoke(develocityConfiguration));
                server.convention(develocityUrl);
            } catch (Exception e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Unable to configure auto-applied Develocity plugin server URL", e);
                } else {
                    LOGGER.warn("Unable to configure auto-applied Develocity plugin server URL");
                }
            }
        }
    }
}
