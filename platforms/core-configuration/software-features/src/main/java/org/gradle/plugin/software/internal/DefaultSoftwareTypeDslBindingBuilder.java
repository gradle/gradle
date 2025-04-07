/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugin.software.internal;

import org.gradle.api.Project;
import org.gradle.api.internal.plugins.SoftwareFeatureApplicationContext;
import org.gradle.api.internal.plugins.SoftwareTypeDslBindingBuilder;
import org.gradle.api.internal.plugins.SoftwareTypeTransform;
import org.gradle.util.Path;

public class DefaultSoftwareTypeDslBindingBuilder extends AbstractDslBindingBuilder implements SoftwareTypeDslBindingBuilder {
    @Override
    public <T, U> SoftwareTypeDslBindingBuilder bind(String name, Class<T> dslType, Class<U> buildModelType, SoftwareTypeTransform<T, U> transform) {
        this.path = Path.path(name);
        this.dslType = dslType;
        this.buildModelType = buildModelType;
        this.bindingTargetType = Project.class;
        this.transform = (SoftwareFeatureApplicationContext context, T definition, Object parentDefinition, U buildModel) -> transform.transform(context, definition, buildModel);
        return this;
    }
}
