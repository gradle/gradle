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

package org.gradle.integtests.resolve.attributes

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Unroll

@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class MultipleVariantSelectionIntegrationTest extends AbstractModuleDependencyResolveTest {

    def setup() {
        buildFile << """
            def CUSTOM_ATTRIBUTE = Attribute.of('custom', String)
            def CUSTOM2_ATTRIBUTE = Attribute.of('custom2', String)
            dependencies.attributesSchema.attribute(CUSTOM_ATTRIBUTE)
            dependencies.attributesSchema.attribute(CUSTOM2_ATTRIBUTE)
        """
    }

    void "can select distinct variants of the same component by using different attributes if they have different capabilities"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api1') {
                    attribute('custom', 'c1')
                    capability('cap1')
                }
                variant('api2') {
                    attribute('custom', 'c2')
                    capability('cap1')
                }
                variant('runtime1') {
                    attribute('custom2', 'c1')
                    capability('cap2')
                }
                variant('runtime2') {
                    attribute('custom2', 'c2')
                    capability('cap2')
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c1')
                    }
                    capabilities {
                        requireCapability('org.test:cap1')
                    }
                }
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM2_ATTRIBUTE, 'c2')
                    }
                    capabilities {
                        requireCapability('org.test:cap2')
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
                    variant('api1', ['org.gradle.status': MultipleVariantSelectionIntegrationTest.defaultStatus(), custom: 'c1'])
                }
                module('org:test:1.0') {
                    variant('runtime2', ['org.gradle.status': MultipleVariantSelectionIntegrationTest.defaultStatus(), custom2: 'c2'])
                }
            }
        }
    }

    void "fails selecting distinct variants of the same component by using attributes if they have different capabilities but incompatible values"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api1') {
                    attribute('custom', 'c1')
                    capability('cap1')
                }
                variant('api2') {
                    attribute('custom', 'c2')
                    capability('cap1')
                }
                variant('runtime1') {
                    attribute('custom', 'c1')
                    capability('cap2')
                }
                variant('runtime2') {
                    attribute('custom', 'c2')
                    capability('cap2')
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c1')
                    }
                    capabilities {
                        requireCapability('org.test:cap1')
                    }
                }
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c2')
                    }
                    capabilities {
                        requireCapability('org.test:cap2')
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
        failure.assertHasCause("""Multiple incompatible variants of org:test:1.0 were selected:
   - Variant org:test:1.0 variant api1 has attributes {custom=c1, org.gradle.status=${MultipleVariantSelectionIntegrationTest.defaultStatus()}}
   - Variant org:test:1.0 variant runtime2 has attributes {custom=c2, org.gradle.status=${MultipleVariantSelectionIntegrationTest.defaultStatus()}}""")
    }

    void "cannot select distinct variants of the same component by using different attributes if they have the same capabilities"() {
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
                        attribute(CUSTOM_ATTRIBUTE, 'c1')
                    }
                }
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c2')
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
        failure.assertHasCause("""Module 'org:test' has been rejected:
   Cannot select module with conflict on capability 'org:test:1.0' also provided by [org:test:1.0(api), org:test:1.0(runtime)]""")
    }

    @Unroll("can select distinct variants of the same component by using different attributes with capabilities (conflict=#conflict)")
    void "can select distinct variants of the same component by using different attributes with capabilities"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                    capability('org.test', 'cap', '1.0')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                    capability('org.test', 'cap', conflict ? '1.0' : '1.1')
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c1')
                    }
                    capabilities {
                        requireCapability('org.test:cap')
                    }
                }
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c2')
                    }
                    capabilities {
                        requireCapability('org.test:cap')
                    }
                }
            }


            configurations.conf.resolutionStrategy.capabilitiesResolution.all { selectHighestVersion() }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
                if (!conflict) {
                    expectGetArtifact()
                }
            }
        }
        if (conflict) {
            fails 'checkDeps'
        } else {
            succeeds 'checkDeps'
        }

        then:
        if (conflict) {
            failure.assertHasCause("""Module 'org:test' has been rejected:
   Cannot select module with conflict on capability 'org.test:cap:1.0' also provided by [org:test:1.0(runtime), org:test:1.0(api)]""")
        } else {
            resolve.expectGraph {
                root(":", ":test:") {
                    edge('org:test:1.0', 'org:test:1.0')
                    module('org:test:1.0') {
                        maybeByConflictResolution()
                        variant('runtime', ['org.gradle.status': MultipleVariantSelectionIntegrationTest.defaultStatus(), 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', custom: 'c2'])
                    }
                }
            }
        }

        where:
        conflict << [true, false]
    }

    def "selects 2 variants of the same component with transitive dependency if they have different capabilities"() {
        given:
        repository {
            'org:foo:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                    artifact('c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                    artifact('c2')
                }
                variant('altruntime') {
                    attribute('custom2', 'c3')
                    capability('cap3')
                    artifact('c3')
                }
            }
            'org:foo:1.1' {
                variant('api') {
                    attribute('custom', 'c1')
                    artifact('c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                    artifact('c2')
                }
                variant('altruntime') {
                    attribute('custom2', 'c3')
                    capability('cap3')
                    artifact('c3')
                }
            }
            'org:bar:1.0' {
                variant('api') {
                    dependsOn('org:foo:1.1') {
                        capability('org.test', 'cap3', '1.0')
                        attributes.custom2 = 'c3'
                    }
                }
                variant('runtime') {
                    dependsOn('org:foo:1.1') {
                        requestedCapability('org.test', 'cap3', '1.0')
                        attributes.custom2 = 'c3'
                    }
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c2')
                    }
                }
                conf('org:bar:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:bar:1.0' {
                expectResolve()
            }
            'org:foo:1.0' {
                expectGetMetadata()
            }
            'org:foo:1.1' {
                expectGetMetadata()
                expectGetVariantArtifacts('runtime')
                expectGetVariantArtifacts('altruntime')
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:foo:1.0', 'org:foo:1.1') {
                    byConflictResolution('between versions 1.1 and 1.0')
                    // the following assertion is true but limitations to the test fixtures make it hard to check
                    //variant('altruntime', [custom: 'c3', 'org.gradle.status': defaultStatus()])
                    variant('runtime', [custom: 'c2', 'org.gradle.status': MultipleVariantSelectionIntegrationTest.defaultStatus(), 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library'])
                    artifact classifier: 'c2'
                    artifact classifier: 'c3'
                }
                module('org:bar:1.0') {
                    module('org:foo:1.1')
                }
            }
        }

    }

    def "prevents selection of 2 variants of the same component with transitive dependency if they have different capabilities but incompatible attributes"() {
        given:
        repository {
            'org:foo:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                    artifact('c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                    artifact('c2')
                }
                variant('altruntime') {
                    attribute('custom', 'c3')
                    capability('cap3')
                    artifact('c3')
                }
            }
            'org:foo:1.1' {
                variant('api') {
                    attribute('custom', 'c1')
                    artifact('c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                    artifact('c2')
                }
                variant('altruntime') {
                    attribute('custom', 'c3')
                    capability('cap3')
                    artifact('c3')
                }
            }
            'org:bar:1.0' {
                variant('api') {
                    dependsOn('org:foo:1.1') {
                        capability('org.test', 'cap3', '1.0')
                        attributes.custom = 'c3'
                    }
                }
                variant('runtime') {
                    dependsOn('org:foo:1.1') {
                        requestedCapability('org.test', 'cap3', '1.0')
                        attributes.custom = 'c3'
                    }
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c2')
                    }
                }
                conf('org:bar:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:bar:1.0' {
                expectGetMetadata()
            }
            'org:foo:1.0' {
                expectGetMetadata()
            }
            'org:foo:1.1' {
                expectGetMetadata()
            }
        }
        fails 'checkDeps'

        then:
        failure.assertHasCause("""Multiple incompatible variants of org:foo:1.1 were selected:
   - Variant org:foo:1.1 variant altruntime has attributes {custom=c3, org.gradle.status=${defaultStatus()}}
   - Variant org:foo:1.1 variant runtime has attributes {custom=c2, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=${defaultStatus()}, org.gradle.usage=java-runtime}""")

    }

    def "cannot select 2 variants of the same component with transitive dependency if they use the same capability"() {
        given:
        repository {
            'org:foo:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                    artifact('c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                    artifact('c2')
                }
            }
            'org:foo:1.1' {
                variant('api') {
                    attribute('custom', 'c1')
                    artifact('c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                    artifact('c2')
                }
            }
            'org:bar:1.0' {
                variant('api') {
                    dependsOn('org:foo:1.1') {
                        attributes.custom = 'c1'
                    }
                }
                variant('runtime') {
                    dependsOn('org:foo:1.1') {
                        attributes.custom = 'c1'
                    }
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c2')
                    }
                }
                conf('org:bar:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:bar:1.0' {
                expectGetMetadata()
            }
            'org:foo:1.0' {
                expectGetMetadata()
            }
            'org:foo:1.1' {
                expectGetMetadata()
            }
        }
        fails 'checkDeps'

        then:
        failure.assertHasCause("""Module 'org:foo' has been rejected:
   Cannot select module with conflict on capability 'org:foo:1.1' also provided by [org:foo:1.1(runtime), org:foo:1.1(api)]""")

    }

    def "selects a single variant of the same component when asking for a consumer specific attribute"() {
        given:
        repository {
            'org:foo:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                    artifact('c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                    artifact('c2')
                }
            }
            'org:foo:1.1' {
                variant('api') {
                    attribute('custom', 'c1')
                    artifact('c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                    artifact('c2')
                }
            }
            'org:bar:1.0' {
                variant('api') {
                    dependsOn('org:foo:1.1') {
                        attributes.custom = 'c2'
                    }
                }
                variant('runtime') {
                    dependsOn('org:foo:1.1') {
                        attributes.custom = 'c2'
                    }
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0') {
                    attributes {
                        attribute(Attribute.of("other", String), 'unused')
                    }
                }
                conf('org:bar:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:bar:1.0' {
                expectResolve()
            }
            'org:foo:1.0' {
                expectGetMetadata()
            }
            'org:foo:1.1' {
                expectGetMetadata()
                expectGetVariantArtifacts('runtime')
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:foo:1.0', 'org:foo:1.1') {
                    byConflictResolution('between versions 1.1 and 1.0')
                    variant('runtime', [custom: 'c2', 'org.gradle.status': MultipleVariantSelectionIntegrationTest.defaultStatus(), 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library'])
                    artifact classifier: 'c2'
                }
                module('org:bar:1.0') {
                    module('org:foo:1.1')
                }
            }
        }
    }

    def "can select both main variant and test fixtures of a single component"() {
        given:
        repository {
            'org:foo:1.0' {
                variant('api') {}
                variant('runtime') {}
                variant('test-fixtures') {
                    artifact('test-fixtures')
                    capability('org', 'foo-testfixtures', '1.0')
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0')
                conf('org:foo:1.0') {
                    capabilities {
                        requireCapability('org:foo-testfixtures')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectResolve()
                expectGetVariantArtifacts('test-fixtures')
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:foo:1.0') {
                    variant('runtime', ['org.gradle.status': MultipleVariantSelectionIntegrationTest.defaultStatus(), 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library'])
                    artifact()
                }
                module('org:foo:1.0') {
                    variant('test-fixtures', ['org.gradle.status': MultipleVariantSelectionIntegrationTest.defaultStatus()])
                    artifact classifier: 'test-fixtures'
                }
            }
        }
    }

    def "detects conflicts between component with a capability and a variant with the same capability"() {
        given:
        repository {
            'org:foo:1.0'()
            'org:bar:1.0' {
                variant('api') {
                    capability('org', 'bar', '1.0')
                    capability('org', 'foo', '1.0')
                }
                variant('runtime') {
                    capability('org', 'bar', '1.0')
                    capability('org', 'foo', '1.0')
                }
            }
        }
        buildFile << """
            dependencies {
                conf('org:foo:1.0')
                conf('org:bar:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
            }
            'org:bar:1.0' {
                expectGetMetadata()
            }
        }
        fails 'checkDeps'

        then:
        failure.assertHasCause("""Module 'org:foo' has been rejected:
   Cannot select module with conflict on capability 'org:foo:1.0' also provided by [org:bar:1.0(runtime)]""")
        failure.assertHasCause("""Module 'org:bar' has been rejected:
   Cannot select module with conflict on capability 'org:foo:1.0' also provided by [org:foo:1.0(runtime)]""")
    }

    def "detects conflicts between 2 variants of 2 different components with the same capability"() {
        given:
        repository {
            'org:foo:1.0' {
                variant('api') {
                    capability('org', 'foo', '1.0')
                    capability('org', 'blah', '1.0')
                }
                variant('runtime') {
                    capability('org', 'foo', '1.0')
                    capability('org', 'blah', '1.0')
                }
            }
            'org:bar:1.0' {
                variant('api') {
                    capability('org', 'bar', '1.0')
                    capability('org', 'blah', '1.0')
                }
                variant('runtime') {
                    capability('org', 'bar', '1.0')
                    capability('org', 'blah', '1.0')
                }
            }
        }
        buildFile << """
            dependencies {
                conf('org:foo:1.0')
                conf('org:bar:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
            }
            'org:bar:1.0' {
                expectGetMetadata()
            }
        }
        fails 'checkDeps'

        then:
        failure.assertHasCause("""Module 'org:foo' has been rejected:
   Cannot select module with conflict on capability 'org:blah:1.0' also provided by [org:bar:1.0(runtime)]""")
        failure.assertHasCause("""Module 'org:bar' has been rejected:
   Cannot select module with conflict on capability 'org:blah:1.0' also provided by [org:foo:1.0(runtime)]""")
    }

    @ToBeImplemented("https://github.com/gradle/gradle/issues/8386")
    def "selects a variant with different attribute value but matching transform"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api') {
                    attribute('usage', 'api')
                    attribute('format', 'foo')
                }
                variant('runtime') {
                    attribute('usage', 'runtime')
                }
            }
        }

        buildFile << """
            configurations {
                conf {
                    attributes {
                        attribute(Attribute.of("usage", String), "api")
                        attribute(Attribute.of("format", String), "bar")
                    }
                }
            }

            dependencies {
                conf('org:test:1.0')

                registerTransform(FooToBar) {
                    from.attribute(Attribute.of("usage", String), "api")
                    from.attribute(Attribute.of("format", String), "foo")
                    to.attribute(Attribute.of("usage", String), "api")
                    to.attribute(Attribute.of("format", String), "bar")
                }
            }

            import org.gradle.api.artifacts.transform.*

            abstract class FooToBar implements TransformAction<TransformParameters.None> {
                @Override
                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    outputs.dir(input.name)
                }
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
            }
        }

        then:
        fails 'checkDeps'

        //TODO: should pass as specified below
//        when:
//        repositoryInteractions {
//            'org:test:1.0' {
//                expectResolve()
//            }
//        }
//        succeeds 'checkDeps'

//        then:
//        resolve.expectGraph {
//            root(":", ":test:") {
//                module('org:test:1.0') {
//                    variant('api', ['org.gradle.status': MultipleVariantSelectionIntegrationTest.defaultStatus(), usage: 'api', format: 'foo'])
//                }
//            }
//        }
    }

    static Closure<String> defaultStatus() {
        { -> GradleMetadataResolveRunner.useIvy() ? 'integration' : 'release' }
    }
}
