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

package org.gradle.api.testing.engines.jupiter;

import com.google.common.collect.Maps;
import org.gradle.api.plugins.jvm.TestEngineConfigurationParameters;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.SystemPropertiesCommandLineProvider;

import java.util.Map;

/**
 * Defines the user-configurable configuration parameters of the JUnit Jupiter test engine.
 */
public interface JUnitJupiterConfigurationParameters extends TestEngineConfigurationParameters {
    /**
     * Enumeration of test instance lifecycle modes.
     */
    enum Lifecycle { PER_CLASS, PER_METHOD }

    /**
     * The lifecycle mode to use for test instances.
     *
     * @see <a href="https://junit.org/junit5/docs/current/api/org.junit.jupiter.api/org/junit/jupiter/api/TestInstance.Lifecycle.html">TestInstance.Lifecycle</a>
     */
    @Input
    @Optional
    Property<Lifecycle> getLifecycle();

    /**
     * Whether to enable the automatic detection of test extensions.
     */
    @Input
    @Optional
    Property<Boolean> getAutoDetectionEnabled();

    /**
     * Comma-separated list of patterns for conditions to deactivate.
     */
    @Input
    @Optional
    Property<String> getDeactivatedConditions();

    /**
     * The fully qualified class name of the display name generator to use.
     *
     * @see <a href="https://junit.org/junit5/docs/current/api/org.junit.jupiter.api/org/junit/jupiter/api/DisplayNameGenerator.html">DisplayNameGenerator</a>
     */
    @Input
    @Optional
    Property<String> getDisplayNameGenerator();

    @Override
    default CommandLineArgumentProvider mapToConfigurationParameters() {
        return new SystemPropertiesCommandLineProvider<JUnitJupiterConfigurationParameters>(this) {
            @Override
            public Map<String, String> getSystemProperties() {
                Map<String, String> parameters = Maps.newHashMap();
                if (getLifecycle().isPresent()) {
                    parameters.put("junit.jupiter.testinstance.lifecycle.default", getLifecycle().get().name().toLowerCase());
                }
                if (getAutoDetectionEnabled().isPresent()) {
                    parameters.put("junit.jupiter.extensions.autodetection.enabled", getAutoDetectionEnabled().get().toString());
                }
                if (getDeactivatedConditions().isPresent()) {
                    parameters.put("junit.jupiter.conditions.deactivate", getDeactivatedConditions().get());
                }
                if (getDisplayNameGenerator().isPresent()) {
                    parameters.put("junit.jupiter.displayname.generator.default", getDisplayNameGenerator().get());
                }
                return parameters;
            }
        };
    }
}
