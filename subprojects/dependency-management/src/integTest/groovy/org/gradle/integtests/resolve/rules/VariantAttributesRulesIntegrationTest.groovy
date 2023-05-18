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
package org.gradle.integtests.resolve.rules

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

class VariantAttributesRulesIntegrationTest extends AbstractModuleDependencyResolveTest {
    @Override
    String getTestConfiguration() { variantToTest }

    /**
     * Does the published metadata provide variants with attributes? Eventually all metadata should do that.
     * For Ivy and Maven POM metadata, the variants and attributes should be derived from configurations and scopes.
     */
    boolean getPublishedModulesHaveAttributes() { gradleMetadataPublished }

    String getVariantToTest() {
        if (gradleMetadataPublished || useIvy()) {
            'customVariant'
        } else {
            'compile'
        }
    }

    void withDefaultVariantToTest() {
        repository {
            id('org.test:moduleA:1.0') {
                variant 'customVariant', [format: 'custom']
                dependsOn('org.test:moduleB:1.0')
            }
        }

        buildFile << """
            def testAttribute = Attribute.of("TEST_ATTRIBUTE", String)
            def formatAttribute = Attribute.of('format', String)

            configurations { $variantToTest { attributes { attribute(formatAttribute, 'custom') } } }

            dependencies {
                $variantToTest group: 'org.test', name: 'moduleA', version: '1.0' ${publishedModulesHaveAttributes ? "" : ", configuration: '$variantToTest'"}
            }
        """
    }

    def "can add attributes"() {
        given:
        withDefaultVariantToTest()
        buildFile << """
            class AttributeRule implements ComponentMetadataRule {
                Attribute attribute

                @javax.inject.Inject
                AttributeRule(Attribute attribute) {
                    this.attribute = attribute
                }

                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("$variantToTest") {
                        attributes {
                            attribute(attribute, "custom")
                        }
                    }
                }
            }

            dependencies {
                components {
                    withModule('org.test:moduleB', AttributeRule) {
                        params(formatAttribute)
                    }
                }
            }
        """

        repository {
            id('org.test:moduleB:1.0') {
                variant 'customVariant', [:]
            }
        }

        when:
        repositoryInteractions {
            id('org.test:moduleA:1.0') {
                expectGetMetadata()
                expectGetArtifact()
            }
            id('org.test:moduleB:1.0') {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant") {
                    module("org.test:moduleB:1.0")
                }
            }
        }
    }

    def "can override attributes"() {
        given:
        withDefaultVariantToTest()
        def transitiveSelectedVariant = !gradleMetadataPublished && useIvy()? 'default' : variantToTest
        buildFile << """
            class AttributeRule implements ComponentMetadataRule {
                Attribute attribute

                @javax.inject.Inject
                AttributeRule(Attribute attribute) {
                    this.attribute = attribute
                }

                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("$transitiveSelectedVariant") {
                        attributes {
                            attribute(attribute, "custom")
                        }
                    }
                }
            }

            dependencies {
                components {
                    withModule('org.test:moduleB', AttributeRule) {
                        params(formatAttribute)
                    }
                }
            }
        """

        repository {
            id('org.test:moduleB:1.0') {
                variant('customVariant') {
                    if (GradleMetadataResolveRunner.isGradleMetadataPublished()) {
                        artifact 'variant1'
                    }
                    attribute 'format', 'will be overridden'
                }
            }
        }

        when:
        repositoryInteractions {
            id('org.test:moduleA:1.0') {
                expectGetMetadata()
                expectGetArtifact()
            }
            id('org.test:moduleB:1.0') {
                expectGetMetadata()
                if (GradleMetadataResolveRunner.isGradleMetadataPublished()) {
                    expectGetVariantArtifacts('customVariant')
                } else {
                    expectGetArtifact()
                }
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant") {
                    module("org.test:moduleB:1.0") {
                        String expectedTargetVariant
                        Map<String, String> expectedAttributes
                        if (GradleMetadataResolveRunner.gradleMetadataPublished) {
                            // when Gradle metadata is on, variants used during selection are Gradle defined variants
                            // and here, they do not define any "usage". However, they do define a "status". The selected variant
                            // is target to the metadata rule, which explains we find the "format" attribute here
                            expectedTargetVariant = expectedVariant
                            artifact classifier: 'variant1'
                            expectedAttributes = [format: 'custom', 'org.gradle.status': GradleMetadataResolveRunner.useIvy() ? 'integration' : 'release']
                        } else {
                            if (GradleMetadataResolveRunner.useIvy()) {
                                // Ivy doesn't derive any variant
                                expectedTargetVariant = 'default'
                                expectedAttributes = [format: 'custom', 'org.gradle.status': GradleMetadataResolveRunner.useIvy() ? 'integration' : 'release']
                            } else {
                                // for Maven, we derive variants for compile/runtime. Variants are then used during selection, and are subject
                                // to metadata rules. In this case, we have multiple variants (default, runtime, compile), but only the "compile"
                                // one is target of the rule (see #getVariantToTest())
                                expectedTargetVariant = 'compile'
                                // the format attribute is added by the rule
                                expectedAttributes = [format: 'custom', 'org.gradle.status': GradleMetadataResolveRunner.useIvy() ? 'integration' : 'release']
                                expectedAttributes['org.gradle.usage'] = 'java-api'
                                expectedAttributes['org.gradle.category'] = 'library'
                                expectedAttributes['org.gradle.libraryelements'] = 'jar'
                            }
                        }
                        variant(expectedTargetVariant, expectedAttributes)
                    }
                }
            }
        }
    }

    // This test documents the current behavior. It's not necessarily
    // what we want, but there doesn't seem to be a good use case for mutating
    // artifact attributes
    def "can specify an artifact attribute on a variant to mitigate missing withArtifacts rules"() {
        given:
        withDefaultVariantToTest()
        buildFile << """
            class AttributeRule implements ComponentMetadataRule {
                Attribute attribute

                @javax.inject.Inject
                AttributeRule(Attribute attribute) {
                    this.attribute = attribute
                }

                void execute(ComponentMetadataContext context) {
                    context.details.withVariant("$variantToTest") {
                        attributes {
                            // defines the 'format' attribute with value 'custom' on all variants
                            // which will be inherited by artifacts
                            attribute(attribute, "custom")
                        }
                    }
                }
            }

            dependencies {
                artifactTypes {
                    jar {
                        // declares that the 'jar' artifact type wants a 'format' attribute with value 'custom'
                        // and this is missing from component and variant metadata
                        attributes.attribute(formatAttribute, 'custom')
                    }
                }
                components {
                    withModule('org.test:moduleB', AttributeRule) {
                        params(formatAttribute)
                    }
                }
            }
        """

        repository {
            id('org.test:moduleB:1.0') {
                variant('customVariant') {
                    if (GradleMetadataResolveRunner.isGradleMetadataPublished()) {
                        artifact 'variant1'
                    }
                }
            }
        }

        when:
        repositoryInteractions {
            id('org.test:moduleA:1.0') {
                expectGetMetadata()
                expectGetArtifact()
            }
            id('org.test:moduleB:1.0') {
                expectGetMetadata()
                if (GradleMetadataResolveRunner.isGradleMetadataPublished()) {
                    expectGetVariantArtifacts('customVariant')
                } else {
                    expectGetArtifact()
                }
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant") {
                    module("org.test:moduleB:1.0") {
                        if (GradleMetadataResolveRunner.isGradleMetadataPublished()) {
                            artifact classifier: 'variant1'
                        }
                    }
                }
            }
        }
    }

    def "rule is applied only once"() {
        given:
        withDefaultVariantToTest()
        int invalidCount = 2
        if (GradleMetadataResolveRunner.useMaven() && !GradleMetadataResolveRunner.gradleMetadataPublished) {
            // for Maven with experimental, we use variant aware matching which will (today) involve another round
            // of execution of the rule
            invalidCount++
        }
        buildFile << """
            int cpt
            dependencies {
                components {
                    withModule('org.test:moduleB') {
                        withVariant("$variantToTest") {
                            attributes {
                                if (++cpt == $invalidCount) {
                                    throw new IllegalStateException("rule should only be applied once on variant $variantToTest")
                                }
                            }
                        }
                    }
                }
            }
        """

        repository {
            id('org.test:moduleB:1.0') {
                variant 'customVariant', [format: 'custom']
            }
        }

        when:
        repositoryInteractions {
            id('org.test:moduleA:1.0') {
                expectGetMetadata()
                expectGetArtifact()
            }
            id('org.test:moduleB:1.0') {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant") {
                    module("org.test:moduleB:1.0")
                }
            }
        }
    }

    def "can disambiguate variants to select #selectedVariant"() {
        given:
        withDefaultVariantToTest()
        buildFile << """
            class AttributeRule implements ComponentMetadataRule {
                Attribute attribute

                @javax.inject.Inject
                AttributeRule(Attribute attribute) {
                    this.attribute = attribute
                }

                void execute(ComponentMetadataContext context) {
                    context.details.withVariant('$selectedVariant') {
                        attributes {
                            attribute(attribute, "select")
                        }
                    }
                }
            }

            configurations {
                ${variantToTest}.attributes.attribute(testAttribute, "select")
            }

            dependencies {
                components {
                    withModule('org.test:moduleB', AttributeRule) {
                        params(testAttribute)
                    }
                }
            }
        """

        repository {
            id('org.test:moduleB:1.0') {
                variant('customVariant1') {
                    attribute 'format', 'custom'
                    artifact 'variant1'
                }
                variant('customVariant2') {
                    attribute 'format', 'custom'
                    artifact 'variant2'
                }
            }
        }

        when:
        // @RequiredFeatures not compatible with @Unroll at method level
        if (GradleMetadataResolveRunner.isGradleMetadataPublished()) {
            repositoryInteractions {
                id('org.test:moduleA:1.0') {
                    expectGetMetadata()
                    expectGetArtifact()
                }
                id('org.test:moduleB:1.0') {
                    expectGetMetadata()
                    expectGetVariantArtifacts(selectedVariant)
                }
            }
        }

        then:
        // @RequiredFeatures not compatible with @Unroll at method level
        if (GradleMetadataResolveRunner.isGradleMetadataPublished()) {
            succeeds 'checkDep'

            def expectedVariant = variantToTest
            resolve.expectGraph {
                root(':', ':test:') {
                    module("org.test:moduleA:1.0:$expectedVariant") {
                        module("org.test:moduleB:1.0") {
                            artifact(classifier: (selectedVariant - 'custom').toLowerCase())
                        }
                    }
                }
            }
        }

        where:
        selectedVariant << ['customVariant1', 'customVariant2']
    }

    // published attributes are only available in Gradle metadata
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "published variant metadata can be overwritten"() {
        given:
        repository {
            id('org.test:module:1.0') {
                variant('customVariant1') {
                    attribute 'quality', 'canary'
                }
                variant('customVariant2') {
                    attribute 'quality', 'canary'
                }
            }
        }
        buildFile << """
            def quality = Attribute.of("quality", String)

            class AttributeRule implements ComponentMetadataRule {
                Attribute attribute

                @javax.inject.Inject
                AttributeRule(Attribute attribute) {
                    this.attribute = attribute
                }

                void execute(ComponentMetadataContext context) {
                    context.details.withVariant('customVariant2') {
                       attributes {
                          attribute attribute, 'qa'
                       }
                    }
                }
            }

            configurations {
                ${variantToTest}.attributes.attribute(quality, 'qa')
            }

            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                components {
                    withModule('org.test:module', AttributeRule) {
                        params(quality)
                    }
                }
                $variantToTest 'org.test:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            id('org.test:module:1.0') {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        run ':checkDeps'
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.test:module:1.0:customVariant2')
            }
        }
    }

    // published attributes are only available in Gradle metadata
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "component metadata rules can mutate attributes returned from getAttributes()"() {
        given:
        repository {
            id('org.test:module:1.0') {
                variant('customVariant1') {
                    attribute 'quality', 'canary'
                }
                variant('customVariant2') {
                    attribute 'quality', 'canary'
                }
            }
        }
        buildFile << """
            def quality = Attribute.of("quality", String)

            class AttributeRule implements ComponentMetadataRule {
                Attribute attribute

                @javax.inject.Inject
                AttributeRule(Attribute attribute) {
                    this.attribute = attribute
                }

                void execute(ComponentMetadataContext context) {
                    context.details.withVariant('customVariant2') {
                        getAttributes().attribute(attribute, 'qa')
                    }
                }
            }

            configurations {
                ${variantToTest}.attributes.attribute(quality, 'qa')
            }

            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                components {
                    withModule('org.test:module', AttributeRule) {
                        params(quality)
                    }
                }
                $variantToTest 'org.test:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            id('org.test:module:1.0') {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        run ':checkDeps'
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.test:module:1.0:customVariant2')
            }
        }
    }
}
