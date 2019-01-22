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
package org.gradle.internal.component.model;

import org.gradle.internal.component.local.model.LocalConfigurationMetadata;

/**
 * A class which only delegates to another configuration, used by the dependency
 * management engine to recognize that a configuration was selected via variant-aware
 * selection.
 */
public interface SelectedByVariantMatchingConfigurationMetadata extends ConfigurationMetadata {

    static SelectedByVariantMatchingConfigurationMetadata of(ConfigurationMetadata delegate) {
        if (delegate instanceof LocalConfigurationMetadata) {
            return new DefaultSelectedByVariantMatchingLocalConfigurationMetadata(delegate);
        }
        return new DefaultSelectedByVariantMatchingConfigurationMetadata(delegate);
    }

}
