/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.internal.properties.GradleProperties;

import java.io.File;

public interface IGradlePropertiesLoader {

    String SYSTEM_PROJECT_PROPERTIES_PREFIX = "org.gradle.project.";

    String ENV_PROJECT_PROPERTIES_PREFIX = "ORG_GRADLE_PROJECT_";

    /**
     * Loads the immutable set of Gradle properties.
     *
     * @since 6.2
     */
    GradleProperties loadGradleProperties(File rootDir);
}
