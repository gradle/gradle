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
package org.gradle.integtests.resolve

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.test.fixtures.server.http.IvyHttpModule
import spock.lang.Unroll

class ComponentAttributesRulesIntegrationTest extends AbstractModuleDependencyResolveTest {

    @Unroll("#outcome if attribute is #mutation via component metadata rule")
    def "check that attribute rules modify the result of dependency resolution"() {
        given:
        repository {
            'org.test:module:1.0'()
        }
        buildFile << """
            def quality = Attribute.of("quality", String)
            configurations {
                conf.attributes.attribute(quality, 'qa')
            }

            class AttributeRule implements ComponentMetadataRule {
                Attribute targetAttribute

                @javax.inject.Inject
                public AttributeRule(Attribute attribute) {
                    targetAttribute = attribute
                }

                public void execute(ComponentMetadataContext context) {
                    context.details.attributes {
                        attribute targetAttribute, ${fixApplied ? '"qa"' : '"canary"'}
                    }
                }
            }
            
            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                components {
                    withModule('org.test:module', AttributeRule, {
                        params(quality)
                    })
                }
                conf 'org.test:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org.test:module:1.0' {
                expectGetMetadata()
                if (fixApplied) {
                    expectGetArtifact()
                }
            }
        }

        then:
        if (fixApplied) {
            run ':checkDeps'
            resolve.expectGraph {
                root(":", ":test:") {
                    module('org.test:module:1.0')
                }
            }
        } else {
            fails ':checkDeps'
            failure.assertHasCause("Unable to find a matching ${variantTerm()} of org.test:module:1.0:")
            failure.assertThatCause(containsNormalizedString("Required quality 'qa' and found incompatible value 'canary'"))
        }

        where:
        fixApplied << [false, true]

        // for description of the test
        outcome << ['fails', 'succeeds']
        mutation << ['not added', 'added']
    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    @Unroll
    def "variant attributes take precedence over component attributes (component level = #componentLevel)"() {
        given:
        repository {
            'org.test:module:1.0'()
        }
        buildFile << """
            def usage = Attribute.of('org.gradle.usage', String)

            class AttributeRule implements ComponentMetadataRule {
                Attribute targetAttribute

                @javax.inject.Inject
                public AttributeRule(Attribute attribute) {
                    targetAttribute = attribute
                }

                public void execute(ComponentMetadataContext context) {
                    if ($componentLevel) {
                        context.details.attributes { attribute targetAttribute, 'unknown' }
                    } else {
                        context.details.withVariant('api') { attributes { attribute targetAttribute, 'unknownApiVariant' } }
                        context.details.withVariant('runtime') { attributes { attribute targetAttribute, 'unknownRuntimeVariant' } }
                    }
                }
            }

            dependencies {
                components {
                    withModule('org.test:module', AttributeRule, {
                        params(usage)
                    })
                }
                conf 'org.test:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org.test:module:1.0' {
                expectGetMetadata()
                if (componentLevel) {
                    expectGetArtifact()
                }
            }
        }

        then:
        if (componentLevel) {
            run ':checkDeps'
            resolve.expectGraph {
                root(":", ":test:") {
                    module('org.test:module:1.0')
                }
            }
        } else {
            fails ':checkDeps'
            failure.assertHasCause("Cannot choose between the following variants of org.test:module:1.0:")
            failure.assertThatCause(containsNormalizedString("Found org.gradle.usage 'unknownApiVariant' but wasn't required"))
            failure.assertThatCause(containsNormalizedString("Found org.gradle.usage 'unknownRuntimeVariant' but wasn't required"))
        }

        where:
        componentLevel << [true, false]
    }

    def "can use a component metadata rule to infer quality attribute"() {
        given:
        repository {
            'org.test:module:1.0'()
            'org.test:module:1.1'()
            'org.test:module:1.2'()
        }
        buildFile << """
            def quality = Attribute.of("quality", String)
            configurations {
                conf {
                   attributes.attribute(quality, 'qa')
                }
            }
            
            class AttributeRule implements ComponentMetadataRule {
                Attribute targetAttribute

                @javax.inject.Inject
                public AttributeRule(Attribute attribute) {
                    targetAttribute = attribute
                }

                public void execute(ComponentMetadataContext context) {
                        context.details.attributes {
                            attribute targetAttribute, context.details.id.version=='1.1' ? 'qa' : 'low'
                        }
                }
            }

            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                components {
                    withModule('org.test:module', AttributeRule, {
                        params(quality)
                    })
                }
                conf 'org.test:module:[1.0,2.0)'
            }
        """

        when:
        repositoryInteractions {
            'org.test:module' {
                expectVersionListing()
            }
            'org.test:module:1.2' {
                expectGetMetadata()
            }
            'org.test:module:1.1' {
                expectResolve()
            }
        }

        then:
        run ':checkDeps'
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org.test:module:[1.0,2.0)', 'org.test:module:1.1')
            }
        }

    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    @Unroll
    def "published component metadata can be overwritten (fix applied = #fixApplied)"() {
        given:
        repository {
            'org.test:module:1.0' {
                attribute 'quality', 'canary'
            }
        }
        buildFile << """
            def quality = Attribute.of("quality", String)
            
            configurations {
                conf.attributes.attribute(quality, 'qa')
            }
            
            class AttributeRule implements ComponentMetadataRule {
                Attribute targetAttribute

                @javax.inject.Inject
                public AttributeRule(Attribute attribute) {
                    targetAttribute = attribute
                }

                public void execute(ComponentMetadataContext context) {
                    if ($fixApplied) {
                       context.details.attributes {
                          attribute targetAttribute, 'qa'
                       }
                    }
                }
            }

            dependencies {
                attributesSchema {
                    attribute(quality)
                }

                components {
                    withModule('org.test:module', AttributeRule, {
                        params(quality)
                    })
                }
                conf 'org.test:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org.test:module:1.0' {
                expectGetMetadata()
                if (fixApplied) {
                    expectGetArtifact()
                }
            }
        }

        then:
        if (fixApplied) {
            run ':checkDeps'
            resolve.expectGraph {
                root(":", ":test:") {
                    module('org.test:module:1.0')
                }
            }
        } else {
            fails ':checkDeps'
            failure.assertHasCause("Unable to find a matching variant of org.test:module:1.0:")
            failure.assertThatCause(containsNormalizedString("Required quality 'qa' and found incompatible value 'canary'"))
        }

        where:
        fixApplied << [false, true]
    }

    def "can add attributes to variants with existing usage attribute"() {
        given:
        repository {
            'org.test:module:1.0'()
        }
        buildFile << """
            def quality = Attribute.of("quality", String)
            
            configurations {
                conf.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_API))
                conf.attributes.attribute(quality, 'qa')
            }
            
            class AttributeRule implements ComponentMetadataRule {
                Attribute targetAttribute

                @javax.inject.Inject
                public AttributeRule(Attribute attribute) {
                    targetAttribute = attribute
                }

                public void execute(ComponentMetadataContext context) {
                   context.details.allVariants {
                       attributes {
                          attribute targetAttribute, 'qa'
                       }
                   }
                }
            }

            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                components {
                    withModule('org.test:module', AttributeRule, {
                        params(quality)
                    })
                }
                conf 'org.test:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org.test:module:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        run ':checkDeps'
        def expectedVariant = testedVariant() // Cannot inline because of Groovy bug

        resolve.expectGraph {
            root(":", ":test:") {
                module("org.test:module:1.0:${expectedVariant}")
            }
        }
    }

    /**
     * This test simulates what should be the new, better way of doing latest.release.
     * Here, we set a value on the consuming configuration, which mandates a release status.
     * This means that we will evict versions which are "integration", even during dynamic
     * version selection. The nice thing is that it uses attribute matching now, and we prove
     * that it integrates well with the legacy "status" API thanks to the Maven use case:
     * since Maven doesn't have a "status" but infers it from the version instead, we can show
     * that we can provide a status to Maven dependencies and still use attribute matching
     * to use the right version.
     */
    @Unroll
    def "can select the latest.#status version having release status"() {
        given:
        def versions = [
            '1': 'release',
            '2': 'milestone',
            '3': 'integration',
            '4': 'release',
            '5': 'integration'
        ]
        repository {
            versions.each { String version, String s ->
                "org:test:$version" {
                    // Gradle metadata
                    attribute(ProjectInternal.STATUS_ATTRIBUTE.name, s)
                    // Ivy metadata
                    withModule(IvyHttpModule) {
                        withStatus s
                    }
                    // Maven will use component metadata rules
                }
            }
        }
        buildFile << """
            configurations {
                conf {
                   attributes.attribute(org.gradle.api.internal.project.ProjectInternal.STATUS_ATTRIBUTE, '$status')
                }
            }
            
            class StatusRule implements ComponentMetadataRule {
                public void execute(ComponentMetadataContext context) {
                    if (${!GradleMetadataResolveRunner.useIvy()}) {
                        // this is just a hack to get the configuration from the test context
                        def release = 'release'
                        def integration = 'integration'
                        def milestone = 'milestone'
                        def versions = $versions
                        // Maven doesn't publish a status, but we can patch it!
                        context.details.status = "\${versions[context.details.id.version as int]}"
                    }
                }
            }
            
            dependencies {
                conf 'org:test:[1,)'
                components {
                    withModule('org:test', StatusRule)
                }
            }
        """

        when:
        repositoryInteractions {
            'org:test' {
                expectVersionListing()
                versions.each { key, value ->
                    def version = key as int
                    "$version" {
                        if (version > selected) {
                            expectGetMetadata()
                        } else if (version == selected) {
                            expectResolve()
                        }
                    }
                }
            }
        }
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:test:[1,)', "org:test:$selected")
            }
        }

        where:
        status        | selected
        'release'     | 4
        'integration' | 5
        'milestone'   | 2
    }

    static String testedVariant() {
        def variant
        if (GradleMetadataResolveRunner.gradleMetadataEnabled) {
            variant = 'api'
        } else {
            if (GradleMetadataResolveRunner.useIvy()) {
                variant = 'default'
            } else {
                if (GradleMetadataResolveRunner.experimentalResolveBehaviorEnabled) {
                    variant = 'compile'
                } else {
                    variant = 'default'
                }
            }
        }
        variant
    }

    static String variantTerm() {
        if (GradleMetadataResolveRunner.gradleMetadataEnabled) {
            return "variant"
        }
        if (GradleMetadataResolveRunner.experimentalResolveBehaviorEnabled && !GradleMetadataResolveRunner.useIvy()) {
            return "variant"
        }
        return "configuration"
    }
}
