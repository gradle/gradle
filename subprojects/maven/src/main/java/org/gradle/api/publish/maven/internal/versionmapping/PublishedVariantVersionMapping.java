/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.publish.maven.internal.versionmapping;

import org.gradle.api.Action;
import org.gradle.api.publish.VariantVersionMappingStrategy;
import org.gradle.api.specs.Spec;

class PublishedVariantVersionMapping {
    private final Spec<? super PublishedVariant> spec;
    private final Action<? super VariantVersionMappingStrategy> configurationAction;

    PublishedVariantVersionMapping(Spec<? super PublishedVariant> spec, Action<? super VariantVersionMappingStrategy> configurationAction) {
        this.spec = spec;
        this.configurationAction = configurationAction;
    }

    void applyTo(PublishedVariant variant, VariantVersionMappingStrategy strategy) {
        if (spec.isSatisfiedBy(variant)) {
            configurationAction.execute(strategy);
        }
    }
}
