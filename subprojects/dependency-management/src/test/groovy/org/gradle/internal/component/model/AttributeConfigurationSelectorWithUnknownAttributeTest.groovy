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

package org.gradle.internal.component.model

import com.google.common.base.Optional
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.capabilities.CapabilitiesMetadata
import org.gradle.api.internal.artifacts.JavaEcosystemSupport
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.util.AttributeTestUtil
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class AttributeConfigurationSelectorWithUnknownAttributeTest extends Specification {

    private final AttributesSchemaInternal attributesSchema =
        new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())

    private ConfigurationMetadata selected = null
    private ComponentResolveMetadata targetComponent
    private ImmutableAttributes consumerAttributes = ImmutableAttributes.EMPTY

    def setup() {
        JavaEcosystemSupport.configureSchema(attributesSchema, TestUtil.objectFactory())
    }

    def "unknown attribute on all but incompatible candidates does not prevent selection of API with optional attribute"() {
        given: '''
        a component such that:
        * all its library variants have an attribute unknown to the consumer,
        * there are additional API+runtime "feature" variants marked with the optional attribute,
        * and there are non-library variants (as a particular case of variants that the given consumer is incompatible with)
        '''
        component(
            variant("apiElements", libraryAttributes(Usage.JAVA_API, [(UNKNOWN_ATTRIBUTE): "u1"])),
            variant("runtimeElements", libraryAttributes(Usage.JAVA_RUNTIME, [(UNKNOWN_ATTRIBUTE): "u1"])),
            variant("o1ApiElements", libraryAttributes(Usage.JAVA_API, [(OPTIONAL_ATTRIBUTE): "o1", (UNKNOWN_ATTRIBUTE): "u1"])),
            variant("o1RuntimeElements", libraryAttributes(Usage.JAVA_RUNTIME, [(OPTIONAL_ATTRIBUTE): "o1", (UNKNOWN_ATTRIBUTE): "u1"])),
            variant("javadocElements", documentationAttributes("javadoc")),
            variant("sourceElements", documentationAttributes("sources"))
        )

        and: 'a consumer requesting the API variant marked with the optional attribute'
        consumerAttributes(libraryAttributes(Usage.JAVA_API, [(OPTIONAL_ATTRIBUTE): "o1"]))

        when:
        performSelection()

        then: "the selection is successful"
        selected.name == "o1ApiElements"
    }

    def "unknown attribute on all API variants does not prevent selection of API without optional attribute"() {
        given: '''
        a library such that:
        * its API variants have an attribute unknown to the consumer, while runtime ones don't,
        * there are API+runtime "feature" variants marked with the optional attribute
        '''
        component(
            variant("apiElements", libraryAttributes(Usage.JAVA_API, [(UNKNOWN_ATTRIBUTE): "my-api"])),
            variant("runtimeElements", libraryAttributes(Usage.JAVA_RUNTIME)),
            variant("o1ApiElements", libraryAttributes(Usage.JAVA_API, [(OPTIONAL_ATTRIBUTE): "o1", (UNKNOWN_ATTRIBUTE): "my-api"])),
            variant("o1RuntimeElements", libraryAttributes(Usage.JAVA_RUNTIME, [(OPTIONAL_ATTRIBUTE): "o1"])),
        )

        and: 'a consumer requesting the API variant without the optional attribute'
        consumerAttributes(libraryAttributes(Usage.JAVA_API))

        when:
        performSelection()

        then: "the selection is successful"
        selected.name == "apiElements"
    }

    private consumerAttributes(ImmutableAttributes attrs) {
        this.consumerAttributes = attrs
    }

    private void performSelection() {
        selected = AttributeConfigurationSelector.selectConfigurationUsingAttributeMatching(
            consumerAttributes,
            [],
            targetComponent,
            attributesSchema,
            []
        )
    }

    private void component(ConfigurationMetadata... variants) {
        targetComponent = Stub(ComponentResolveMetadata) {
            getModuleVersionId() >> Stub(ModuleVersionIdentifier) {
                getGroup() >> 'org'
                getName() >> 'lib'
                getVersion() >> '1.0'
            }
            getId() >> Stub(ComponentIdentifier) {
                getDisplayName() >> 'org:lib:1.0'
            }
            getVariantsForGraphTraversal() >> Optional.of(
                ImmutableList.copyOf(variants)
            )
            getAttributesSchema() >> attributesSchema
        }
    }

    private ConfigurationMetadata variant(String name, AttributeContainer attributes) {
        Stub(ConfigurationMetadata) {
            getName() >> name
            getAttributes() >> attributes
            getCapabilities() >> Mock(CapabilitiesMetadata) {
                getCapabilities() >> []
            }
        }
    }


    private static final Attribute<String> UNKNOWN_ATTRIBUTE = Attribute.of("com.example.unknown", String)

    /** Simulates "optional" attributes such as 'org.gradle.plugin.api-version' or 'org.gradle.jvm.version' or third-party ones. */
    private static final Attribute<String> OPTIONAL_ATTRIBUTE = Attribute.of("com.example.flavor", String)

    private static ImmutableAttributes documentationAttributes(String docsType) {
        return AttributeTestUtil.attributesTyped(
            (Category.CATEGORY_ATTRIBUTE): AttributeTestUtil.named(Category, Category.DOCUMENTATION),
            (DocsType.DOCS_TYPE_ATTRIBUTE): AttributeTestUtil.named(DocsType, docsType),
            (Usage.USAGE_ATTRIBUTE): AttributeTestUtil.named(Usage, Usage.JAVA_RUNTIME)
        )
    }

    private static ImmutableAttributes libraryAttributes(
        String usage,
        Map<Attribute<?>, Object> others = Collections.emptyMap()
    ) {
        return AttributeTestUtil.attributesTyped(
            [
                (Category.CATEGORY_ATTRIBUTE): AttributeTestUtil.named(Category, Category.LIBRARY),
                (Usage.USAGE_ATTRIBUTE): AttributeTestUtil.named(Usage, usage),
                (TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE): 11,
                (LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE): AttributeTestUtil.named(LibraryElements, LibraryElements.JAR),
            ] << others
        )
    }
}
