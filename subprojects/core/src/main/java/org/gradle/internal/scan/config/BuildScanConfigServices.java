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

import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.Factory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.scan.BuildScanRequest;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.vcs.VcsMappings;
import org.gradle.vcs.internal.VcsMappingsInternal;

/**
 * Wiring of the objects that provide the build scan config integration.
 *
 * The objects provided here are are requested of the root project of the root build's service registry.
 */
public class BuildScanConfigServices {

    BuildScanPluginCompatibility createBuildScanPluginCompatibility() {
        return new BuildScanPluginCompatibility();
    }

    BuildScanConfigManager createBuildScanConfigManager(
        StartParameter startParameter,
        ListenerManager listenerManager,
        BuildScanPluginCompatibility compatibility,
        ServiceRegistry serviceRegistry
    ) {
        return new BuildScanConfigManager(startParameter, listenerManager, compatibility, serviceRegistry.getFactory(BuildScanConfig.Attributes.class));
    }

    // legacy support
    BuildScanRequest createBuildScanRequest(BuildScanPluginCompatibility compatibilityEnforcer) {
        return new BuildScanRequestLegacyBridge(compatibilityEnforcer);
    }

    Factory<BuildScanConfig.Attributes> createBuildScanConfigAttributes(final Gradle gradle) {
        return new Factory<BuildScanConfig.Attributes>() {
            @Override
            public BuildScanConfig.Attributes create() {
                VcsMappings vcsMappings = ((GradleInternal) gradle).getSettings().getSourceControl().getVcsMappings();
                final boolean hasRules = ((VcsMappingsInternal) vcsMappings).hasRules();
                return new BuildScanConfig.Attributes() {
                    @Override
                    public boolean isRootProjectHasVcsMappings() {
                        return hasRules;
                    }
                };
            }
        };
    }

}
