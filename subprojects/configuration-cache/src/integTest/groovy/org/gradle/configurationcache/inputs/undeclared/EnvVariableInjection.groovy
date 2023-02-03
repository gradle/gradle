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
        return environmentVariables((key): value)
    }

    static EnvVariableInjection environmentVariables(Map<String, String> variables) {
        return new EnvVariableInjection() {
            @Override
            String getDescription() {
                return "using environment variable"
            }

            @Override
            void setup(AbstractConfigurationCacheIntegrationTest test) {
                HashMap<String, String> environment = new HashMap<>(System.getenv())
                variables.forEach((k, v) -> {
                    if (v != null) {
                        environment.put(k, v)
                    } else {
                        environment.remove(k)
                    }
                })
                test.executer.withEnvironmentVars(environment)
            }
        }
    }

    static EnvVariableInjection unsetEnvironmentVariable(String key) {
        return environmentVariable(key, null)
    }
}
