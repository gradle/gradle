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

package org.gradle.api.plugins.jvm;

import org.gradle.process.CommandLineArgumentProvider;

/**
 * Marker interface for test engine parameters.  Test engines can define their own parameters by extending this interface.
 *
 * @since 8.3
 */
public interface TestEngineConfigurationParameters {
    /**
     * Maps the parameters for this test engine to command line arguments (e.g. system properties) that will be passed to the
     * test task process.
     */
    CommandLineArgumentProvider mapToConfigurationParameters();

    /**
     * Convenience class for test engines that expose no parameters.
     */
    class None implements TestEngineConfigurationParameters {
        public None() {}

        @Override
        public CommandLineArgumentProvider mapToConfigurationParameters() {
            return CommandLineArgumentProvider.NONE;
        }
    }
}
