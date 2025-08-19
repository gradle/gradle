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

package org.gradle.initialization;


import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Validates the JVM toolchains configuration used by the Gradle Daemon.
 */
@ServiceScope(Scope.BuildSession.class)
public interface JvmToolchainsConfigurationValidator {
    /**
     * Validates that the given property is configured properly.
     */
    void validatePropertyConfiguration(String propertyName);

    /**
     * Validate that all properties related to daemon JVM toolchain discovery are configured properly.
     *
     * Should emit a deprecation warning or error if any of the properties are not set correctly.
     */
    void validateAllPropertiesConfigurationsForDaemonJvmToolchains();
}
