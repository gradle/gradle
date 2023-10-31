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

package org.gradle.integtests.resolve.attributes

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import org.gradle.internal.component.ResolutionFailureHandler
import spock.lang.Issue
import spock.lang.Unroll

class DependenciesAttributesIntegrationTest extends AbstractModuleDependencyResolveTest {

    def setup() {
        buildFile << """
            def CUSTOM_ATTRIBUTE = Attribute.of('custom', String)
            dependencies.attributesSchema.attribute(CUSTOM_ATTRIBUTE)
        """
    }

    def "can declare attributes on dependencies"() {
        given:
        repository {
            'org:test:1.0'()
        }

        buildFile << """
            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'test value')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:test:1.0')
            }
        }

        and:
        outputDoesNotContain("Cannot set attributes for dependency \"org:test:1.0\": it was probably created by a plugin using internal APIs")
    }

    def "can declare attributes on constraints"() {
        given:
        repository {
            'org:test:1.0'()
        }

        buildFile << """
            dependencies {
                constraints {
                    conf('org:test:1.0') {
                        attributes {
                            attribute(CUSTOM_ATTRIBUTE, 'test value')
                        }
                    }
                }
                conf 'org:test'
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:test', 'org:test:1.0') {
                    byConstraint()
                }
                constraint('org:test:1.0', 'org:test:1.0')
            }
        }

        and:
        outputDoesNotContain("Cannot set attributes for constraint \"org:test:1.0\": it was probably created by a plugin using internal APIs")
    }


    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    @Unroll("Selects variant #expectedVariant using custom attribute value #attributeValue")
    def "attribute value is used during selection"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, '$attributeValue')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:test:1.0') {
                    configuration = expectedVariant
                    variant(expectedVariant, expectedAttributes)
                }
            }
        }

        where:
        attributeValue | expectedVariant | expectedAttributes
        'c1'           | 'api'           | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: 'c1']
        'c2'           | 'runtime'       | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: 'c2']
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "Fails resolution because dependency attributes and constraint attributes conflict"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                }
            }
        }

        buildFile << """
            dependencies {
                constraints {
                    conf('org:test:1.0') {
                        attributes {
                            attribute(CUSTOM_ATTRIBUTE, 'c2')
                        }
                    }
                }
                conf('org:test') {
                   attributes {
                      attribute(CUSTOM_ATTRIBUTE, 'c1')
                   }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
            }
        }
        fails 'checkDeps'

        then:
        failure.assertHasCause("""Inconsistency between attributes of a constraint and a dependency, on attribute 'custom' : dependency requires 'c1' while constraint required 'c2'""")
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    @Unroll("Selects variant #expectedVariant using typed attribute value #attributeValue")
    @Issue("gradle/gradle#5232")
    def "can declare typed attributes without failing serialization"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api') {
                    attribute('lifecycle', 'c1')
                }
                variant('runtime') {
                    attribute('lifecycle', 'c2')
                }
            }
        }

        buildFile << """
            interface Lifecycle extends Named {}

            def LIFECYCLE_ATTRIBUTE = Attribute.of('lifecycle', Lifecycle)
            dependencies.attributesSchema.attribute(LIFECYCLE_ATTRIBUTE)

            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(LIFECYCLE_ATTRIBUTE, objects.named(Lifecycle, '$attributeValue'))
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:test:1.0') {
                    configuration = expectedVariant
                    variant(expectedVariant, expectedAttributes)
                }
            }
        }

        and:
        outputDoesNotContain("Cannot set attributes for dependency \"org:test:1.0\": it was probably created by a plugin using internal APIs")

        where:
        attributeValue | expectedVariant | expectedAttributes
        'c1'           | 'api'           | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', lifecycle: 'c1']
        'c2'           | 'runtime'       | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', lifecycle: 'c2']
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    @Issue("gradle/gradle#5232")
    def "Serializes and reads back failed resolution when failure comes from an unmatched typed attribute"() {
        given:
        repository {
            'org:test:1.0' {
                attribute('lifecycle', 'some')
            }
        }

        buildFile << """
            interface Lifecycle extends Named {}

            def LIFECYCLE_ATTRIBUTE = Attribute.of('lifecycle', Lifecycle)
            dependencies.attributesSchema.attribute(LIFECYCLE_ATTRIBUTE)

            dependencies {
                conf('org:test:[1.0,)') {
                    attributes {
                        attribute(LIFECYCLE_ATTRIBUTE, objects.named(Lifecycle, 'other'))
                    }
                }
            }

            configurations.conf.incoming.afterResolve {
                // afterResolve will trigger the problem when reading
                it.resolutionResult.allComponents {
                    println "Success for \${it.id}"
                }
            }
        """

        when:
        repositoryInteractions {
            'org:test' {
                expectVersionListing()
            }
            'org:test:1.0' {
                expectGetMetadata()
            }
        }
        fails 'checkDeps'

        then:
        failure.assertHasCause("""Could not find any version that matches org:test:[1.0,).""")

        and:
        outputContains("Success for project :")
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "Merges consumer configuration attributes with dependency attributes"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                }
            }
        }

        buildFile << """
            configurations.conf.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "java-api"))

            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c1')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:test:1.0') {
                    configuration = 'api'
                    variant('api', ['org.gradle.status': DependenciesAttributesIntegrationTest.defaultStatus(), 'org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: 'c1'])
                }
            }
        }
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "Fails resolution because consumer configuration attributes and dependency attributes conflict"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                }
            }
        }

        buildFile << """
            configurations.conf.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "java-runtime"))

            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c1')
                    }
                }
            }
        """
        file("gradle.properties").text = "${ResolutionFailureHandler.FULL_FAILURES_MESSAGE_PROPERTY}=true"

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
            }
        }
        fails 'checkDeps'

        then:
        failure.assertHasCause("""No matching variant of org:test:1.0 was found. The consumer was configured to find a component for use during runtime, as well as attribute 'custom' with value 'c1' but:
  - Variant 'api' capability org:test:1.0 declares a component, as well as attribute 'custom' with value 'c1':
      - Incompatible because this component declares a component for use during compile-time and the consumer needed a component for use during runtime
  - Variant 'runtime' capability org:test:1.0 declares a component for use during runtime:
      - Incompatible because this component declares a component, as well as attribute 'custom' with value 'c2' and the consumer needed a component, as well as attribute 'custom' with value 'c1'""")
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    @Unroll("Selects variant #expectedVariant using custom attribute value #dependencyValue overriding configuration attribute #configurationValue")
    def "dependency attribute value overrides configuration attribute"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                }
            }
        }

        buildFile << """
            configurations.conf.attributes.attribute(CUSTOM_ATTRIBUTE, '$configurationValue')

            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, '$dependencyValue')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:test:1.0') {
                    configuration = expectedVariant
                    variant(expectedVariant, expectedAttributes)
                }
            }
        }

        where:
        configurationValue | dependencyValue | expectedVariant | expectedAttributes
        'c2'               | 'c1'            | 'api'           | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: 'c1']
        'c1'               | 'c2'            | 'runtime'       | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: 'c2']
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    @Unroll("Selects variant #expectedVariant using custom attribute value #dependencyValue overriding configuration attribute #configurationValue using dependency constraint")
    def "dependency attribute value overrides configuration attribute using dependency constraint"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                }
            }
        }

        buildFile << """
            configurations.conf.attributes.attribute(CUSTOM_ATTRIBUTE, '$configurationValue')

            dependencies {
                constraints {
                    conf('org:test:1.0') {
                       attributes {
                          attribute(CUSTOM_ATTRIBUTE, '$dependencyValue')
                       }
                    }
                }
                conf 'org:test'
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:test', 'org:test:1.0') {
                    byConstraint()
                    configuration = expectedVariant
                    variant(expectedVariant, expectedAttributes)
                }
                constraint('org:test:1.0', 'org:test:1.0') {
                    configuration = expectedVariant
                    variant(expectedVariant, expectedAttributes)
                }
            }
        }

        where:
        configurationValue | dependencyValue | expectedVariant | expectedAttributes
        'c2'               | 'c1'            | 'api'           | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: 'c1']
        'c1'               | 'c2'            | 'runtime'       | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: 'c2']
    }

    @Issue("https://github.com/gradle/gradle/issues/20182")
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    @Unroll("Selects variant #expectedVariant using custom attribute value #dependencyValue overriding configuration attribute #configurationValue using dependency constraint without version")
    def "dependency attribute value overrides configuration attribute using dependency constraint without version"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                }
            }
        }

        buildFile << """
            configurations.conf.attributes.attribute(CUSTOM_ATTRIBUTE, '$configurationValue')

            dependencies {
                constraints {
                    conf('org:test') {
                       attributes {
                          attribute(CUSTOM_ATTRIBUTE, '$dependencyValue')
                       }
                    }
                }
                conf 'org:test:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:test:1.0') {
                    configuration = expectedVariant
                    byConstraint()
                    variant(expectedVariant, expectedAttributes)
                }
                constraint('org:test', 'org:test:1.0') {
                    configuration = expectedVariant
                    variant(expectedVariant, expectedAttributes)
                }
            }
        }

        where:
        configurationValue | dependencyValue | expectedVariant | expectedAttributes
        'c2'               | 'c1'            | 'api'           | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: 'c1']
        'c1'               | 'c2'            | 'runtime'       | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: 'c2']
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "Fails resolution because consumer configuration attributes and constraint attributes conflict"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                }
            }
        }

        buildFile << """
            configurations.conf.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "java-runtime"))

            dependencies {
                constraints {
                    conf('org:test:1.0') {
                        attributes {
                            attribute(CUSTOM_ATTRIBUTE, 'c1')
                        }
                    }
                }
                conf 'org:test'
            }
        """

        file("gradle.properties").text = "${ResolutionFailureHandler.FULL_FAILURES_MESSAGE_PROPERTY}=true"

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
            }
        }
        fails 'checkDeps'

        then:
        failure.assertHasCause("""No matching variant of org:test:1.0 was found. The consumer was configured to find a component for use during runtime, as well as attribute 'custom' with value 'c1' but:
  - Variant 'api' capability org:test:1.0 declares a component, as well as attribute 'custom' with value 'c1':
      - Incompatible because this component declares a component for use during compile-time and the consumer needed a component for use during runtime
  - Variant 'runtime' capability org:test:1.0 declares a component for use during runtime:
      - Incompatible because this component declares a component, as well as attribute 'custom' with value 'c2' and the consumer needed a component, as well as attribute 'custom' with value 'c1'""")
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    @Unroll("Selects variant #expectedVariant using dependency attribute value #attributeValue set in a metadata rule")
    def "attribute value set by metadata rule is used during selection"() {
        given:
        repository {
            'org:testA:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                }
            }
            'org:testB:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                }
            }

            'org:directA:1.0' {
                dependsOn 'org:testA:1.0'
            }
            'org:directB:1.0'()
        }

        buildFile << """
            // this is actually the rules that we want to test
            class ModifyDependencyRule implements ComponentMetadataRule {
                Attribute attribute

                @javax.inject.Inject
                ModifyDependencyRule(Attribute attribute) {
                    this.attribute = attribute
                }

                void execute(ComponentMetadataContext context) {
                    context.details.allVariants {
                        withDependencies {
                            it.each {
                               it.attributes {
                                  it.attribute(attribute, '$attributeValue')
                               }
                            }
                        }
                    }
                }
            }

            class AddDependencyRule implements ComponentMetadataRule {
                Attribute attribute

                @javax.inject.Inject
                AddDependencyRule(Attribute attribute) {
                    this.attribute = attribute
                }

                void execute(ComponentMetadataContext context) {
                    context.details.allVariants {
                        withDependencies {
                            it.add('org:testB:1.0') {
                               it.attributes {
                                  it.attribute(attribute, '$attributeValue')
                               }
                            }
                        }
                    }
                }
            }

            dependencies {
                components {
                    // first notation: mutation of existing dependencies
                    withModule('org:directA', ModifyDependencyRule) {
                        params(CUSTOM_ATTRIBUTE)
                    }
                    // 2d notation: adding dependencies (this is a different code path)
                    withModule('org:directB', AddDependencyRule) {
                        params(CUSTOM_ATTRIBUTE)
                    }
                }
                conf('org:directA:1.0')
                conf('org:directB:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:directA:1.0' {
                expectResolve()
            }
            'org:testA:1.0' {
                expectResolve()
            }
            'org:directB:1.0' {
                expectResolve()
            }
            'org:testB:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:directA:1.0') {
                    module('org:testA:1.0') {
                        configuration = expectedVariant
                        variant(expectedVariant, expectedAttributes)
                    }
                }
                module('org:directB:1.0') {
                    module('org:testB:1.0') {
                        configuration = expectedVariant
                        variant(expectedVariant, expectedAttributes)
                    }
                }
            }
        }

        where:
        attributeValue | expectedVariant | expectedAttributes
        'c1'           | 'api'           | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: 'c1']
        'c2'           | 'runtime'       | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: 'c2']
    }


    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    @Unroll("Selects variant #expectedVariant using transitive dependency attribute value #attributeValue set in a metadata rule")
    def "attribute value set by metadata rule on transitive dependency is used during selection"() {
        given:
        repository {
            'org:testA:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                }
            }
            'org:testB:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                }
            }

            'org:directA:1.0' {
                dependsOn 'org:transitiveA:1.0'
            }
            'org:directB:1.0' {
                dependsOn 'org:transitiveB:1.0'
            }

            'org:transitiveA:1.0' {
                dependsOn 'org:testA:1.0'
            }

            'org:transitiveB:1.0'()
        }

        buildFile << """
            // this is actually the rules that we want to test
            class ModifyDependencyRule implements ComponentMetadataRule {
                Attribute attribute

                @javax.inject.Inject
                ModifyDependencyRule(Attribute attribute) {
                    this.attribute = attribute
                }

                void execute(ComponentMetadataContext context) {
                    context.details.allVariants {
                        withDependencies {
                            it.each {
                               it.attributes {
                                  it.attribute(attribute, '$attributeValue')
                               }
                            }
                        }
                    }
                }
            }

            class AddDependencyRule implements ComponentMetadataRule {
                Attribute attribute

                @javax.inject.Inject
                AddDependencyRule(Attribute attribute) {
                    this.attribute = attribute
                }

                void execute(ComponentMetadataContext context) {
                    context.details.allVariants {
                        withDependencies {
                            it.add('org:testB:1.0') {
                               it.attributes {
                                  it.attribute(attribute, '$attributeValue')
                               }
                            }
                        }
                    }
                }
            }

            dependencies {
                components {
                    // first notation: mutation of existing dependencies
                    withModule('org:transitiveA', ModifyDependencyRule) {
                        params(CUSTOM_ATTRIBUTE)
                    }
                    // 2d notation: adding dependencies (this is a different code path)
                    withModule('org:transitiveB', AddDependencyRule) {
                        params(CUSTOM_ATTRIBUTE)
                    }
                }
                conf('org:directA:1.0')
                conf('org:directB:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:directA:1.0' {
                expectResolve()
            }
            'org:transitiveA:1.0' {
                expectResolve()
            }
            'org:testA:1.0' {
                expectResolve()
            }
            'org:directB:1.0' {
                expectResolve()
            }
            'org:transitiveB:1.0' {
                expectResolve()
            }
            'org:testB:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:directA:1.0') {
                    module('org:transitiveA:1.0') {
                        module('org:testA:1.0') {
                            configuration = expectedVariant
                            variant(expectedVariant, expectedAttributes)
                        }
                    }
                }
                module('org:directB:1.0') {
                    module('org:transitiveB:1.0') {
                        module('org:testB:1.0') {
                            configuration = expectedVariant
                            variant(expectedVariant, expectedAttributes)
                        }
                    }
                }
            }
        }

        where:
        attributeValue | expectedVariant | expectedAttributes
        'c1'           | 'api'           | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: 'c1']
        'c2'           | 'runtime'       | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: 'c2']
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    @Unroll("Selects direct=#expectedDirectVariant, transitive=[#expectedTransitiveVariantA, #expectedTransitiveVariantB], leaf=#expectedLeafVariant making sure dependency attribute value doesn't leak to transitives")
    def "Attribute value on dependency only affects selection of this dependency (using component metadata rules)"() {
        given:
        repository {
            def modules = ['direct', 'transitive', 'leaf']
            modules.eachWithIndex { module, idx ->
                ['A', 'B'].each { appendix ->
                    "org:${module}${appendix}:1.0" {
                        if (idx < modules.size() - 1) {
                            dependsOn("org:${modules[idx + 1]}${appendix}:1.0")
                        }
                        variant('api') {
                            attribute('custom', 'c1')
                        }
                        variant('runtime') {
                            attribute('custom', 'c2')
                        }
                    }
                }
            }
        }

        buildFile << """
            configurations.conf.attributes.attribute(CUSTOM_ATTRIBUTE, '$configurationAttributeValue')

            class ModifyDependencyRule implements ComponentMetadataRule {
                Attribute attribute
                String value

                @javax.inject.Inject
                ModifyDependencyRule(Attribute attribute, String value) {
                    this.attribute = attribute
                    this.value = value
                }

                void execute(ComponentMetadataContext context) {
                    context.details.allVariants {
                        withDependencies {
                            it.each {
                               it.attributes {
                                  it.attribute(attribute, value)
                               }
                            }
                        }
                    }
                }
            }

            dependencies {
                components {
                    // transitive module will override the configuration attribute
                    // and it shouldn't affect the selection of 'direct' or 'leaf' dependencies
                    withModule('org:directA', ModifyDependencyRule) {
                        params(CUSTOM_ATTRIBUTE)
                        params('$transitiveAttributeValueA')
                    }
                    withModule('org:directB', ModifyDependencyRule) {
                        params(CUSTOM_ATTRIBUTE)
                        params('$transitiveAttributeValueB')
                    }
                }
                conf('org:directA:1.0')
                conf('org:directB:1.0')
            }
        """

        when:
        repositoryInteractions {
            ['direct', 'transitive', 'leaf'].each { module ->
                ['A', 'B'].each { appendix ->
                    "org:${module}${appendix}:1.0" {
                        expectResolve()
                    }
                }
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:directA:1.0') {
                    configuration = expectedDirectVariant
                    variant(expectedDirectVariant, ['org.gradle.status': DependenciesAttributesIntegrationTest.defaultStatus(), 'org.gradle.usage': "java-${expectedDirectVariant}", 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: configurationAttributeValue])
                    module('org:transitiveA:1.0') {
                        configuration = expectedTransitiveVariantA
                        variant(expectedTransitiveVariantA, ['org.gradle.status': DependenciesAttributesIntegrationTest.defaultStatus(), 'org.gradle.usage': "java-${expectedTransitiveVariantA}", 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: transitiveAttributeValueA])
                        module('org:leafA:1.0') {
                            configuration = expectedLeafVariant
                            variant(expectedLeafVariant, ['org.gradle.status': DependenciesAttributesIntegrationTest.defaultStatus(), 'org.gradle.usage': "java-${expectedLeafVariant}", 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: configurationAttributeValue])
                        }
                    }
                }
                module('org:directB:1.0') {
                    configuration = expectedDirectVariant
                    variant(expectedDirectVariant, ['org.gradle.status': DependenciesAttributesIntegrationTest.defaultStatus(), 'org.gradle.usage': "java-${expectedDirectVariant}", 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: configurationAttributeValue])
                    module('org:transitiveB:1.0') {
                        configuration = expectedTransitiveVariantB
                        variant(expectedTransitiveVariantB, ['org.gradle.status': DependenciesAttributesIntegrationTest.defaultStatus(), 'org.gradle.usage': "java-${expectedTransitiveVariantB}", 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: transitiveAttributeValueB])
                        module('org:leafB:1.0') {
                            configuration = expectedLeafVariant
                            variant(expectedLeafVariant, ['org.gradle.status': DependenciesAttributesIntegrationTest.defaultStatus(), 'org.gradle.usage': "java-${expectedLeafVariant}", 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: configurationAttributeValue])
                        }
                    }
                }
            }
        }

        where:
        configurationAttributeValue | transitiveAttributeValueA | transitiveAttributeValueB | expectedDirectVariant | expectedTransitiveVariantA | expectedTransitiveVariantB | expectedLeafVariant
        'c1'                        | 'c1'                      | 'c1'                      | 'api'                 | 'api'                      | 'api'                      | 'api'
        'c1'                        | 'c2'                      | 'c2'                      | 'api'                 | 'runtime'                  | 'runtime'                  | 'api'
        'c2'                        | 'c2'                      | 'c2'                      | 'runtime'             | 'runtime'                  | 'runtime'                  | 'runtime'
        'c2'                        | 'c1'                      | 'c1'                      | 'runtime'             | 'api'                      | 'api'                      | 'runtime'

        'c1'                        | 'c1'                      | 'c2'                      | 'api'                 | 'api'                      | 'runtime'                  | 'api'
        'c1'                        | 'c2'                      | 'c1'                      | 'api'                 | 'runtime'                  | 'api'                      | 'api'
        'c2'                        | 'c2'                      | 'c1'                      | 'runtime'             | 'runtime'                  | 'api'                      | 'runtime'
        'c2'                        | 'c1'                      | 'c2'                      | 'runtime'             | 'api'                      | 'runtime'                  | 'runtime'
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    @Unroll("Selects direct=#expectedDirectVariant, transitive=[#expectedTransitiveVariantA, #expectedTransitiveVariantB], leaf=#expectedLeafVariant making sure dependency attribute value doesn't leak to transitives (using published metadata)")
    def "Attribute value on dependency only affects selection of this dependency (using published metadata)"() {
        given:
        repository {
            def modules = ['direct', 'transitive', 'leaf']
            modules.eachWithIndex { module, idx ->
                ['A', 'B'].each { appendix ->
                    "org:${module}${appendix}:1.0" {
                        if (idx < modules.size() - 1) {
                            ['api', 'runtime'].each { name ->
                                variant(name) {
                                    dependsOn("org:${modules[idx + 1]}${appendix}:1.0") {
                                        if (module == 'direct') {
                                            attributes.custom = "${appendix == 'A' ? transitiveAttributeValueA : transitiveAttributeValueB}"
                                        }
                                    }
                                }
                            }
                        }
                        variant('api') {
                            attribute('custom', 'c1')
                        }
                        variant('runtime') {
                            attribute('custom', 'c2')
                        }
                    }
                }
            }
        }

        buildFile << """
            configurations.conf.attributes.attribute(CUSTOM_ATTRIBUTE, '$configurationAttributeValue')

            dependencies {
                conf('org:directA:1.0')
                conf('org:directB:1.0')
            }
        """

        when:
        repositoryInteractions {
            ['direct', 'transitive', 'leaf'].each { module ->
                ['A', 'B'].each { appendix ->
                    "org:${module}${appendix}:1.0" {
                        expectResolve()
                    }
                }
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:directA:1.0') {
                    configuration = expectedDirectVariant
                    variant(expectedDirectVariant, ['org.gradle.status': DependenciesAttributesIntegrationTest.defaultStatus(), 'org.gradle.usage': "java-${expectedDirectVariant}", 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: configurationAttributeValue])
                    module('org:transitiveA:1.0') {
                        configuration = expectedTransitiveVariantA
                        variant(expectedTransitiveVariantA, ['org.gradle.status': DependenciesAttributesIntegrationTest.defaultStatus(), 'org.gradle.usage': "java-${expectedTransitiveVariantA}", 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: transitiveAttributeValueA])
                        module('org:leafA:1.0') {
                            configuration = expectedLeafVariant
                            variant(expectedLeafVariant, ['org.gradle.status': DependenciesAttributesIntegrationTest.defaultStatus(), 'org.gradle.usage': "java-${expectedLeafVariant}", 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: configurationAttributeValue])
                        }
                    }
                }
                module('org:directB:1.0') {
                    configuration = expectedDirectVariant
                    variant(expectedDirectVariant, ['org.gradle.status': DependenciesAttributesIntegrationTest.defaultStatus(), 'org.gradle.usage': "java-${expectedDirectVariant}", 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: configurationAttributeValue])
                    module('org:transitiveB:1.0') {
                        configuration = expectedTransitiveVariantB
                        variant(expectedTransitiveVariantB, ['org.gradle.status': DependenciesAttributesIntegrationTest.defaultStatus(), 'org.gradle.usage': "java-${expectedTransitiveVariantB}", 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: transitiveAttributeValueB])
                        module('org:leafB:1.0') {
                            configuration = expectedLeafVariant
                            variant(expectedLeafVariant, ['org.gradle.status': DependenciesAttributesIntegrationTest.defaultStatus(), 'org.gradle.usage': "java-${expectedLeafVariant}", 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: configurationAttributeValue])
                        }
                    }
                }
            }
        }

        where:
        configurationAttributeValue | transitiveAttributeValueA | transitiveAttributeValueB | expectedDirectVariant | expectedTransitiveVariantA | expectedTransitiveVariantB | expectedLeafVariant
        'c1'                        | 'c1'                      | 'c1'                      | 'api'                 | 'api'                      | 'api'                      | 'api'
        'c1'                        | 'c2'                      | 'c2'                      | 'api'                 | 'runtime'                  | 'runtime'                  | 'api'
        'c2'                        | 'c2'                      | 'c2'                      | 'runtime'             | 'runtime'                  | 'runtime'                  | 'runtime'
        'c2'                        | 'c1'                      | 'c1'                      | 'runtime'             | 'api'                      | 'api'                      | 'runtime'

        'c1'                        | 'c1'                      | 'c2'                      | 'api'                 | 'api'                      | 'runtime'                  | 'api'
        'c1'                        | 'c2'                      | 'c1'                      | 'api'                 | 'runtime'                  | 'api'                      | 'api'
        'c2'                        | 'c2'                      | 'c1'                      | 'runtime'             | 'runtime'                  | 'api'                      | 'runtime'
        'c2'                        | 'c1'                      | 'c2'                      | 'runtime'             | 'api'                      | 'runtime'                  | 'runtime'
    }

    static Closure<String> defaultStatus() {
        { -> GradleMetadataResolveRunner.useIvy() ? 'integration' : 'release' }
    }
}
