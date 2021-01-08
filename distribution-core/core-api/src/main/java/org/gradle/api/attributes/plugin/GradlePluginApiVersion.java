/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.attributes.plugin;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

/**
 * Represents a version of the minimal version of the Gradle API required by (variant of) a Gradle plugin.
 *
 * @since 7.0
 */
@Incubating
public interface GradlePluginApiVersion extends Named {

    /**
     * Minimal Gradle version required. See {@link org.gradle.util.GradleVersion} for supported values.
     */
    Attribute<GradlePluginApiVersion> GRADLE_PLUGIN_API_VERSION_ATTRIBUTE = Attribute.of("org.gradle.plugin.api-version", GradlePluginApiVersion.class);
}
