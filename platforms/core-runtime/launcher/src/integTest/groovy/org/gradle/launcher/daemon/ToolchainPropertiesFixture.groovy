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

package org.gradle.launcher.daemon

import groovy.transform.SelfType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

@SelfType(AbstractIntegrationSpec)
trait ToolchainPropertiesFixture {

    String toolchainPropertyErrorMessageFor(String propertyName, String value) {
        return "Specifying '${propertyName}' as a project property on the command line is no longer supported. " +
            "Instead, specify it as a Gradle property: '-D${propertyName}=${value}'."
    }

    String toolchainPropertyMisMatchErrorFor(String propertyName, String gradlePropertyValue, String projectPropertyValue) {
        return "The Gradle property '${propertyName}' (set to '${gradlePropertyValue}')" +
            " has a different value than the project property '${propertyName}' (set to '${projectPropertyValue}')." +
            " Please set them to the same value or only set the Gradle property."
    }
}
