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

package org.gradle.internal.environment;

import com.google.common.collect.ImmutableMap;
import org.gradle.initialization.Environment;
import org.gradle.util.internal.GUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.stream.Collectors;

import static org.gradle.internal.Cast.uncheckedNonnullCast;

public class DefaultEnvironment implements Environment {

    @Override
    @Nullable
    public Map<String, String> propertiesFile(File propertiesFile) {
        if (!propertiesFile.isFile()) {
            return null;
        }

        Map<String, String> properties = uncheckedNonnullCast(GUtil.loadProperties(propertiesFile));
        return ImmutableMap.copyOf(properties);
    }

    @Override
    public Environment.Properties getSystemProperties() {
        Map<String, String> properties = uncheckedNonnullCast(System.getProperties());
        return new DefaultProperties(properties);
    }

    @Override
    public Environment.Properties getVariables() {
        return new DefaultProperties(System.getenv());
    }

    public static class DefaultProperties implements Environment.Properties {
        private final Map<String, String> map;

        DefaultProperties(Map<String, String> map) {
            this.map = ImmutableMap.copyOf(map);
        }

        @Override
        public Map<String, String> byNamePrefix(String prefix) {
            return filterKeysByPrefix(map, prefix);
        }

        @Override
        @Nullable
        public String get(String name) {
            return map.get(name);
        }

        private static Map<String, String> filterKeysByPrefix(Map<String, String> map, String prefix) {
            return map.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }
}
