/*
 * Copyright 2020 the original author or authors.
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

abstract class SystemPropertyInjection extends BuildInputInjection {
    List<String> getGradleArgs() {
        return []
    }

    static List<SystemPropertyInjection> all(String prop, String value) {
        return [
            commandLine(prop, value),
            gradleProperties(prop, value),
            clientJvmArgs(prop, value)
        ]
    }

    static SystemPropertyInjection commandLine(String prop, String value) {
        return new SystemPropertyInjection() {
            @Override
            String getDescription() {
                return "using command-line"
            }

            @Override
            List<String> getGradleArgs() {
                return ["-D${prop}=${value}"]
            }
        }
    }

    static SystemPropertyInjection gradleProperties(String prop, String value) {
        return new SystemPropertyInjection() {
            @Override
            String getDescription() {
                return "using gradle.properties"
            }

            @Override
            void setup(AbstractConfigurationCacheIntegrationTest test) {
                test.file("gradle.properties").text = "systemProp.${prop}=${value}"
            }
        }
    }

    static SystemPropertyInjection clientJvmArgs(String prop, String value) {
        return new SystemPropertyInjection() {
            @Override
            String getDescription() {
                return "using client JVM args"
            }

            @Override
            void setup(AbstractConfigurationCacheIntegrationTest test) {
                test.executer.requireDaemon().requireIsolatedDaemons()
                test.executer.withCommandLineGradleOpts("-D${prop}=${value}")
            }
        }
    }
}
