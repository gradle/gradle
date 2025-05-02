/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factory;

import java.util.List;

public interface ConfigurationVariantInternal extends ConfigurationVariant {

    // TODO: Remove this and replace usages with getArtifacts().addAllLater(Provider)
    void artifactsProvider(Factory<List<PublishArtifact>> artifacts);

    void preventFurtherMutation();

    void setDescription(String description);

    DisplayName getDisplayName();

    @Override
    AttributeContainerInternal getAttributes();
}
