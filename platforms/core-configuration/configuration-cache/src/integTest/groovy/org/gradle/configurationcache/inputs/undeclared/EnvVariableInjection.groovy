/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.inputs.undeclared

import org.gradle.configurationcache.AbstractConfigurationCacheIntegrationTest

abstract class EnvVariableInjection extends BuildInputInjection {
    static EnvVariableInjection environmentVariable(String key, String value) {
        return environmentVariables((key): Objects.requireNonNull(value))
    }

    static EnvVariableInjection environmentVariables(Map<String, String> variables) {
        return new EnvVariableInjection() {
            @Override
            String getDescription() {
                return "using environment variable"
            }

            @Override
            void setup(AbstractConfigurationCacheIntegrationTest test) {
                test.executer.withEnvironmentVars(variables)
            }
        }
    }

    static void checkEnvironmentVariableUnset(String key) {
        // We can't "unset" the variable with the API executer provides, but it isn't necessary, thanks to the filtering in
        // build-logic/jvm/src/main/kotlin/gradlebuild/propagated-env-variables.kt
        if (System.getenv().containsKey(key)) {
            throw new IllegalStateException("Environment variable $key is present for this process and may affect tests")
        }
    }
}
