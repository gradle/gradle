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

package org.gradle.initialization.properties;

import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Map;

@ServiceScope(Scope.BuildTree.class)
public interface SystemPropertiesInstaller {

    /**
     * Computes the system properties declared by the given Gradle properties and by the start parameter,
     * without applying them to the JVM.
     * <p>
     * Computing and applying are separate steps so that a caller can observe the properties that are about
     * to be installed. The configuration cache needs this to record them as an environment change, in
     * addition to having them applied.
     */
    Map<String, String> systemPropertiesFrom(GradleProperties gradleProperties);

    /**
     * Applies the properties computed by {@link #systemPropertiesFrom} to the JVM.
     */
    void applySystemProperties(Map<String, String> systemProperties);
}
