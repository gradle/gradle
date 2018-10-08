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
package org.gradle.internal.component.model;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.capabilities.MutableCapabilitiesMetadata;
import org.gradle.internal.component.external.model.VariantMetadataRules;

import java.util.List;

/**
 * A set of rules provided by the build script author
 * (as {@link Action &lt;? super CapabilitiesMetadata&gt;}
 * that are applied on the capabilities defined in variant/configuration metadata.
 */
public class CapabilitiesRules {
    private final List<VariantMetadataRules.VariantAction<? super MutableCapabilitiesMetadata>> actions = Lists.newLinkedList();


    public void addCapabilitiesAction(VariantMetadataRules.VariantAction<? super MutableCapabilitiesMetadata> action) {
        actions.add(action);
    }

    public CapabilitiesMetadata execute(VariantResolveMetadata variant, MutableCapabilitiesMetadata capabilities) {
        for (VariantMetadataRules.VariantAction<? super MutableCapabilitiesMetadata> action : actions) {
            action.maybeExecute(variant, capabilities);
        }
        return capabilities.asImmutable();
    }
}
