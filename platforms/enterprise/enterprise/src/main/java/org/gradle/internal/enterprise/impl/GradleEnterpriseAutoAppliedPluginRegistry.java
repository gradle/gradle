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

package org.gradle.internal.enterprise.impl;

import com.google.common.base.Strings;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.plugin.management.internal.DefaultPluginRequest;
import org.gradle.plugin.management.internal.PluginCoordinates;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.management.internal.autoapply.AutoAppliedDevelocityPlugin;
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginRegistry;

import static org.gradle.initialization.StartParameterBuildOptions.BuildScanOption;
import static org.gradle.plugin.management.internal.PluginRequestInternal.Origin.AUTO_APPLIED;

@ServiceScope(Scope.BuildTree.class)
public class GradleEnterpriseAutoAppliedPluginRegistry implements AutoAppliedPluginRegistry {

    @Override
    public PluginRequests getAutoAppliedPlugins(Project target) {
        return PluginRequests.EMPTY;
    }

    @Override
    public PluginRequests getAutoAppliedPlugins(Settings target) {
        if (((StartParameterInternal) target.getStartParameter()).isUseEmptySettings() || !shouldApplyDevelocityPlugin(target)) {
            return PluginRequests.EMPTY;
        } else {
            // We are going with an auto-application request, let's configure the URL
            // TODO Remove this once the default applied version supports DefaultGradleEnterprisePluginConfig.getDevelocityUrl()
            target.getPluginManager().withPlugin(AutoAppliedDevelocityPlugin.ID.getId(), new DevelocityAutoAppliedPluginConfigurationAction(target));
            return PluginRequests.of(createDevelocityPluginRequest());
        }
    }

    private static boolean shouldApplyDevelocityPlugin(Settings settings) {
        Gradle gradle = settings.getGradle();
        StartParameterInternal startParameter = (StartParameterInternal) gradle.getStartParameter();
        return (startParameter.isBuildScan()
                || !Strings.isNullOrEmpty(startParameter.getDevelocityUrl()))
            && gradle.getParent() == null;
    }

    private static PluginRequestInternal createDevelocityPluginRequest() {
        ModuleIdentifier moduleIdentifier = DefaultModuleIdentifier.newId(AutoAppliedDevelocityPlugin.GROUP, AutoAppliedDevelocityPlugin.NAME);
        ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(moduleIdentifier, AutoAppliedDevelocityPlugin.VERSION);
        return new DefaultPluginRequest(
            AutoAppliedDevelocityPlugin.ID,
            true,
            AUTO_APPLIED,
            getScriptDisplayName(),
            null,
            AutoAppliedDevelocityPlugin.VERSION,
            selector,
            null,
            gradleEnterprisePluginCoordinates()
        );
    }

    private static PluginCoordinates gradleEnterprisePluginCoordinates() {
        ModuleIdentifier moduleIdentifier = DefaultModuleIdentifier.newId(AutoAppliedDevelocityPlugin.GROUP, AutoAppliedDevelocityPlugin.GRADLE_ENTERPRISE_PLUGIN_ARTIFACT_NAME);
        ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(moduleIdentifier, AutoAppliedDevelocityPlugin.VERSION);
        return new PluginCoordinates(AutoAppliedDevelocityPlugin.GRADLE_ENTERPRISE_PLUGIN_ID, selector);
    }

    private static String getScriptDisplayName() {
        // TODO This needs to be aware of the application reason, which can be the DV URL now
        return String.format("auto-applied by using --%s", BuildScanOption.LONG_OPTION);
    }
}
