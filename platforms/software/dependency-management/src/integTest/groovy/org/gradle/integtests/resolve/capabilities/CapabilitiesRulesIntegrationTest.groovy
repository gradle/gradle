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

package org.gradle.integtests.resolve.capabilities

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import spock.lang.Issue

class CapabilitiesRulesIntegrationTest extends AbstractModuleDependencyResolveTest {

    def "can declare capabilities using a component metadata rule"() {
        given:
        repository {
            'cglib:cglib:3.2.5'()
            'cglib:cglib-nodep:3.2.5'()
        }

        buildFile << """
            class CapabilityRule implements ComponentMetadataRule {

                @Override
                void execute(ComponentMetadataContext context) {
                    def details = context.details
                    details.allVariants {
                         withCapabilities {
                             addCapability('cglib', 'cglib', details.id.version)
                         }
                     }
                }
            }

            dependencies {
               conf "cglib:cglib-nodep:3.2.5"
               conf "cglib:cglib:3.2.5"

               components {
                  withModule('cglib:cglib-nodep', CapabilityRule)
               }
            }
        """

        when:
        repositoryInteractions {
            'cglib:cglib-nodep:3.2.5' {
                expectGetMetadata()
            }
            'cglib:cglib:3.2.5' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        def variant = 'runtime'
        if (!isGradleMetadataPublished() && useIvy()) {
            variant = 'default'
        }
        failure.assertHasCause("""Module 'cglib:cglib-nodep' has been rejected:
   Cannot select module with conflict on capability 'cglib:cglib:3.2.5' also provided by [cglib:cglib:3.2.5($variant)]""")
        failure.assertHasCause("""Module 'cglib:cglib' has been rejected:
   Cannot select module with conflict on capability 'cglib:cglib:3.2.5' also provided by [cglib:cglib-nodep:3.2.5($variant)]""")
    }

    def "implicit capability conflict is detected if implicit capability is discovered late"() {
        given:
        repository {
            'cglib:cglib:3.2.5'()
            'cglib:cglib-nodep:3.2.5'()
            'org:lib:1.0' {
                dependsOn 'cglib:cglib:3.2.5'
            }
        }

        buildFile << """
            class CapabilityRule implements ComponentMetadataRule {

                @Override
                void execute(ComponentMetadataContext context) {
                    def details = context.details
                    details.allVariants {
                         withCapabilities {
                             addCapability('cglib', 'cglib', details.id.version)
                         }
                     }
                }
            }

            dependencies {
               conf "cglib:cglib-nodep:3.2.5"
               conf "org:lib:1.0"

               components {
                  withModule('cglib:cglib-nodep', CapabilityRule)
               }
            }
        """

        when:
        repositoryInteractions {
            'cglib:cglib-nodep:3.2.5' {
                expectGetMetadata()
            }
            'cglib:cglib:3.2.5' {
                expectGetMetadata()
            }
            'org:lib:1.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        def variant = 'runtime'
        if (!isGradleMetadataPublished() && useIvy()) {
            variant = 'default'
        }
        failure.assertHasCause("""Module 'cglib:cglib-nodep' has been rejected:
   Cannot select module with conflict on capability 'cglib:cglib:3.2.5' also provided by [cglib:cglib:3.2.5($variant)]""")
        failure.assertHasCause("""Module 'cglib:cglib' has been rejected:
   Cannot select module with conflict on capability 'cglib:cglib:3.2.5' also provided by [cglib:cglib-nodep:3.2.5($variant)]""")
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false")
    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def "can detect conflict with capability in different versions (#rule)"() {
        given:
        repository {
            'cglib:cglib:3.2.5'()
            'cglib:cglib-nodep:3.2.4'()
        }

        buildFile << """
            class CapabilityRule implements ComponentMetadataRule {

                @Override
                void execute(ComponentMetadataContext context) {
                    def details = context.details
                    if (details.id.name == 'cglib') {
                         details.allVariants {
                             withCapabilities {
                                 addCapability('cglib', 'random', details.id.version)
                             }
                         }
                    } else {
                        details.allVariants {
                             withCapabilities {
                                 addCapability('cglib', 'cglib', details.id.version)
                             }
                         }
                    }
                }
            }

            dependencies {
               conf "cglib:cglib-nodep:3.2.4"
               conf "cglib:cglib:3.2.5"

               components {
                  withModule('cglib:cglib-nodep', CapabilityRule)
                  withModule('cglib:cglib', CapabilityRule)
               }
            }

            configurations.conf.resolutionStrategy.capabilitiesResolution {
                $rule
            }


            tasks.register("dumpCapabilitiesFromArtifactView") {
                def artifactCollection = configurations.conf.incoming.artifactView {
                    attributes {
                        attribute(Attribute.of("artifactType", String), "jar")
                    }
                }.artifacts
                doFirst {
                    artifactCollection.artifacts.each {
                        println "Artifact: \${it.id.componentIdentifier.displayName}"
                        println "  - artifact: \${it.file}"

                        def capabilities = it.variant.capabilities
                        if (capabilities.isEmpty()) {
                            throw new IllegalStateException("Expected default capability to be explicit")
                        } else {
                            println "  - capabilities: " + capabilities.collect {
                                if (!(it instanceof org.gradle.internal.component.external.model.DefaultImmutableCapability)) {
                                    throw new IllegalStateException("Unexpected capability type: \$it")
                                }
                                "\${it}"
                            }
                        }
                        println("---------")
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'cglib:cglib-nodep:3.2.4' {
                expectGetMetadata()
            }
            'cglib:cglib:3.2.5' {
                expectResolve()
            }
        }
        run ':checkDeps', ':dumpCapabilitiesFromArtifactView'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('cglib:cglib-nodep:3.2.4', 'cglib:cglib:3.2.5')
                    .byConflictResolution(reason)
                module('cglib:cglib:3.2.5')
            }
        }
        outputContains('- capabilities: [capability group=\'cglib\', name=\'cglib\', version=\'3.2.5\', capability group=\'cglib\', name=\'random\', version=\'3.2.5\']')

        where:
        rule                                                                                  | reason
        'all { selectHighestVersion() }'                                                      | 'latest version of capability cglib:cglib'
        'withCapability("cglib:cglib") { selectHighestVersion() }'                            | 'latest version of capability cglib:cglib'
        'withCapability("cglib", "cglib") { selectHighestVersion() }'                         | 'latest version of capability cglib:cglib'
        'all { select(candidates.find { it.id.module == "cglib" }) because "custom reason" }' | 'On capability cglib:cglib custom reason'
    }

    def "can detect conflict between local project and capability from external dependency"() {
        given:
        repository {
            'org:test:1.0'()
        }

        buildFile << """
            apply plugin: 'java-library'

            class CapabilityRule implements ComponentMetadataRule {

                @Override
                void execute(ComponentMetadataContext context) {
                    context.details.allVariants {
                        withCapabilities {
                            addCapability('org', 'capability', '1.0')
                        }
                    }
                }
            }

            configurations.api.outgoing {
                capability 'org:capability:1.0'
            }

            dependencies {
                conf 'org:test:1.0'

                components {
                   withModule('org:test', CapabilityRule)
                }
            }

            configurations {
                conf.extendsFrom(api)
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Module 'org:test' has been rejected:
   Cannot select module with conflict on capability 'org:capability:1.0' also provided by [:test:unspecified(conf)]""")
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value="true")
    def "can remove a published capability"() {
        given:
        repository {
            'org:a:1.0' {
                variant('runtime') {
                    capability('test_capability')
                }
            }
            'org:b:1.0' {
                variant('runtime') {
                    capability('test_capability')
                }
            }
        }

        buildFile << """
            class CapabilityRule implements ComponentMetadataRule {

                @Override
                void execute(ComponentMetadataContext context) {
                    context.details.allVariants {
                        withCapabilities {
                            removeCapability('org.test', 'test_capability')
                        }
                    }
                }
            }

            dependencies {
                conf 'org:a:1.0'
                conf 'org:b:1.0'

                components.all(CapabilityRule)
            }
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectResolve()
            }
            'org:b:1.0' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:a:1.0')
                module('org:b:1.0')
            }
        }
    }

    @Issue("gradle/gradle#12011")
    def "can detect capability conflict even when participants belong to a virtual platform (#first, #second)"() {
        given:
        repository {
            'aligned:foo:1.0'()
            'indirect:bar:1.0' {
                dependsOn 'other:baz:1.0'
            }
            'other:baz:1.0'()
        }

        buildFile << """
            class TheOneRule implements ComponentMetadataRule {

                @Override
                void execute(ComponentMetadataContext context) {
                    def id = context.details.id
                    if (id.group == "aligned") {
                        context.details.belongsTo('aligned:virtual:1.0')
                        context.details.allVariants {
                            withCapabilities {
                                addCapability('org.test', 'test_capability', '1.0')
                            }
                        }
                    } else if (id.group == "other") {
                        context.details.allVariants {
                            withCapabilities {
                                addCapability('org.test', 'test_capability', '1.0')
                            }
                        }
                    }
                }
            }

            dependencies {
                conf '$first'
                conf '$second'

                components.all(TheOneRule)
            }
"""

        when:
        repositoryInteractions {
            'aligned:foo:1.0' {
                expectGetMetadata()
            }
            'indirect:bar:1.0' {
                expectGetMetadata()
            }
            'other:baz:1.0' {
                expectGetMetadata()
            }
        }
        fails ":checkDeps"

        then:
        failure.assertHasCause("Module 'aligned:foo' has been rejected")

        where:
        first   | second
        'aligned:foo:1.0'   | 'indirect:bar:1.0'
        'indirect:bar:1.0'  | 'aligned:foo:1.0'
    }

    @Issue('gradle/gradle#18494')
    def 'two capabilities conflict when one is a transitive of the other resolve successfully'() {
        given:
        repository {
            'jakarta:activation:1.0'()
            'jakarta:xml:1.0' {
                dependsOn 'jakarta:activation:1.0'
            }
            'javax:jaxb:1.0'()
            'jakarta:xml:2.0'()
            'sun:jaxb:1.0' {
                dependsOn 'jakarta:xml:2.0'
            }
            'jersey:json:1.0' {
                dependsOn 'sun:jaxb:1.0'
            }
            'hadoop:yarn:1.0' {
                dependsOn 'javax:jaxb:1.0'
                dependsOn 'jersey:json:1.0'
            }
            'javax:activation:1.0'()
        }

        buildFile << """
project.dependencies.components.all { ComponentMetadataDetails details ->
    def groupName = details.getId().getGroup() + ':' + details.getId().getName();

    String capabilityGroup = null
    String capabilityName = null
    if (groupName == 'jakarta:xml' || groupName == 'javax:jaxb') {
        capabilityGroup = 'capabilities'
        capabilityName = 'xml'
    }

    if (groupName == 'javax:activation' || groupName == 'jakarta:activation') {
        capabilityGroup = 'capabilities'
        capabilityName = 'activation'
    }

    if (capabilityGroup != null) {
        details.allVariants { v ->
            v.withCapabilities { m ->
                m.addCapability(capabilityGroup, capabilityName, '0')
            }
        }
    }
}

configurations.configureEach {
    resolutionStrategy {
        capabilitiesResolution {
            withCapability('capabilities', 'xml') {
                select('jakarta:xml:1.0')
            }
            withCapability('capabilities', 'activation') {
                select('jakarta:activation:1.0')
            }
        }
        eachDependency { details ->
            if (details.requested.name == 'xml' && details.requested.group == 'jakarta') {
                details.useVersion('1.0')
            }
        }
    }
}

dependencies {
    conf 'jakarta:xml'
    conf 'hadoop:yarn:1.0'
    conf 'javax:activation:1.0'
}
"""
        when:
        repositoryInteractions {
            'jakarta:activation:1.0' {
                expectResolve()
            }
            'jakarta:xml:1.0' {
                expectResolve()
            }
            'javax:jaxb:1.0' {
                expectGetMetadata()
            }
            'sun:jaxb:1.0' {
                expectResolve()
            }
            'jersey:json:1.0' {
                expectResolve()
            }
            'hadoop:yarn:1.0' {
                expectResolve()
            }
            'javax:activation:1.0' {
                expectGetMetadata()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('jakarta:xml', 'jakarta:xml:1.0') {
                    byConflictResolution()
                    selectedByRule()
                    module('jakarta:activation:1.0') {
                        byConflictResolution()
                    }
                }
                module('hadoop:yarn:1.0') {
                    edge('javax:jaxb:1.0', 'jakarta:xml:1.0')
                    module('jersey:json:1.0') {
                        module('sun:jaxb:1.0') {
                            edge('jakarta:xml:2.0', 'jakarta:xml:1.0')
                        }
                    }
                }
                edge('javax:activation:1.0', 'jakarta:activation:1.0')
            }
        }
    }
}
