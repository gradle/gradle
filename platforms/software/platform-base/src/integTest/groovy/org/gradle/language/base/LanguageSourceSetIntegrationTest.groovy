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

import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

@UnsupportedWithConfigurationCache(because = "software model")
class LanguageSourceSetIntegrationTest extends AbstractIntegrationSpec {

    def "can not create a top level LSS for using an implementation class"() {
        buildFile.text = """
        ${registerCustomLanguageWithImpl()}

        class Rules extends RuleSource {
            @Model
            void lss(DefaultCustomSourceSet source) {
            }
        }
        apply plugin: Rules
        """

        when:
        fails "model"

        then:
        failure.assertHasCause("Cannot create an instance of type 'DefaultCustomSourceSet' as this type is not known. Known types: org.gradle.platform.base.ComponentSpec, CustomSourceSet, ${LanguageSourceSet.name}")
    }

    def "can create a top level LSS with a rule"() {
        buildScript """
        ${registerCustomLanguage()}

        ${addPrintSourceDirTask()}

        class Rules extends RuleSource {
            @Model
            void lss(CustomSourceSet sourceSet) {
                sourceSet.source.srcDir("src/main/lss")
            }
        }
        apply plugin: Rules
        """

        expect:
        succeeds("model", "printSourceDirs")
        normaliseFileSeparators(output).contains("${normaliseFileSeparators(testDirectory.path)}/src/main/lss")
    }

    def "can create a top level LSS via the model DSL"() {
        buildFile.text = """
        ${registerCustomLanguage()}

        model {
            lss(CustomSourceSet)
        }
        """

        when:
        succeeds "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        modelNode.lss.@creator[0] == "lss(CustomSourceSet) @ build.gradle line 13, column 13"
        modelNode.lss.@type[0] == "CustomSourceSet"
    }

    def "can create a LSS as property of a managed type"() {
        buildFile << """
        ${registerCustomLanguage()}

        @Managed
        interface BuildType {
            //Readonly
            CustomSourceSet getSources()

            //Read/write
            CustomSourceSet getInputs()
            void setInputs(CustomSourceSet sources)
        }

        class Rules extends RuleSource {
            @Model
            void buildType(BuildType buildType) { }
        }

        apply plugin: Rules
        """

        expect:
        succeeds "model"
        def buildType = ModelReportOutput.from(output).modelNode.buildType

        buildType.inputs.@type[0] == 'CustomSourceSet'
        buildType.inputs.@nodeValue[0] == 'null'
        buildType.inputs.@creator[0] == 'Rules#buildType(BuildType)'

        buildType.sources.@type[0] == 'CustomSourceSet'
        buildType.sources.@nodeValue[0] == "Custom source 'sources'"
        buildType.sources.@creator[0] == 'Rules#buildType(BuildType)'
    }

    def "An LSS can be an element of managed collections"() {
        buildFile << """
        ${registerCustomLanguage()}

        @Managed
        interface BuildType {
            ModelMap<CustomSourceSet> getComponentSources()
            ModelSet<CustomSourceSet> getTestSources()
        }

        class Rules extends RuleSource {
            @Model
            void buildType(BuildType buildType) { }

            @Mutate
            void addSources(BuildType buildType){
                buildType.componentSources.create("componentA")
                buildType.testSources.create({})
            }
        }

        apply plugin: Rules
        """

        expect:
        succeeds "model"
        def buildType = ModelReportOutput.from(output).modelNode.buildType

        buildType.componentSources.@type[0] == "org.gradle.model.ModelMap<CustomSourceSet>"
        buildType.componentSources.@creator[0] == 'Rules#buildType(BuildType)'
        buildType.componentSources.componentA.@type[0] == 'CustomSourceSet'
        buildType.componentSources.componentA.@creator[0] == 'Rules#addSources(BuildType) > create(componentA)'

        buildType.testSources.@type[0] == "org.gradle.model.ModelSet<CustomSourceSet>"
        buildType.testSources.@creator[0] == 'Rules#buildType(BuildType)'
        buildType.testSources."0".@type[0] == 'CustomSourceSet'
        buildType.testSources."0".@creator[0] == 'Rules#addSources(BuildType) > create()'
    }

    private String registerCustomLanguage() {
        return """
            @Managed interface CustomSourceSet extends LanguageSourceSet {}
            class CustomSourceSetPlugin extends RuleSource {
                @ComponentType
                void registerCustomLanguage(TypeBuilder<CustomSourceSet> builder) {
                }
            }
            apply plugin: CustomSourceSetPlugin
        """.stripIndent()
    }

    private String registerCustomLanguageWithImpl() {
        return """
            interface CustomSourceSet extends LanguageSourceSet {}
            class DefaultCustomSourceSet extends BaseLanguageSourceSet implements CustomSourceSet {}
            class CustomSourceSetPlugin extends RuleSource {
                @ComponentType
                void registerCustomLanguage(TypeBuilder<CustomSourceSet> builder) {
                    builder.defaultImplementation(DefaultCustomSourceSet)
                }
            }
            apply plugin: CustomSourceSetPlugin
        """.stripIndent()
    }

    private String addPrintSourceDirTask() {
        """
            class PrintSourceDirectoryRules extends RuleSource {
                @Mutate void printTask(ModelMap<Task> tasks, LanguageSourceSet lss) {
                    tasks.create("printSourceDirs") {
                      doLast {
                        println ("source dirs: \${lss.source.getSrcDirs()}")
                      }
                  }
                }
            }
            apply plugin: PrintSourceDirectoryRules
        """.stripIndent()
    }
}
