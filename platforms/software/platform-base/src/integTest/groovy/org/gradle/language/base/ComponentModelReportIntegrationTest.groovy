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
import org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class ComponentModelReportIntegrationTest extends AbstractIntegrationSpec implements StableConfigurationCacheDeprecations {

    def "model report for unmanaged software components shows them all"() {
        given:
        buildFile << """
            ${registerAllTypes()}
            ${declareSoftwareComponents('UnmanagedComponent', 'UnmanagedBinary', 'UnmanagedLanguageSourceSet')}
        """.stripIndent()

        when:
        expectTaskGetProjectDeprecations()
        succeeds 'model'

        then:
        output.contains """
            + components
                  | Type:   	org.gradle.platform.base.ComponentSpecContainer
                  | Creator: 	ComponentBasePlugin.PluginRules#components(ComponentSpecContainer)
                  | Rules:
                     ⤷ components { ... } @ build.gradle line 88, column 5
                + myComponent
                      | Type:   	UnmanagedComponent
                      | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 89, column 9
                      | Rules:
                         ⤷ ComponentModelBasePlugin.PluginRules#addComponentSourcesSetsToProjectSourceSet(SourceComponentSpec, ProjectSourceSet)
                         ⤷ ComponentModelBasePlugin.PluginRules#inputRules(ComponentModelBasePlugin.PluginRules.AttachInputs, GeneralComponentSpec)
                         ⤷ DeclarationRules#mutateMyComponent(UnmanagedComponent)
                    + binaries
                          | Type:   	org.gradle.model.ModelMap<org.gradle.platform.base.BinarySpec>
                          | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 89, column 9
                          | Rules:
                             ⤷ ComponentModelBasePlugin.PluginRules.AttachInputs#initializeBinarySourceSets(ModelMap<BinarySpec>)
                        + myBinary
                              | Type:   	UnmanagedBinary
                              | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 89, column 9 > create(myBinary)
                              | Rules:
                                 ⤷ DeclarationRules#mutateMyBinary(UnmanagedBinary)
                                 ⤷ ComponentModelBasePlugin.PluginRules.AttachInputs#initializeBinarySourceSets(ModelMap<BinarySpec>) > withType()
                                 ⤷ BinaryBasePlugin.Rules#defineBuildLifecycleTask(BinarySpecInternal, NamedEntityInstantiator<Task>)
                                 ⤷ BinaryBasePlugin.Rules#addSourceSetsOwnedByBinariesToTheirInputs(BinarySpecInternal)
                                 ⤷ ComponentModelBasePlugin.PluginRules#defineBinariesCheckTasks(BinarySpecInternal, NamedEntityInstantiator<Task>)
                            + sources
                                  | Type:   	org.gradle.model.ModelMap<org.gradle.language.base.LanguageSourceSet>
                                  | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 89, column 9 > create(myBinary)
                                + myBinarySource
                                      | Type:   	UnmanagedLanguageSourceSet
                                      | Value:  	Unmanaged source 'myComponent:myBinary:myBinarySource'
                                      | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 89, column 9 > create(myBinary) > create(myBinarySource)
                                      | Rules:
                                         ⤷ DeclarationRules#mutateMyBinarySource(UnmanagedLanguageSourceSet)
                                         ⤷ ComponentModelBasePlugin.PluginRules#applyFallbackSourceConventions(LanguageSourceSet, ProjectIdentifier)
                            + tasks
                                  | Type:   	org.gradle.platform.base.BinaryTasksCollection
                                  | Value:  	Task collection
                                  | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 89, column 9 > create(myBinary)
                    + sources
                          | Type:   	org.gradle.model.ModelMap<org.gradle.language.base.LanguageSourceSet>
                          | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 89, column 9
                        + myComponentSource
                              | Type:   	UnmanagedLanguageSourceSet
                              | Value:  	Unmanaged source 'myComponent:myComponentSource'
                              | Creator: 	myComponent(UnmanagedComponent) { ... } @ build.gradle line 89, column 9 > create(myComponentSource)
                              | Rules:
                                 ⤷ DeclarationRules#mutateMyComponentSource(UnmanagedLanguageSourceSet)
                                 ⤷ ComponentModelBasePlugin.PluginRules#addComponentSourcesSetsToProjectSourceSet(SourceComponentSpec, ProjectSourceSet) > afterEach()
                                 ⤷ ComponentModelBasePlugin.PluginRules#applyFallbackSourceConventions(LanguageSourceSet, ProjectIdentifier)
            """.stripIndent().trim()
    }

    def "model report for managed software components show them all with their managed properties"() {
        given:
        buildFile << """
            ${registerAllTypes()}
            ${declareSoftwareComponents('ManagedComponent', 'ManagedBinary', 'ManagedLanguageSourceSet')}
        """.stripIndent()

        when:
        expectTaskGetProjectDeprecations()
        succeeds 'model'

        then:
        output.contains """
            + components
                  | Type:   	org.gradle.platform.base.ComponentSpecContainer
                  | Creator: 	ComponentBasePlugin.PluginRules#components(ComponentSpecContainer)
                  | Rules:
                     ⤷ components { ... } @ build.gradle line 88, column 5
                + myComponent
                      | Type:   	ManagedComponent
                      | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 89, column 9
                      | Rules:
                         ⤷ ComponentModelBasePlugin.PluginRules#addComponentSourcesSetsToProjectSourceSet(SourceComponentSpec, ProjectSourceSet)
                         ⤷ ComponentModelBasePlugin.PluginRules#inputRules(ComponentModelBasePlugin.PluginRules.AttachInputs, GeneralComponentSpec)
                         ⤷ DeclarationRules#mutateMyComponent(ManagedComponent)
                    + binaries
                          | Type:   	org.gradle.model.ModelMap<org.gradle.platform.base.BinarySpec>
                          | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 89, column 9
                          | Rules:
                             ⤷ ComponentModelBasePlugin.PluginRules.AttachInputs#initializeBinarySourceSets(ModelMap<BinarySpec>)
                        + myBinary
                              | Type:   	ManagedBinary
                              | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 89, column 9 > create(myBinary)
                              | Rules:
                                 ⤷ DeclarationRules#mutateMyBinary(ManagedBinary)
                                 ⤷ ComponentModelBasePlugin.PluginRules.AttachInputs#initializeBinarySourceSets(ModelMap<BinarySpec>) > withType()
                                 ⤷ BinaryBasePlugin.Rules#defineBuildLifecycleTask(BinarySpecInternal, NamedEntityInstantiator<Task>)
                                 ⤷ BinaryBasePlugin.Rules#addSourceSetsOwnedByBinariesToTheirInputs(BinarySpecInternal)
                                 ⤷ ComponentModelBasePlugin.PluginRules#defineBinariesCheckTasks(BinarySpecInternal, NamedEntityInstantiator<Task>)
                            + data
                                  | Type:   	java.lang.String
                                  | Value:  	my binary
                                  | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 89, column 9 > create(myBinary)
                            + sources
                                  | Type:   	org.gradle.model.ModelMap<org.gradle.language.base.LanguageSourceSet>
                                  | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 89, column 9 > create(myBinary)
                                + myBinarySource
                                      | Type:   	ManagedLanguageSourceSet
                                      | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 89, column 9 > create(myBinary) > create(myBinarySource)
                                      | Rules:
                                         ⤷ DeclarationRules#mutateMyBinarySource(ManagedLanguageSourceSet)
                                         ⤷ ComponentModelBasePlugin.PluginRules#applyFallbackSourceConventions(LanguageSourceSet, ProjectIdentifier)
                                    + data
                                          | Type:   	java.lang.String
                                          | Value:  	my binary sources
                                          | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 89, column 9 > create(myBinary) > create(myBinarySource)
                            + tasks
                                  | Type:   	org.gradle.platform.base.BinaryTasksCollection
                                  | Value:  	Task collection
                                  | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 89, column 9 > create(myBinary)
                    + data
                          | Type:   	java.lang.String
                          | Value:  	my component
                          | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 89, column 9
                    + sources
                          | Type:   	org.gradle.model.ModelMap<org.gradle.language.base.LanguageSourceSet>
                          | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 89, column 9
                        + myComponentSource
                              | Type:   	ManagedLanguageSourceSet
                              | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 89, column 9 > create(myComponentSource)
                              | Rules:
                                 ⤷ DeclarationRules#mutateMyComponentSource(ManagedLanguageSourceSet)
                                 ⤷ ComponentModelBasePlugin.PluginRules#addComponentSourcesSetsToProjectSourceSet(SourceComponentSpec, ProjectSourceSet) > afterEach()
                                 ⤷ ComponentModelBasePlugin.PluginRules#applyFallbackSourceConventions(LanguageSourceSet, ProjectIdentifier)
                            + data
                                  | Type:   	java.lang.String
                                  | Value:  	my component sources
                                  | Creator: 	myComponent(ManagedComponent) { ... } @ build.gradle line 89, column 9 > create(myComponentSource)
            """.stripIndent().trim()
    }

    def "components report for #componentType / #binaryType / #sourceType show all software components"() {
        given:
        buildFile << """
            ${registerAllTypes()}
            ${declareSoftwareComponents(componentType, binaryType, sourceType)}
        """.stripIndent()

        when:
        expectTaskGetProjectDeprecations()
        succeeds 'components'

        then:
        output.contains """
            $componentType 'myComponent'
            ${'-'.multiply("$componentType 'myComponent'".length())}

            Source sets
                $languageName source 'myComponent:myComponentSource'
                    srcDir: src${File.separator}myComponent${File.separator}myComponentSource

            Binaries
                $binaryType 'myComponent:myBinary'
                    build using task: :myComponentMyBinary
                    source sets:
                        $languageName source 'myComponent:myBinary:myBinarySource'
                            srcDir: src${File.separator}myBinary${File.separator}myBinarySource
            """.stripIndent()

        where:
        componentType        | binaryType        | sourceType                   | languageName
        'UnmanagedComponent' | 'UnmanagedBinary' | 'UnmanagedLanguageSourceSet' | "Unmanaged"
        'UnmanagedComponent' | 'UnmanagedBinary' | 'ManagedLanguageSourceSet'   | "Managed"
        'UnmanagedComponent' | 'ManagedBinary'   | 'UnmanagedLanguageSourceSet' | "Unmanaged"
        'UnmanagedComponent' | 'ManagedBinary'   | 'ManagedLanguageSourceSet'   | "Managed"
        'ManagedComponent'   | 'UnmanagedBinary' | 'UnmanagedLanguageSourceSet' | "Unmanaged"
        'ManagedComponent'   | 'UnmanagedBinary' | 'ManagedLanguageSourceSet'   | "Managed"
        'ManagedComponent'   | 'ManagedBinary'   | 'UnmanagedLanguageSourceSet' | "Unmanaged"
        'ManagedComponent'   | 'ManagedBinary'   | 'ManagedLanguageSourceSet'   | "Managed"
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
            interface UnmanagedComponent extends GeneralComponentSpec {
                String getData()
                void setData(String data)
            }
            class DefaultUnmanagedComponent extends BaseComponentSpec implements UnmanagedComponent {
                String data
            }
            class UnmanagedComponentPlugin extends RuleSource {
                @ComponentType
                void registerUnmanagedComponent(TypeBuilder<UnmanagedComponent> builder) {
                    builder.defaultImplementation(DefaultUnmanagedComponent)
                }
            }
            apply plugin: UnmanagedComponentPlugin
        """.stripIndent()
    }

    def registerManagedComponent() {
        return """
            @Managed interface ManagedComponent extends GeneralComponentSpec {
                String getData()
                void setData(String data)
            }
            class ManagedComponentPlugin extends RuleSource {
                @ComponentType
                void registerManagedComponent(TypeBuilder<ManagedComponent> builder) {}
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
                @ComponentType
                void registerUnmanagedBinary(TypeBuilder<UnmanagedBinary> builder) {
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
                @ComponentType
                void registerUnmanagedBinary(TypeBuilder<ManagedBinary> builder) {}
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
                @ComponentType
                void registerUnmanagedLanguageSourceSet(TypeBuilder<UnmanagedLanguageSourceSet> builder) {
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
                @ComponentType
                void registerUnmanagedLanguageSourceSet(TypeBuilder<ManagedLanguageSourceSet> builder) {
                }
            }
            apply plugin: ManagedLanguageSourceSetPlugin
        """.stripIndent()
    }
}
