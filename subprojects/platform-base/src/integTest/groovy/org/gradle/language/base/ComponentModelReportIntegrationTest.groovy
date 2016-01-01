/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.base

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class ComponentModelReportIntegrationTest extends AbstractIntegrationSpec {

    def "model report for unmanaged software components show them all"() {
        given:
        buildFile << """
            ${registerAllTypes()}
            ${declareSoftwareComponents('UnmanagedComponent', 'UnmanagedBinary', 'UnmanagedLanguageSourceSet')}
        """.stripIndent()

        when:
        succeeds 'model'

        then:
        output.contains """
            + components
                  | Type:   	org.gradle.platform.base.ComponentSpecContainer
                  | Creator: 	ComponentModelBasePlugin.Rules#components
                  | Rules:
                     ⤷ components { ... } @ build.gradle line 90, column 5
                + myComponent
                      | Type:   	UnmanagedComponent
                      | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 91, column 9
                      | Rules:
                         ⤷ ComponentRules#addSourcesSetsToProjectSourceSet
                         ⤷ ComponentRules#applyDefaultSourceConventions
                         ⤷ ComponentRules#initializeSourceSets
                         ⤷ ComponentBinaryRules#initializeBinarySourceSets
                         ⤷ DeclarationRules#mutateMyComponent
                    + binaries
                          | Type:   	org.gradle.model.ModelMap<org.gradle.platform.base.BinarySpec>
                          | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 91, column 9
                        + myBinary
                              | Type:   	UnmanagedBinary
                              | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 91, column 9 > create(myBinary)
                              | Rules:
                                 ⤷ ComponentBinaryRules#initializeBinarySourceSets > beforeEach()
                                 ⤷ DeclarationRules#mutateMyBinary
                            + sources
                                  | Type:   	org.gradle.model.ModelMap<org.gradle.language.base.LanguageSourceSet>
                                  | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 91, column 9 > create(myBinary)
                                + myBinarySource
                                      | Type:   	UnmanagedLanguageSourceSet
                                      | Value:  	UnmanagedLanguageSourceSet 'myComponent:myBinarySource'
                                      | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 91, column 9 > create(myBinary) > create(myBinarySource)
                                      | Rules:
                                         ⤷ DeclarationRules#mutateMyBinarySource
                            + tasks
                                  | Type:   	org.gradle.platform.base.BinaryTasksCollection
                                  | Value:  	[]
                                  | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 91, column 9 > create(myBinary)
                    + sources
                          | Type:   	org.gradle.model.ModelMap<org.gradle.language.base.LanguageSourceSet>
                          | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 91, column 9
                        + myComponentSource
                              | Type:   	UnmanagedLanguageSourceSet
                              | Value:  	UnmanagedLanguageSourceSet 'myComponent:myComponentSource'
                              | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 91, column 9 > create(myComponentSource)
                              | Rules:
                                 ⤷ DeclarationRules#mutateMyComponentSource
                                 ⤷ ComponentRules#addSourcesSetsToProjectSourceSet > afterEach()
                                 ⤷ ComponentRules#applyDefaultSourceConventions > afterEach()
        """.stripIndent().trim()
    }

    def "model report for managed software components show them all with their managed properties"() {
        given:
        buildFile << """
            ${registerAllTypes()}
            ${declareSoftwareComponents('ManagedComponent', 'ManagedBinary', 'ManagedLanguageSourceSet')}
        """.stripIndent()

        when:
        succeeds 'model'

        then:
        output.contains """
            + components
                  | Type:   	org.gradle.platform.base.ComponentSpecContainer
                  | Creator: 	ComponentModelBasePlugin.Rules#components
                  | Rules:
                     ⤷ components { ... } @ build.gradle line 90, column 5
                + myComponent
                      | Type:   	ManagedComponent
                      | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 91, column 9
                      | Rules:
                         ⤷ ComponentRules#addSourcesSetsToProjectSourceSet
                         ⤷ ComponentRules#applyDefaultSourceConventions
                         ⤷ ComponentRules#initializeSourceSets
                         ⤷ ComponentBinaryRules#initializeBinarySourceSets
                         ⤷ DeclarationRules#mutateMyComponent
                    + binaries
                          | Type:   	org.gradle.model.ModelMap<org.gradle.platform.base.BinarySpec>
                          | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 91, column 9
                        + myBinary
                              | Type:   	ManagedBinary
                              | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 91, column 9 > create(myBinary)
                              | Rules:
                                 ⤷ ComponentBinaryRules#initializeBinarySourceSets > beforeEach()
                                 ⤷ DeclarationRules#mutateMyBinary
                            + data
                                  | Type:   	java.lang.String
                                  | Value:  	my binary
                                  | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 91, column 9 > create(myBinary)
                            + sources
                                  | Type:   	org.gradle.model.ModelMap<org.gradle.language.base.LanguageSourceSet>
                                  | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 91, column 9 > create(myBinary)
                                + myBinarySource
                                      | Type:   	ManagedLanguageSourceSet
                                      | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 91, column 9 > create(myBinary) > create(myBinarySource)
                                      | Rules:
                                         ⤷ DeclarationRules#mutateMyBinarySource
                                    + data
                                          | Type:   	java.lang.String
                                          | Value:  	my binary sources
                                          | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 91, column 9 > create(myBinary) > create(myBinarySource)
                            + tasks
                                  | Type:   	org.gradle.platform.base.BinaryTasksCollection
                                  | Value:  	[]
                                  | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 91, column 9 > create(myBinary)
                    + data
                          | Type:   	java.lang.String
                          | Value:  	my component
                          | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 91, column 9
                    + sources
                          | Type:   	org.gradle.model.ModelMap<org.gradle.language.base.LanguageSourceSet>
                          | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 91, column 9
                        + myComponentSource
                              | Type:   	ManagedLanguageSourceSet
                              | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 91, column 9 > create(myComponentSource)
                              | Rules:
                                 ⤷ DeclarationRules#mutateMyComponentSource
                                 ⤷ ComponentRules#addSourcesSetsToProjectSourceSet > afterEach()
                                 ⤷ ComponentRules#applyDefaultSourceConventions > afterEach()
                            + data
                                  | Type:   	java.lang.String
                                  | Value:  	my component sources
                                  | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 91, column 9 > create(myComponentSource)
        """.stripIndent().trim()
    }

    @Unroll
    def "components report for #componentType / #binaryType / #sourceType show all software components"() {
        given:
        buildFile << """
            ${registerAllTypes()}
            ${declareSoftwareComponents(componentType, binaryType, sourceType)}
        """.stripIndent()

        when:
        succeeds 'components'

        then:
        output.contains """
            $componentType 'myComponent'
            ${'-'.multiply((componentType+" 'myComponent'").length())}

            Source sets
                $sourceType 'myComponent:myComponentSource'
                    srcDir: src${File.separator}myComponent${File.separator}myComponentSource

            Binaries
                $binaryType 'myComponent:myBinary'
                    build using task: :myComponentMyBinary
                    source sets:
                        $sourceType 'myComponent:myBinarySource'
                            No source directories
            """.stripIndent()

        where:
        componentType        | binaryType        | sourceType
        'UnmanagedComponent' | 'UnmanagedBinary' | 'UnmanagedLanguageSourceSet'
        'UnmanagedComponent' | 'UnmanagedBinary' | 'ManagedLanguageSourceSet'
        'UnmanagedComponent' | 'ManagedBinary'   | 'UnmanagedLanguageSourceSet'
        'ManagedComponent'   | 'UnmanagedBinary' | 'UnmanagedLanguageSourceSet'
        'UnmanagedComponent' | 'ManagedBinary'   | 'ManagedLanguageSourceSet'
        'ManagedComponent'   | 'UnmanagedBinary' | 'ManagedLanguageSourceSet'
        'ManagedComponent'   | 'ManagedBinary'   | 'UnmanagedLanguageSourceSet'
        'ManagedComponent'   | 'ManagedBinary'   | 'ManagedLanguageSourceSet'
    }

    def declareSoftwareComponents(componentType, binaryType, sourceType) {
        return """
            model {
                components {
                    myComponent($componentType) {
                        sources {
                            myComponentSource($sourceType)
                        }
                        binaries {
                            myBinary($binaryType) {
                                sources {
                                    myBinarySource($sourceType)
                                }
                            }
                        }
                    }
                }
            }
            class DeclarationRules extends RuleSource {
                @Mutate
                void mutateMyComponent(@Path("components.myComponent") $componentType component) {
                    component.data = "my component"
                }
                @Mutate
                void mutateMyComponentSource(@Path("components.myComponent.sources.myComponentSource") $sourceType componentSourceSet) {
                    componentSourceSet.data = "my component sources"
                }
                @Mutate
                void mutateMyBinary(@Path("components.myComponent.binaries.myBinary") $binaryType binary) {
                    binary.data = "my binary"
                }
                @Mutate
                void mutateMyBinarySource(@Path("components.myComponent.binaries.myBinary.sources.myBinarySource") $sourceType binarySourceSet) {
                    binarySourceSet.data = "my binary sources"
                }
            }
            apply plugin: DeclarationRules
        """.stripIndent()
    }

    def registerAllTypes() {
        return """
            ${registerUnmanagedComponent()}
            ${registerManagedComponent()}
            ${registerUnmanagedBinary()}
            ${registerManagedBinary()}
            ${registerUnmanagedLanguageSourceSet()}
            ${registerManagedLanguageSourceSet()}
        """.stripIndent()
    }

    def registerUnmanagedComponent() {
        return """
            interface UnmanagedComponent extends ComponentSpec {
                String getData()
                void setData(String data)
            }
            class DefaultUnmanagedComponent extends BaseComponentSpec implements UnmanagedComponent {
                String data
            }
            class UnmanagedComponentPlugin extends RuleSource {
                @ComponentType
                void registerUnmanagedComponent(ComponentTypeBuilder<UnmanagedComponent> builder) {
                    builder.defaultImplementation(DefaultUnmanagedComponent)
                }
            }
            apply plugin: UnmanagedComponentPlugin
        """.stripIndent()
    }

    def registerManagedComponent() {
        return """
            @Managed interface ManagedComponent extends ComponentSpec {
                String getData()
                void setData(String data)
            }
            class ManagedComponentPlugin extends RuleSource {
                @ComponentType
                void registerManagedComponent(ComponentTypeBuilder<ManagedComponent> builder) {}
            }
            apply plugin: ManagedComponentPlugin
        """.stripIndent()
    }

    def registerUnmanagedBinary() {
        return """
            interface UnmanagedBinary extends BinarySpec {
                String getData()
                void setData(String data)
            }
            class DefaultUnmanagedBinary extends BaseBinarySpec implements UnmanagedBinary {
                String data
            }
            class UnmanagedBinaryPlugin extends RuleSource {
                @BinaryType
                void registerUnmanagedBinary(BinaryTypeBuilder<UnmanagedBinary> builder) {
                    builder.defaultImplementation(DefaultUnmanagedBinary)
                }
            }
            apply plugin: UnmanagedBinaryPlugin
        """.stripIndent()
    }

    def registerManagedBinary() {
        return """
            @Managed interface ManagedBinary extends BinarySpec {
                String getData()
                void setData(String data)
            }
            class ManagedBinaryPlugin extends RuleSource {
                @BinaryType
                void registerUnmanagedBinary(BinaryTypeBuilder<ManagedBinary> builder) {}
            }
            apply plugin: ManagedBinaryPlugin
        """.stripIndent()
    }

    def registerUnmanagedLanguageSourceSet() {
        return """
            interface UnmanagedLanguageSourceSet extends LanguageSourceSet {
                String getData()
                void setData(String data)
            }
            class DefaultUnmanagedLanguageSourceSet extends BaseLanguageSourceSet implements UnmanagedLanguageSourceSet {
                String data
            }
            class UnmanagedLanguageSourceSetPlugin extends RuleSource {
                @LanguageType
                void registerUnamanagedLanguageSourceSet(LanguageTypeBuilder<UnmanagedLanguageSourceSet> builder) {
                    builder.setLanguageName("lang")
                    builder.defaultImplementation(DefaultUnmanagedLanguageSourceSet)
                }
            }
            apply plugin: UnmanagedLanguageSourceSetPlugin
        """.stripIndent()
    }

    def registerManagedLanguageSourceSet() {
        return """
            @Managed interface ManagedLanguageSourceSet extends LanguageSourceSet {
                String getData()
                void setData(String data)
            }
            class ManagedLanguageSourceSetPlugin extends RuleSource {
                @LanguageType
                void registerUnamanagedLanguageSourceSet(LanguageTypeBuilder<ManagedLanguageSourceSet> builder) {\n\
                    builder.setLanguageName("lang")
                }
            }
            apply plugin: ManagedLanguageSourceSetPlugin
        """.stripIndent()
    }
}
