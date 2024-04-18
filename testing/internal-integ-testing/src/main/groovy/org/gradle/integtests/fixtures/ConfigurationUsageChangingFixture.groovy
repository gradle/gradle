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

package org.gradle.integtests.fixtures

import groovy.transform.SelfType

/**
 * Allows adding methods to the test class that are useful for testing configuration usage changes.
 */
@SelfType(AbstractIntegrationSpec)
trait ConfigurationUsageChangingFixture {
    void expectConsumableChanging(String configurationPath, boolean value) {
        expectChangingUsage(configurationPath, "setCanBeConsumed", value)
    }

    void expectResolvableChanging(String configurationPath, boolean value) {
        expectChangingUsage(configurationPath, "setCanBeResolved", value)
    }

    void expectDeclarableChanging(String configurationPath, boolean value) {
        expectChangingUsage(configurationPath, "setCanBeDeclared", value)
    }

    void expectChangingUsage(String configurationPath, String method, boolean value) {
        executer.expectDocumentedDeprecationWarning("Calling $method($value) on configuration '$configurationPath' has been deprecated. This will fail with an error in Gradle 9.0. This configuration's role was set upon creation and its usage should not be changed. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configurations_allowed_usage")
    }
}
