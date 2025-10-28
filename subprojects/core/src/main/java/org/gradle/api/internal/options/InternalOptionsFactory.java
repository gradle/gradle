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
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.gradle.internal.Cast.uncheckedNonnullCast;

public class InternalOptionsFactory {

    public static InternalOptions createInternalOptions(
        StartParameterInternal startParameter,
        File rootBuildDir
    ) {
        Map<String, String> properties = new HashMap<>();
        putAllProperties(properties, startParameter.getGradleHomeDir());
        putAllProperties(properties, rootBuildDir);
        putAllProperties(properties, startParameter.getGradleUserHomeDir());
        properties.putAll(startParameter.getSystemPropertiesArgs());

        // TODO: ensure all internal properties have `org.gradle.internal.` prefix
        Map<String, String> internalProperties = filterKeysByPrefix(properties, "org.gradle.");
        return new DefaultInternalOptions(internalProperties);
    }

    private static Map<String, String> filterKeysByPrefix(Map<String, String> map, String prefix) {
        return map.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(prefix))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static void putAllProperties(Map<String, String> properties, File dir) {
        File propertiesFile = new File(dir, Project.GRADLE_PROPERTIES);
        if (propertiesFile.isFile()) {
            Map<String, String> readProperties = uncheckedNonnullCast(GUtil.loadProperties(propertiesFile));
            properties.putAll(readProperties);
        }
    }
}
