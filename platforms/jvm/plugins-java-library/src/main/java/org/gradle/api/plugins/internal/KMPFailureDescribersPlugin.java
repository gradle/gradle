/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.plugins.internal;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.resolution.failure.type.IncompatibleGraphVariantFailure;

/**
 * Attempt at implementing a failure describer based on <a href="https://github.com/dsavvinov/kmp-var-failure-showcase">the idea here</a>.
 *
 * This lives in the `plugins-java-library` solely for testing convenience.
 */
public abstract class KMPFailureDescribersPlugin implements Plugin<Project> {
    @Override
    public void apply(Project target) {
        configureSchema(target);
    }

    private void configureSchema(Project project) {
        // TODO: Need to expose addFailureDescriber on the public AttributesSchema API to make this something the Kotlin plugin can do
        AttributesSchemaInternal attributesSchema = (AttributesSchemaInternal) project.getDependencies().getAttributesSchema();
        attributesSchema.addFailureDescriber(IncompatibleGraphVariantFailure.class, KMPFailureDescriber.class);
    }
}
