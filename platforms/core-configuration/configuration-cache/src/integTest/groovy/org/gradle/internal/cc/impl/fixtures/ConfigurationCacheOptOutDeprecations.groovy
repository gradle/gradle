/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl.fixtures

import groovy.transform.SelfType
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.HasGradleExecutor

/**
 * Apply this trait to tests that set one of the deprecated temporary Configuration Cache opt-out
 * properties and therefore expect the corresponding deprecation warning.
 */
@SelfType(HasGradleExecutor)
trait ConfigurationCacheOptOutDeprecations {

    void expectDeprecatedOptOutWarning(String propertyName) {
        executer.expectDocumentedDeprecationWarning(
            "The '$propertyName' Gradle property has been deprecated. " +
                "This is scheduled to be removed in Gradle 11. " +
                "${adviceFor(propertyName)} " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_configuration_cache_opt_out_properties"
        )
    }

    private static String adviceFor(String propertyName) {
        switch (propertyName) {
            case StartParameterBuildOptions.ConfigurationCacheIgnoredFileSystemCheckInputs.PROPERTY_NAME:
                return "Remove the property and fix the build logic or plugins that perform file system checks during configuration, " +
                    "so that they no longer cause unnecessary Configuration Cache invalidation."
            case StartParameterBuildOptions.ConfigurationCacheIgnoreInputsDuringStore.PROPERTY_NAME:
                return "Remove the property and fix the build logic or plugins that read the build environment while the task graph is being serialized, " +
                    "so that they no longer cause unnecessary Configuration Cache invalidation."
            case StartParameterBuildOptions.ConfigurationCacheIgnoreUnsupportedBuildEventsListeners.PROPERTY_NAME:
                return "Remove the property and convert the build event listeners into build services."
            case StartParameterBuildOptions.ConfigurationCacheSkipTaskLoggingListenersSerialization.PROPERTY_NAME:
                return "Remove the property and fix the serialization issues of the task output listeners registered during configuration."
            default:
                throw new IllegalArgumentException("Unknown Configuration Cache opt-out property: $propertyName")
        }
    }
}
