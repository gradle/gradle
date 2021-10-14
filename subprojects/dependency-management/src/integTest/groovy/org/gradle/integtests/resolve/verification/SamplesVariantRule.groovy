/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.resolve.verification


import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.VariantMetadata
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.DocsType
import org.gradle.api.model.ObjectFactory

import javax.inject.Inject

@CacheableRule
abstract class SamplesVariantRule implements ComponentMetadataRule {

    @Inject
    abstract ObjectFactory getObjectFactory()

    @Override
    void execute(ComponentMetadataContext ctx) {
        def id = ctx.details.id
        org.gradle.api.attributes.Category category = objectFactory.named(org.gradle.api.attributes.Category, org.gradle.api.attributes.Category.DOCUMENTATION)
        DocsType docsType = objectFactory.named(DocsType, "sample")
        // SAMPLE_SOURCE_CLASSIFIER

        ctx.details.addVariant("sample") { VariantMetadata vm ->
            vm.attributes{ AttributeContainer ac ->
                ac.attribute(org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE, category)
                ac.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, docsType)
            }
            vm.withFiles {
                it.addFile("${id.name}-${id.version}-sample.jar")
            }
        }

    }
}
