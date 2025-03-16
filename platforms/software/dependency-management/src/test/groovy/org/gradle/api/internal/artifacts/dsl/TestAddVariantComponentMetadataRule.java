/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.attributes.Attribute;

@CacheableRule
public class TestAddVariantComponentMetadataRule implements ComponentMetadataRule {
    @Override
    public void execute(ComponentMetadataContext componentMetadataContext) {
        componentMetadataContext.getDetails().addVariant("test", variantMetadata -> {
            variantMetadata.attributes(attributeContainer -> {
                attributeContainer.attribute(Attribute.of("org.gradle.test", String.class), "test");
            });
        });
    }
}
