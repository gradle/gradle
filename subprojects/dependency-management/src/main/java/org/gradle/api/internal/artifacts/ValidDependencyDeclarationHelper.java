/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.GradleException;
import org.gradle.internal.deprecation.DeprecatableConfiguration;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Static util class containing logic to check whether a configuration is allowed to declare dependencies.
 *
 * @since 8.0
 */
public abstract class ValidDependencyDeclarationHelper {
    private ValidDependencyDeclarationHelper() {}

    public static void ensureValidConfigurationForDeclaration(String configurationName, DeprecatableConfiguration conf) {
        ensureValidConfigurationForDeclaration(configurationName, conf.getDeclarationAlternatives());
    }

    public static void ensureValidConfigurationForDeclaration(String configurationName, @Nullable List<String> alternatives) {
        boolean alternativesExist = alternatives != null;
        ensureValidConfigurationForDeclaration(configurationName, !alternativesExist);
    }

    public static void ensureValidConfigurationForDeclaration(String configurationName, boolean isValid) {
        if (!isValid) {
            throw new GradleException("Dependencies can no longer be declared using the `" + configurationName + "` configuration.");
        }
    }
}
