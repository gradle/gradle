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

package org.gradle.api.internal.options;

import org.gradle.api.Project;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.internal.buildoption.DefaultInternalOptions;
import org.gradle.internal.buildoption.InternalOption;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.util.internal.GUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.gradle.internal.Cast.uncheckedNonnullCast;

/**
 * Factory for resolving {@link InternalOptions} from {@code gradle.properties} files and system arguments.
 * <p>
 * This factory is separate from {@link org.gradle.api.internal.properties.GradlePropertiesController GradlePropertiesController}, because
 * we require internal properties very early in the Gradle invocation lifecycle (even before we have the root build tree).
 * The {@code GradlePropertiesController} on the other hand is only available within a build-tree,
 * and concerns itself with the user-facing set of properties and the tracking of their access.
 */
public class InternalOptionsFactory {

    /**
     * Creates {@link InternalOptions} by collecting internal options from multiple sources.
     * <p>
     * Internal options are properties with {@code org.gradle.internal.} prefix (with some {@link InternalOption#isInternalOption(String) exceptions}).
     * <p>
     * The options are merged from multiple places with the following priority (higher to lower):
     * <ul>
     * <li>{@code startParameter.systemPropertiesArgs} aka {@code -D} arguments</li>
     * <li>{@code <Gradle User Home>/gradle.properties}</li>
     * <li>{@code <build-root directory>/gradle.properties}</li>
     * <li>{@code <Gradle Home>/gradle.properties}</li>
     * </ul>
     *
     * @see InternalOption#isInternalOption(String)
     */
    public static InternalOptions createInternalOptions(
        StartParameterInternal startParameter,
        File rootBuildDir
    ) {
        Map<String, String> properties = new HashMap<>();
        putAllProperties(properties, startParameter.getGradleHomeDir());
        putAllProperties(properties, rootBuildDir);
        putAllProperties(properties, startParameter.getGradleUserHomeDir());
        properties.putAll(startParameter.getSystemPropertiesArgs());

        Map<String, String> internalProperties = filterInternalOptions(properties);
        return new DefaultInternalOptions(internalProperties);
    }

    private static Map<String, String> filterInternalOptions(Map<String, String> map) {
        return map.entrySet().stream()
            .filter(entry -> InternalOption.isInternalOption(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static void putAllProperties(Map<String, String> properties, @Nullable File dir) {
        if (dir == null) {
            return;
        }

        File propertiesFile = new File(dir, Project.GRADLE_PROPERTIES);
        if (propertiesFile.isFile()) {
            Map<String, String> readProperties = uncheckedNonnullCast(GUtil.loadProperties(propertiesFile));
            properties.putAll(readProperties);
        }
    }
}
