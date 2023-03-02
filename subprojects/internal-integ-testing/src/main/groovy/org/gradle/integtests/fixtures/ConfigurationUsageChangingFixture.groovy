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
    void expectConsumableChanging(String configurationPath, boolean current) {
        expectChangingUsage(configurationPath, "consumable", current)
    }

    void expectResolvableChanging(String configurationPath, boolean current) {
        expectChangingUsage(configurationPath, "resolvable", current)
    }

    void expectDeclarableAgainstChanging(String configurationPath, boolean current) {
        expectChangingUsage(configurationPath, "declarable against", current)
    }

    void expectChangingUsage(String configurationPath, String usage, boolean current) {
        executer.expectDocumentedDeprecationWarning("Allowed usage is changing for configuration '$configurationPath', $usage was ${!current} and is now $current. Ideally, usage should be fixed upon creation. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Usage should be fixed upon creation. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configurations_allowed_usage")
    }
}
