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

package org.gradle.internal.component.model

import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.AmbiguousGraphVariantsException
import org.gradle.internal.component.NoMatchingGraphVariantsException
import org.gradle.internal.component.ResolutionFailureHandler
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

import static org.gradle.api.problems.TestProblemsUtil.createTestProblems
import static org.gradle.util.AttributeTestUtil.attributes

class AttributeMatchingGraphArtifactVariantSelectorTest extends Specification {
    private final AttributesSchemaInternal attributesSchema = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())

    private ComponentGraphResolveState targetState
    private ComponentGraphResolveMetadata targetComponent
    private VariantGraphResolveState selected
    private ImmutableAttributes consumerAttributes = ImmutableAttributes.EMPTY
    private List<Capability> requestedCapabilities = []
    private List<IvyArtifactName> artifacts = []
    private ConfigurationGraphResolveState defaultConfiguration

    def "selects a variant when there's no ambiguity"() {
        given:
        component(
            variant("api", attributes('org.gradle.usage': 'java-api')),
            variant("runtime", attributes('org.gradle.usage': 'java-runtime'))
        )

        and:
        consumerAttributes('org.gradle.usage': usage)

        when:
        performSelection()

        then:
        selected.name == expected

        where:
        usage          | expected
        'java-api'     | 'api'
        'java-runtime' | 'runtime'
    }

    def "fails to select a variant when there are more than one candidate"() {
        given:
        component(
            variant("api1", attributes('org.gradle.usage': 'java-api')),
            variant("api2", attributes('org.gradle.usage': 'java-api'))
        )

        and:
        consumerAttributes('org.gradle.usage': 'java-api')

        when:
        performSelection()

        then:
        AmbiguousGraphVariantsException e = thrown()
        failsWith(e, '''The consumer was configured to find attribute 'org.gradle.usage' with value 'java-api'. However we cannot choose between the following variants of org:lib:1.0:
  - api1
  - api2
All of them match the consumer attributes:
  - Variant 'api1' capability org:lib:1.0 declares attribute 'org.gradle.usage' with value 'java-api\'
  - Variant 'api2' capability org:lib:1.0 declares attribute 'org.gradle.usage' with value 'java-api\'''')
    }

    def "fails to select a variant when there no matching candidates"() {
        given:
        component(
            variant("api", attributes('org.gradle.usage': 'java-api')),
            variant("runtime", attributes('org.gradle.usage': 'java-runtime'))
        )

        and:
        consumerAttributes('org.gradle.usage': 'cplusplus-headers')

        when:
        performSelection()

        then:
        NoMatchingGraphVariantsException e = thrown()
        failsWith(e, '''No matching variant of org:lib:1.0 was found. The consumer was configured to find attribute 'org.gradle.usage' with value 'cplusplus-headers' but:
  - Variant 'api' capability org:lib:1.0:
      - Incompatible because this component declares attribute 'org.gradle.usage' with value 'java-api' and the consumer needed attribute 'org.gradle.usage' with value 'cplusplus-headers\'
  - Variant 'runtime' capability org:lib:1.0:
      - Incompatible because this component declares attribute 'org.gradle.usage' with value 'java-runtime' and the consumer needed attribute 'org.gradle.usage' with value 'cplusplus-headers\'''')
    }

    def "falls back to the default configuration if variant aware resolution is not supported"() {
        given:
        component(Optional.empty())

        and:
        defaultConfiguration()

        when:
        performSelection()

        then:
        selected.name == "default"
    }

    def "fails to fall back to the default configuration if the attributes do not match"() {
        given:
        component(Optional.empty())

        and:
        defaultConfiguration(attributes('org.gradle.usage': 'java-api'))
        consumerAttributes('org.gradle.usage': 'cplusplus-headers')

        when:
        performSelection()

        then:
        NoMatchingGraphVariantsException e = thrown()
        failsWith(e, '''No matching configuration of org:lib:1.0 was found. The consumer was configured to find attribute 'org.gradle.usage' with value 'cplusplus-headers' but:
  - None of the consumable configurations have attributes.''')
    }

    def "can select a variant thanks to the capabilities"() {
        given:
        component(
            variant("api1", attributes('org.gradle.usage': 'java-api'), capability('first')),
            variant("api2", attributes('org.gradle.usage': 'java-api'), capability('second'))
        )

        and:
        consumerAttributes('org.gradle.usage': 'java-api')
        requestCapability capability(cap)

        when:
        performSelection()

        then:
        selected.name == expected

        where:
        cap      | expected
        'first'  | 'api1'
        'second' | 'api2'
    }

    def "can select a variant thanks to the implicit capability"() {
        given:
        component(
            variant("api1", attributes('org.gradle.usage': 'java-api')),
            variant("api2", attributes('org.gradle.usage': 'java-api'), capability('second'))
        )

        and:
        consumerAttributes('org.gradle.usage': 'java-api')

        if (cap) {
            requestCapability capability(cap)
        }

        when:
        performSelection()

        then:
        selected.name == expected

        where:
        cap      | expected
        null     | 'api1'
        'lib'    | 'api1'
        'second' | 'api2'
    }


    def "fails if more than one variant provides the implicit capability"() {
        given:
        component(
            variant("api1", attributes('org.gradle.usage': 'java-api')),
            variant("api2", attributes('org.gradle.usage': 'java-api')),
            variant("api3", attributes('org.gradle.usage': 'java-api'), capability('lib'), capability('second'))
        )

        and:
        consumerAttributes('org.gradle.usage': 'java-api')

        requestCapability capability('lib')

        when:
        performSelection()

        then:
        AmbiguousGraphVariantsException e = thrown()
        failsWith(e, '''The consumer was configured to find attribute 'org.gradle.usage' with value 'java-api'. However we cannot choose between the following variants of org:lib:1.0:
  - api1
  - api2
  - api3
All of them match the consumer attributes:
  - Variant 'api1' capability org:lib:1.0 declares attribute 'org.gradle.usage' with value 'java-api\'
  - Variant 'api2' capability org:lib:1.0 declares attribute 'org.gradle.usage' with value 'java-api\'
  - Variant 'api3' capabilities org:lib:1.0 and org:second:1.0 declares attribute 'org.gradle.usage' with value 'java-api\'''')
        /*
        There were multiple variants of `org:lib:1.0` which provided what this configuration was looking for:
        All api1, api2 and api3 are compatible with what this configuration is looking for (a Java API).
         */
        /*
        Caused by: org.gradle.internal.resolve.ModuleVersionResolveException: Could not resolve com.squareup.okio:okio:2.4.1.
             Required by:
                 project :telemetry-definitions > com.squareup.okhttp3:okhttp:4.3.1
             Caused by: org.gradle.internal.component.AmbiguousGraphVariantsException: Cannot choose between the following variants of com.squareup.okio:okio:2.4.3:
               - jvm-api
               - jvm-runtime
               - metadata-api
             All of them match the consumer attributes:
               - Variant 'jvm-api' capability com.squareup.okio:okio:2.4.3:
                   - Unmatched attributes:
                       - Found org.gradle.status 'release' but wasn't required.
                       - Found org.gradle.usage 'java-api' but wasn't required.
                       - Found org.jetbrains.kotlin.platform.type 'jvm' but wasn't required.
                   - Compatible attribute:
                       - Required org.gradle.libraryelements 'resources' and found value 'jar'.
               - Variant 'jvm-runtime' capability com.squareup.okio:okio:2.4.3:
                   - Unmatched attributes:
                       - Found org.gradle.status 'release' but wasn't required.
                       - Found org.gradle.usage 'java-runtime' but wasn't required.
                       - Found org.jetbrains.kotlin.platform.type 'jvm' but wasn't required.
                   - Compatible attribute:
                       - Required org.gradle.libraryelements 'resources' and found value 'jar'.
               - Variant 'metadata-api' capability com.squareup.okio:okio:2.4.3:
                   - Unmatched attributes:
                       - Required org.gradle.libraryelements 'resources' but no value provided.
                       - Found org.gradle.status 'release' but wasn't required.
                       - Found org.gradle.usage 'kotlin-api' but wasn't required.
                       - Found org.jetbrains.kotlin.platform.type 'common' but wasn't required.

         */
        /*
        2020-02-04T21:53:19.7995006Z Caused by: org.gradle.internal.resolve.ModuleVersionResolveException: Could not resolve project :carbonite:carbonite.
             Required by:
                 project :carbonite:carbonite-compiler
             Caused by: org.gradle.internal.component.AmbiguousGraphVariantsException: Cannot choose between the following variants of project :carbonite:carbonite:
               - compile
               - default
               - runtime
               - testCompile
               - testRuntime
             All of them match the consumer attributes:
               - Variant 'compile' capability slack-android-ng.carbonite:carbonite:unspecified:
                   - Unmatched attributes:
                       - Required org.gradle.dependency.bundling 'external' but no value provided.
                       - Required org.gradle.jvm.version '8' but no value provided.
                       - Required org.gradle.libraryelements 'classes' but no value provided.
                       - Required org.gradle.usage 'java-api' but no value provided.
                       - Found org.jetbrains.kotlin.localToProject 'local to :carbonite:carbonite' but wasn't required.
                   - Compatible attribute:
                       - Provides org.jetbrains.kotlin.platform.type 'jvm'
               - Variant 'default' capability slack-android-ng.carbonite:carbonite:unspecified:
                   - Unmatched attributes:
                       - Required org.gradle.dependency.bundling 'external' but no value provided.
                       - Required org.gradle.jvm.version '8' but no value provided.
                       - Required org.gradle.libraryelements 'classes' but no value provided.
                       - Required org.gradle.usage 'java-api' but no value provided.
                       - Found org.jetbrains.kotlin.localToProject 'local to :carbonite:carbonite' but wasn't required.
                   - Compatible attribute:
                       - Provides org.jetbrains.kotlin.platform.type 'jvm'
               - Variant 'runtime' capability slack-android-ng.carbonite:carbonite:unspecified:
                   - Unmatched attributes:
                       - Required org.gradle.dependency.bundling 'external' but no value provided.
                       - Required org.gradle.jvm.version '8' but no value provided.
                       - Required org.gradle.libraryelements 'classes' but no value provided.
                       - Required org.gradle.usage 'java-api' but no value provided.
                       - Found org.jetbrains.kotlin.localToProject 'local to :carbonite:carbonite' but wasn't required.
                   - Compatible attribute:
                       - Provides org.jetbrains.kotlin.platform.type 'jvm'
               - Variant 'testCompile' capability slack-android-ng.carbonite:carbonite:unspecified:
                   - Unmatched attributes:
                       - Required org.gradle.dependency.bundling 'external' but no value provided.
                       - Required org.gradle.jvm.version '8' but no value provided.
                       - Required org.gradle.libraryelements 'classes' but no value provided.
                       - Required org.gradle.usage 'java-api' but no value provided.
                       - Found org.jetbrains.kotlin.localToProject 'local to :carbonite:carbonite' but wasn't required.
                   - Compatible attribute:
                       - Provides org.jetbrains.kotlin.platform.type 'jvm'
               - Variant 'testRuntime' capability slack-android-ng.carbonite:carbonite:unspecified:
                   - Unmatched attributes:
                       - Required org.gradle.dependency.bundling 'external' but no value provided.
                       - Required org.gradle.jvm.version '8' but no value provided.
                       - Required org.gradle.libraryelements 'classes' but no value provided.
                       - Required org.gradle.usage 'java-api' but no value provided.
                       - Found org.jetbrains.kotlin.localToProject 'local to :carbonite:carbonite' but wasn't required.
                   - Compatible attribute:
                       - Provides org.jetbrains.kotlin.platform.type 'jvm'
         */
    }

    def "should select the variant which matches the most attributes"() {
        given:
        component(
            variant("first", attributes('org.gradle.usage': 'java-api')),
            variant("second", attributes('org.gradle.usage': 'java-api', 'other': true))
        )

        and:
        consumerAttributes('org.gradle.usage': 'java-api')

        when:
        performSelection()

        then:
        selected.name == 'first'
    }

    def "should not select variant whenever 2 variants provide different extra attributes"() {
        given:
        component(
            variant("first", attributes('org.gradle.usage': 'java-api', extra: 'v1')),
            variant("second", attributes('org.gradle.usage': 'java-api', other: true))
        )

        and:
        consumerAttributes('org.gradle.usage': 'java-api')

        when:
        performSelection()

        then:
        AmbiguousGraphVariantsException e = thrown()
        failsWith(e, '''The consumer was configured to find attribute 'org.gradle.usage' with value 'java-api'. However we cannot choose between the following variants of org:lib:1.0:
  - first
  - second
All of them match the consumer attributes:
  - Variant 'first' capability org:lib:1.0 declares attribute 'org.gradle.usage' with value 'java-api':
      - Unmatched attribute:
          - Provides extra 'v1' but the consumer didn't ask for it
  - Variant 'second' capability org:lib:1.0 declares attribute 'org.gradle.usage' with value 'java-api':
      - Unmatched attribute:
          - Provides other 'true' but the consumer didn't ask for it''')

    }

    def "should select the variant matching most closely the requested attributes when they provide more than one extra attributes"() {
        given:
        component(
            variant("first", attributes('org.gradle.usage': 'java-api', extra: 'v1', other: true)),
            variant("second", attributes('org.gradle.usage': 'java-api', other: true))
        )

        and:
        consumerAttributes('org.gradle.usage': 'java-api')

        when:
        performSelection()

        then:
        selected.name == 'second'

    }


    def "should select the variant which matches the most attributes and producer doesn't have requested value"() {
        given:
        component(
            variant("first", attributes('org.gradle.usage': 'java-api')),
            variant("second", attributes('org.gradle.usage': 'java-runtime', 'other': true))
        )
        attributesSchema.attribute(Attribute.of("org.gradle.usage", String)) {
            it.compatibilityRules.add(UsageCompatibilityRule)
        }
        and:
        consumerAttributes('org.gradle.usage': 'java-api', 'other': true)

        when:
        performSelection()

        then:
        selected.name == 'second'
    }

    def "should select the variant which matches the requested classifier"() {
        def variant1 = variantWithArtifacts("first", artifact('foo', null))
        def variant2 = variantWithArtifacts("second", artifact('foo', 'classy'))

        given:
        component(variant1, variant2)

        and:
        requireArtifact('foo', 'jar', 'jar', 'classy')

        when:
        performSelection()

        then:
        selected.name == 'second'
    }

    def "should select the variant with the most exact capability match"() {
        given:
        component(
            variant('A', attributes('org.gradle.usage': 'java-api', 'other': 'c'), capability('first'), capability('second')),
            variant('B', attributes('org.gradle.usage': 'java-api', 'other': 'c'), capability('first'))
        )

        and:
        consumerAttributes('org.gradle.usage': 'java-api', 'other': 'c')
        requestCapability capability('first')

        when:
        performSelection()

        then:
        selected.name == 'B' // B matches best: capabilities match exactly, attributes match exactly
    }

    def "should select the variant with the most exact match in case of ambiguity between attributes and capabilities"() {
        given:
        attributesSchema.attribute(Attribute.of('other', String)) {
            it.compatibilityRules.add(AllCompatibilityRule)
        }
        component(
            variant('A', attributes('org.gradle.usage': 'java-api', 'other': 'a'), capability('first'), capability('second')),
            variant('B', attributes('org.gradle.usage': 'java-api', 'other': 'b'), capability('first')),
            variant('C', attributes('org.gradle.usage': 'java-api'), capability('first'))
        )

        and:
        consumerAttributes('org.gradle.usage': 'java-api', 'other': 'c')
        requestCapability capability('first')

        when:
        performSelection()

        then:
        selected.name == 'B' // B matches best: capabilities match exactly, attribute 'other' was requested and a compatible value is provided (variant C does not provide any value for 'other')
    }

    private void performSelection() {
        GraphVariantSelector variantSelector = new GraphVariantSelector(new ResolutionFailureHandler(createTestProblems(), Mock(DocumentationRegistry)))
        selected = variantSelector.selectVariants(
            consumerAttributes,
            requestedCapabilities,
            targetState,
            attributesSchema,
            artifacts
        ).variants[0]
    }

    private void requireArtifact(String name = "foo", String type = "jar", String ext = "jar", String classifier = null) {
        artifacts << new DefaultIvyArtifactName(name, type, ext, classifier)
    }

    private ModuleComponentArtifactMetadata artifact(String name, String classifier) {
        Stub(ModuleComponentArtifactMetadata) {
            getId() >> Stub(ModuleComponentArtifactIdentifier)
            getName() >> Stub(IvyArtifactName) {
                getName() >> name
                getType() >> "jar"
                getExtension() >> "jar"
                getClassifier() >> classifier
            }
        }
    }

    private consumerAttributes(Map<String, Object> attrs) {
        this.consumerAttributes = attributes(attrs)
    }

    private void requestCapability(Capability c) {
        requestedCapabilities << c
    }

    private void defaultConfiguration(ImmutableAttributes attrs = attributes([:])) {
        def variant = Stub(VariantGraphResolveState) {
            getName() >> 'default'
        }
        def metadata = Stub(ConfigurationGraphResolveMetadata) {
            isCanBeConsumed() >> true
        }
        defaultConfiguration = Stub(ConfigurationGraphResolveState) {
            getName() >> 'default'
            getAttributes() >> attrs
            getMetadata() >> metadata
            asVariant() >> variant
        }
    }

    private void component(VariantGraphResolveState... variants) {
        component(Optional.of(ImmutableList.copyOf(variants)))
    }

    private void component(Optional<List<VariantGraphResolveState>> variants) {
        targetComponent = Stub(ComponentGraphResolveMetadata) {
            getModuleVersionId() >> Stub(ModuleVersionIdentifier) {
                getGroup() >> 'org'
                getName() >> 'lib'
                getVersion() >> '1.0'
            }
            getId() >> Stub(ComponentIdentifier) {
                getDisplayName() >> 'org:lib:1.0'
            }
            getAttributesSchema() >> attributesSchema
        }
        def candidates = Stub(GraphSelectionCandidates) {
            isUseVariants() >> { variants.isPresent() }
            getVariants() >> { variants.get() }
            getLegacyConfiguration() >> { defaultConfiguration }
        }
        targetState = Stub(ComponentGraphResolveState) {
            getMetadata() >> targetComponent
            getCandidatesForGraphVariantSelection() >> candidates
        }
    }

    private VariantGraphResolveState variant(String name, ImmutableAttributes attributes, Capability... capabilities) {
        def metadata = Stub(VariantGraphResolveMetadata) {
            getName() >> name
            getAttributes() >> attributes
            getCapabilities() >> ImmutableCapabilities.of(Lists.newArrayList(capabilities))
        }
        return Stub(VariantGraphResolveState) {
            getName() >> name
            getAttributes() >> attributes
            getCapabilities() >> ImmutableCapabilities.of(Lists.newArrayList(capabilities))
            getMetadata() >> metadata
        }
    }

    private VariantGraphResolveState variantWithArtifacts(String name, ModuleComponentArtifactMetadata artifact) {
        def metadata = Stub(VariantGraphResolveMetadata) {
            getName() >> name
            getAttributes() >> ImmutableAttributes.EMPTY
            getCapabilities() >> ImmutableCapabilities.EMPTY
        }
        def artifactMetadata = Stub(VariantArtifactGraphResolveMetadata) {
            getArtifacts() >> ImmutableList.of(artifact)
        }
        return Stub(VariantGraphResolveState) {
            getName() >> name
            getAttributes() >> ImmutableAttributes.EMPTY
            getCapabilities() >> ImmutableCapabilities.EMPTY
            getMetadata() >> metadata
            resolveArtifacts() >> artifactMetadata
        }
    }

    private Capability capability(String group, String name, String version = '1.0') {
        Stub(Capability) {
            getGroup() >> group
            getName() >> name
            getVersion() >> version
        }
    }

    private Capability capability(String name) {
        capability('org', name)
    }

    private void failsWith(Throwable e, String message) {
        String actualMessage = TextUtil.normaliseLineSeparators(e.message)
        String expectedMessage = TextUtil.normaliseLineSeparators(message)
        assert actualMessage == expectedMessage
    }

    private static class UsageCompatibilityRule implements AttributeCompatibilityRule<String> {

        @Override
        void execute(CompatibilityCheckDetails<String> details) {
            if (details.consumerValue == 'java-api' && details.producerValue == 'java-runtime') {
                details.compatible()
            }
        }
    }

    private static class AllCompatibilityRule implements AttributeCompatibilityRule<String> {

        @Override
        void execute(CompatibilityCheckDetails<String> details) {
            details.compatible()
        }
    }
}
