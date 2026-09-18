/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.deprecation;

import org.gradle.StartParameter;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.buildoption.DeprecatedBuildOptionUsageRegistry;
import org.gradle.internal.buildoption.DeprecatedBuildOptionUsageRegistry.DeprecatedUsage;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Drains the global {@link DeprecatedBuildOptionUsageRegistry} and emits a deprecation warning
 * for each recorded usage of a {@link BuildOption}'s {@code deprecatedProperty}. Also re-scans
 * the start parameter's command-line {@code -D} entries so the warning fires when the launcher
 * and daemon are different JVMs (the registry only catches the launcher-side conversion).
 *
 * <p>The {@code -D} scan only sees properties the user passed on the command line. Deprecated
 * names set via {@code gradle.properties} reach the build through a different channel and rely
 * on the in-process registry path; cross-JVM coverage for that case would need explicit
 * propagation through the build action wire format.
 */
public class BuildOptionDeprecations {

    public static void nagAboutDeprecatedBuildOptionProperties(StartParameter startParameter) {
        for (DeprecatedUsage usage : collectDeprecatedUsages(startParameter)) {
            DeprecationLogger.deprecateSystemProperty(usage.getDeprecatedProperty())
                .replaceWith(usage.getReplacementProperty())
                .willBeRemovedInGradle10()
                .withUpgradeGuideSection(9, "deprecated_unsafe_configuration_cache_properties")
                .nagUser();
        }
    }

    private static Set<DeprecatedUsage> collectDeprecatedUsages(StartParameter startParameter) {
        Set<DeprecatedUsage> usages = new LinkedHashSet<>(DeprecatedBuildOptionUsageRegistry.drain());
        Map<String, String> systemPropertiesArgs = startParameter.getSystemPropertiesArgs();
        for (BuildOption<?> option : new StartParameterBuildOptions().getAllOptions()) {
            String deprecatedProperty = option.getDeprecatedProperty();
            String replacementProperty = option.getProperty();
            if (deprecatedProperty == null || replacementProperty == null) {
                continue;
            }
            if (systemPropertiesArgs.containsKey(deprecatedProperty)) {
                usages.add(new DeprecatedUsage(deprecatedProperty, replacementProperty));
            }
        }
        return usages;
    }
}
