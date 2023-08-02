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

package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.attributes.HasConfigurableAttributes;

/**
 * This type is deprecated and will be removed in a future version of Gradle - it exists only to
 * avoid breaking existing plugins.
 *
 * New code should use {@link JvmPluginServices} instead.
 *
 * @see <a href="https://github.com/gradle/gradle/issues/25542">Removal of JvmEcosystemUtilities Issue</a>
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public interface JvmEcosystemUtilities {
    /**
     * Configures a configuration with reasonable defaults to be resolved as a runtime classpath.
     *
     * @param configuration the configuration to be configured
     */
    void configureAsRuntimeClasspath(HasConfigurableAttributes<?> configuration);
}
