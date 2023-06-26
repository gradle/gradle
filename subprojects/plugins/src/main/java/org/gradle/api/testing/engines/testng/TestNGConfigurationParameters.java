/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.testing.engines.testng;

import com.google.common.collect.Maps;
import org.gradle.api.plugins.jvm.TestEngineConfigurationParameters;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.Cast;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.SystemPropertiesCommandLineProvider;

import java.util.Map;
import java.util.function.Function;

public interface TestNGConfigurationParameters extends TestEngineConfigurationParameters {
    enum ParallelMode {
        NONE,
        METHODS,
        TESTS,
        CLASSES,
        INSTANCES
    }

    @Input
    @Optional
    Property<Boolean> getAllowReturnValues();

    @Input
    @Optional
    Property<Integer> getDataProviderThreadCount();

    @Input
    @Optional
    ListProperty<String> getExcludedGroups();

    @Input
    @Optional
    ListProperty<String> getGroups();

    @Input
    @Optional
    Property<Boolean> getParallel();

    @Input
    @Optional
    Property<Boolean> getPreserveOrder();

    @Input
    @Optional
    Property<Boolean> getThreadCount();

    @Override
    default CommandLineArgumentProvider mapToConfigurationParameters() {
        return new SystemPropertiesCommandLineProvider<TestNGConfigurationParameters>(this) {
            @Override
            public Map<String, String> getSystemProperties() {
                Map<String, String> systemProperties = Maps.newHashMap();
                putIfPresent(systemProperties, "testng.allowReturnValues", getAllowReturnValues());
                putIfPresent(systemProperties, "testng.dataProviderThreadCount", getDataProviderThreadCount());
                putIfPresent(systemProperties, "testng.excludedGroups", getExcludedGroups(), property -> String.join(",", Cast.<Iterable<? extends CharSequence>>uncheckedCast(property.get())));
                putIfPresent(systemProperties, "testng.groups", getGroups(), property -> String.join(",", Cast.<Iterable<? extends CharSequence>>uncheckedCast(property.get())));
                putIfPresent(systemProperties, "testng.parallel", getParallel(), property -> property.get().toString().toLowerCase());
                putIfPresent(systemProperties, "testng.preserveOrder", getPreserveOrder());
                putIfPresent(systemProperties, "testng.threadCount", getThreadCount());
                return systemProperties;
            }

            void putIfPresent(Map<String, String> systemProperties, String key, Provider<?> property, Function<Provider<?>, String> mapping) {
                if (property.isPresent()) {
                    systemProperties.put(key, mapping.apply(property));
                }
            }

            void putIfPresent(Map<String, String> systemProperties, String key, Provider<?> property) {
                putIfPresent(systemProperties, key, property, p -> p.get().toString());
            }
        };
    }
}
