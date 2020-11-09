/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.attributes.gradle;

import org.gradle.api.attributes.Attribute;

/**
 * Represents the target version of a Gradle version of a Gradle plugin variant.
 *
 * @since 6.8
 */
public interface TargetGradleVersion {

    /**
     * The minimal target version for a Gradle plugin. A lower Gradle version would not be able to consume it.
     */
    Attribute<String> TARGET_GRADLE_VERSION_ATTRIBUTE = Attribute.of("org.gradle.plugin.version", String.class);
}
