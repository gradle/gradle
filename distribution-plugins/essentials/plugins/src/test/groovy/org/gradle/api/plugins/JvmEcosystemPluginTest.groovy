/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.internal.artifacts.dsl.ComponentMetadataHandlerInternal
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.component.external.model.JavaEcosystemVariantDerivationStrategy
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class JvmEcosystemPluginTest extends AbstractProjectBuilderSpec {

    def setup() {
        project.pluginManager.apply(JvmEcosystemPlugin)
    }

    def "configures the attributes schema"() {
        when:
        def attrs = project.dependencies.attributesSchema.attributes

        then:
        attrs.containsAll(
            Usage.USAGE_ATTRIBUTE,
            Category.CATEGORY_ATTRIBUTE,
            Bundling.BUNDLING_ATTRIBUTE,
            TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
        )
    }

    def "creates empty source sets container"() {
        given:
        def sourceSets = project.extensions.getByType(SourceSetContainer)

        expect:
        sourceSets.empty
    }

    def "applies the Java derivation strategy"() {
        given:
        def components = (ComponentMetadataHandlerInternal) project.dependencies.components

        expect:
        components.variantDerivationStrategy instanceof JavaEcosystemVariantDerivationStrategy
    }

}
