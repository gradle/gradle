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

import groovy.test.NotYetImplemented
import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

@UnsupportedWithConfigurationCache(because = "software model")
class FunctionalSourceSetIntegrationTest extends AbstractIntegrationSpec {

    def "can create a top level functional source set with a rule"() {
        buildFile """
        apply plugin: 'language-base'

        class Rules extends RuleSource {
            @Model
            void fss(FunctionalSourceSet sources) {
            }

            @Mutate void printTask(ModelMap<Task> tasks, FunctionalSourceSet sources) {
                tasks.create("printTask") {
                    doLast {
                        println "name: " + sources.name
                        println "display-name: " + sources.displayName
                        println "to-string: " + sources.toString()
                    }
                }
            }
        }
        apply plugin: Rules
        """

        expect:
        succeeds "printTask"
        output.contains("name: fss")
        output.contains("display-name: FunctionalSourceSet 'fss'")
        output.contains("to-string: FunctionalSourceSet 'fss'")
    }

    def "can view a functional source set as a ModelElement"() {
        buildFile """
        apply plugin: 'language-base'

        class Rules extends RuleSource {
            @Model
            void fss(FunctionalSourceSet sources) {
            }

            @Mutate void printTask(ModelMap<Task> tasks, @Path("fss") ModelElement sources) {
                tasks.create("printTask") {
                    doLast {
                        println "name: " + sources.name
                        println "display-name: " + sources.displayName
                        println "to-string: " + sources.toString()
                    }
                }
            }
        }
        apply plugin: Rules
        """

        expect:
        succeeds "printTask"
        output.contains("name: fss")
        output.contains("display-name: FunctionalSourceSet 'fss'")
        output.contains("to-string: FunctionalSourceSet 'fss'")
    }

    def "can create a top level functional source set via the model dsl"() {
        buildFile << """
        apply plugin: 'language-base'

        model {
            functionalSources(FunctionalSourceSet)
        }
        """

        expect:
        succeeds "model"
    }

    def "model report renders a functional source set and elements"() {
        buildFile << """
        apply plugin: 'language-base'

        ${registerJavaLanguage()}

        model {
            functionalSources(FunctionalSourceSet) {
                lssElement(SomeJavaSourceSet)
            }
        }
        """

        when:
        succeeds "model"

        then:
        def reportOutput = ModelReportOutput.from(output)
        reportOutput.hasNodeStructure {
            functionalSources {
                lssElement()
            }
        }
        def functionalSourceSetCreator = "functionalSources(org.gradle.language.base.FunctionalSourceSet) { ... } @ build.gradle line 15, column 13"
        reportOutput.modelNode.functionalSources.@type[0] == "org.gradle.language.base.FunctionalSourceSet"
        reportOutput.modelNode.functionalSources.@creator[0] == functionalSourceSetCreator
        reportOutput.modelNode.functionalSources.lssElement.@type[0] == "SomeJavaSourceSet"
        reportOutput.modelNode.functionalSources.lssElement.@creator[0] == functionalSourceSetCreator + " > create(lssElement)"
    }

    def "can define a FunctionalSourceSet as a property of a managed type"() {
        buildFile << """
        apply plugin: 'language-base'

        @Managed
        interface BuildType {
            //Readonly
            FunctionalSourceSet getSources()
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

        buildType.sources.@type[0] == 'org.gradle.language.base.FunctionalSourceSet'
        buildType.sources.@creator[0] == 'Rules#buildType(BuildType)'
    }

    def "can have FunctionalSourceSets as managed collection"() {
        buildFile << """
        apply plugin: 'language-base'

        @Managed
        interface BuildType {
            ModelMap<FunctionalSourceSet> getComponentSources()
            ModelSet<FunctionalSourceSet> getTestSources()
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

        buildType.componentSources.@type[0] == 'org.gradle.model.ModelMap<org.gradle.language.base.FunctionalSourceSet>'
        buildType.componentSources.@creator[0] == 'Rules#buildType(BuildType)'
        buildType.componentSources.componentA.@type[0] == 'org.gradle.language.base.FunctionalSourceSet'
        buildType.componentSources.componentA.@creator[0] == 'Rules#addSources(BuildType) > create(componentA)'

        buildType.testSources.@type[0] == 'org.gradle.model.ModelSet<org.gradle.language.base.FunctionalSourceSet>'
        buildType.testSources.@creator[0] == 'Rules#buildType(BuildType)'
        buildType.testSources."0".@type[0] == 'org.gradle.language.base.FunctionalSourceSet'
        buildType.testSources."0".@creator[0] == 'Rules#addSources(BuildType) > create()'
    }

    def "can register a language source set"() {
        buildFile """
        apply plugin: 'language-base'

        ${registerJavaLanguage()}
        ${addPrintSourceDirTask()}

        class Rules extends RuleSource {
            @Model
            void functionalSources(FunctionalSourceSet sources) {
                sources.create("myJavaSourceSet", SomeJavaSourceSet) { LanguageSourceSet lss ->
                    lss.source.srcDir "src/main/myJavaSourceSet"
                }
            }
        }
        apply plugin: Rules
        """
        expect:
        succeeds ("model", "printSourceDirs")
        normaliseFileSeparators(output).contains("source dirs: [${normaliseFileSeparators(testDirectory.path)}/src/main/myJavaSourceSet]")
    }

    def "non-component language source sets are not added to the project source set"() {
        buildFile << """
        ${registerJavaLanguage()}
        ${addPrintSourceDirTask()}

        model {
            functionalSources(FunctionalSourceSet){
                myJavaSourceSet(SomeJavaSourceSet) {
                    source {
                        srcDir "src/main/myJavaSourceSet"
                    }
                }
            }
        }
        """

        when:
        succeeds ("model", "printSourceDirs")

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        modelNode.functionalSources.myJavaSourceSet.@type[0] == 'SomeJavaSourceSet'
        modelNode.sources.@nodeValue[0]  == 'LanguageSourceSet collection'

        and:
        normaliseFileSeparators(output).contains("source dirs: [${normaliseFileSeparators(testDirectory.path)}/src/main/myJavaSourceSet]")

    }

    def "can reference sourceSet elements in a rule"() {
        given:
        buildFile << registerJavaLanguage()
        buildFile << '''
            model {
                functionalSources(FunctionalSourceSet){
                    myJavaSourceSet(SomeJavaSourceSet) {
                        source {
                            srcDir "src/main/myJavaSourceSet"
                        }
                    }
                }
                tasks {
                    create("printSourceDisplayName") {
                        def sources = $.functionalSources.myJavaSourceSet
                        doLast {
                            println "sources display name: ${sources.displayName}"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printSourceDisplayName"

        then:
        output.contains "sources display name: SomeJava source 'myJavaSourceSet'"
    }

    def "can reference sourceSet elements using specialized type in a rule"() {
        given:
        buildFile << registerJavaLanguage()
        buildFile << '''
            class TaskRules extends RuleSource {
                @Mutate
                void addPrintSourceDisplayNameTask(ModelMap<Task> tasks, @Path("functionalSources.myJavaSourceSet") SomeJavaSourceSet sourceSet) {
                    tasks.create("printSource") {
                        doLast {
                            println "sources display name: ${sourceSet.displayName}"
                        }
                    }
                }
            }

            apply type: TaskRules
            model {
                functionalSources(FunctionalSourceSet){
                    myJavaSourceSet(SomeJavaSourceSet) {
                        source {
                            srcDir "src/main/myJavaSourceSet"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printSource"

        then:
        output.contains "sources display name: SomeJava source 'myJavaSourceSet'"
    }

    def "elements in FunctionalSourceSet are not created when defined"() {
        when:
        buildFile << """
            ${registerJavaLanguage()}
            model {
                functionalSources(FunctionalSourceSet){
                    ss1(SomeJavaSourceSet) {
                        println "created ss1"
                    }
                    beforeEach {
                        println "before \$it.name"
                    }
                    all {
                        println "configured \$it.name"
                    }
                    afterEach {
                        println "after \$it.name"
                    }
                    println "configured functionalSources"
                }
                tasks {
                    verify(Task) {
                        \$.functionalSources
                    }
                }
            }
        """
        then:
        succeeds "verify"
        output.contains '''configured functionalSources
before ss1
created ss1
configured ss1
after ss1
'''
    }

    @NotYetImplemented // Needs the ability to specify a rule for top-level nodes by type
    def "a LSS is initialized with a default source set"() {
        buildFile << """
        ${registerJavaLanguage()}

        model {
            functionalSources(FunctionalSourceSet){
                myJavaSourceSet(SomeJavaSourceSet)
            }
        }

        class Rules extends RuleSource {
            @Mutate void printTask(ModelMap<Task> tasks, FunctionalSourceSet fss) {
                tasks.create("verify") {
                  doLast {
                    assert TextUtil.normaliseFileSeparators(fss.getByName("myJavaSourceSet").source.getSrcDirs()[0].path) == '${normaliseFileSeparators(testDirectory.path)}/src/functionalSources/myJavaSourceSet'
                  }
              }
            }

        }
        apply plugin: Rules
        """

        expect:
        succeeds "verify"
    }

    private String registerJavaLanguage() {
        return """
            @Managed interface SomeJavaSourceSet extends LanguageSourceSet {}
            class JavaLangRuleSource extends RuleSource {
                @ComponentType
                void registerLanguage(TypeBuilder<SomeJavaSourceSet> builder) {
                }
            }
            apply plugin: JavaLangRuleSource
        """.stripIndent()
    }


    private String addPrintSourceDirTask(){
        """
            class PrintSourceDirectoryRules extends RuleSource {
                @Mutate void printTask(ModelMap<Task> tasks, FunctionalSourceSet fss) {
                    tasks.create("printSourceDirs") {
                      doLast {
                        fss.each { lss ->
                            println ("source dirs: \${lss.source.getSrcDirs()}")
                        }
                      }
                  }
                }
            }
            apply plugin: PrintSourceDirectoryRules
        """.stripIndent()
    }

}
