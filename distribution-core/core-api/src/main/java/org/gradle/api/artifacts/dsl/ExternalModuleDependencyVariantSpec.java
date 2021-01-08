/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.artifacts.dsl;

import org.gradle.api.Incubating;

/**
 * The specification of a dependency variant. Some dependencies can be fined tuned
 * to select a particular variant. For example, one might want to select the test
 * fixtures of a target component, or a specific classifier.
 *
 * @since 6.8
 */
@Incubating
public interface ExternalModuleDependencyVariantSpec {
    /**
     * Configures the dependency to select the "platform" variant.
     */
    void platform();

    /**
     * Configures the dependency to select the test fixtures capability.
     */
    void testFixtures();

    /**
     * Configures the classifier of this dependency
     */
    void classifier(String classifier);

    /**
     * Configures the artifact type of this dependency
     */
    void artifactType(String artifactType);

}
