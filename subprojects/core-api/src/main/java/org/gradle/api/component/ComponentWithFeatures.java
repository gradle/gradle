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
package org.gradle.api.component;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.specs.Spec;

/**
 * A component which can declare additional variants corresponding to
 * features. When published to Maven POMs, the dependencies of those variants
 * are exposed as optional dependencies. When published to Gradle metadata, the
 * variants are published as is.
 *
 * @since 5.3
 */
@Incubating
public interface ComponentWithFeatures extends SoftwareComponent {
    /**
     * Declares an additional variant to publish, corresponding to an additional feature.
     * @param outgoingConfiguration the configuration corresponding to the variant to use as source of dependencies and artifacts
     * @param spec tell if this outgoing variant of this configuration should be published
     */
    void addFeatureVariantsFromConfiguration(Configuration outgoingConfiguration, Spec<? super ConfigurationVariant> spec);
}
