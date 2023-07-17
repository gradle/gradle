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

package org.gradle.api.attributes;

import org.gradle.api.Named;

/**
 * Represents the Gradle version which this plugin was compiled against. Only artifacts
 * with this attribute will be considered for Gradle API upgrading.
 */
public interface GradleRuntimeVersion extends Named  {
    Attribute<GradleRuntimeVersion> GRADLE_RUNTIME_VERSION_ATTRIBUTE = Attribute.of("org.gradle.runtime.version", GradleRuntimeVersion.class);
}
