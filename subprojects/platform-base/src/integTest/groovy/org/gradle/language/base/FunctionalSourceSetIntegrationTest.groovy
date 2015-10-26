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

import groovy.transform.NotYetImplemented
import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.TextUtil

class FunctionalSourceSetIntegrationTest extends AbstractIntegrationSpec {

    def "can not create a top level FSS when the language base plugin has not been applied"() {
        buildFile.text = """
        class Rules extends RuleSource {
            @Model
            void functionalSources(FunctionalSourceSet sources) {
            }
        }
        apply plugin: Rules
        """

        when:
        fails "model"

        then:
        failureCauseContains("Declaration of model rule Rules#functionalSources is invalid.")
        failureCauseContains("A model element of type: 'org.gradle.language.base.FunctionalSourceSet' can not be constructed.")
    }

    def "can create a top level functional source set with a rule"() {
        buildScript """
        apply plugin: 'language-base'

        class Rules extends RuleSource {
            @Model
            void functionalSources(FunctionalSourceSet sources) {

            }

            @Mutate void printTask(ModelMap<Task> tasks, FunctionalSourceSet sources) {
                tasks.create("printTask") {
                  doLast {
                    println "FunctionalSourceSet: \$sources"
                  }
              }
            }

        }
        apply plugin: Rules
        """

        expect:
        succeeds "printTask"
        output.contains("FunctionalSourceSet: []")
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

    def "model report renders a functional source set"() {
        buildFile << """
        apply plugin: 'language-base'

        model {
            functionalSources(FunctionalSourceSet)
        }
        """

        when:
        succeeds "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        modelNode.functionalSources.@creator[0] == "model.functionalSources @ build.gradle line 5, column 13"
        modelNode.functionalSources.@type[0] == "org.gradle.language.base.FunctionalSourceSet"
        modelNode.functionalSources.@nodeValue[0] == "source set 'functionalSources'"
    }

    def "can define a FunctionalSourceSet as a property of a managed type"() {
        buildFile << """
        apply plugin: 'language-base'

        @Managed
        interface BuildType {
            //Readonly
            FunctionalSourceSet getSources()

            //Read/write
            FunctionalSourceSet getInputs()
            void setInputs(FunctionalSourceSet sources)
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

        buildType.inputs.@type[0] == 'org.gradle.language.base.FunctionalSourceSet'
        buildType.inputs.@nodeValue[0] == "source set 'inputs'"
        buildType.inputs.@creator[0] == 'Rules#buildType'

        buildType.sources.@type[0] == 'org.gradle.language.base.FunctionalSourceSet'
        buildType.sources.@nodeValue[0] == "source set 'sources'"
        buildType.sources.@creator[0] == 'Rules#buildType'
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
        buildType.componentSources.@creator[0] == 'Rules#buildType'
        buildType.componentSources.componentA.@type[0] == 'org.gradle.language.base.FunctionalSourceSet'
        buildType.componentSources.componentA.@nodeValue[0] == "source set 'componentA'"
        buildType.componentSources.componentA.@creator[0] == 'Rules#addSources > create(componentA)'

        buildType.testSources.@type[0] == 'org.gradle.model.ModelSet<org.gradle.language.base.FunctionalSourceSet>'
        buildType.testSources.@creator[0] == 'Rules#buildType'
        buildType.testSources."0".@type[0] == 'org.gradle.language.base.FunctionalSourceSet'
        buildType.testSources."0".@nodeValue[0] == "source set '0'"
        buildType.testSources."0".@creator[0] == 'Rules#addSources > create()'
    }

    def "can register a language source set"() {
        buildScript """
        apply plugin: 'language-base'

        ${registerJavaLanguage()}

        class Rules extends RuleSource {
            @Model
            void functionalSources(FunctionalSourceSet sources) {
                sources.create("javaB", JavaSourceSet)
            }
        }
        apply plugin: Rules
        """
        expect:
        succeeds "model"
    }

    def "can register a language source set via the model dsl"() {
        buildFile << """
        ${registerJavaLanguage()}

        model {
            functionalSources(FunctionalSourceSet){
                myJavaSourceSet(JavaSourceSet)
            }
        }
        """

        when:
        succeeds "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        modelNode.functionalSources.@nodeValue[0] == "source set 'functionalSources'"
        modelNode.sources.@nodeValue[0] == "[Java source 'functionalSources:myJavaSourceSet']"
    }

    @NotYetImplemented
    def "a LSS is initialized with a default source set"() {
        buildFile << """
        ${registerJavaLanguage()}

        model {
            functionalSources(FunctionalSourceSet){
                myJavaSourceSet(JavaSourceSet)
            }
        }

        class Rules extends RuleSource {
            @Mutate void printTask(ModelMap<Task> tasks, FunctionalSourceSet fss) {
                tasks.create("verify") {
                  doLast {
                    assert TextUtil.normaliseFileSeparators(fss.getByName("myJavaSourceSet").source.getSrcDirs()[0].path) == '${TextUtil.normaliseFileSeparators(testDirectory.path)}/src/functionalSources/myJavaSourceSet'
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
            import org.gradle.language.java.internal.DefaultJavaLanguageSourceSet

            class JavaLangRuleSource extends RuleSource {

            @LanguageType
            void registerLanguage(LanguageTypeBuilder<JavaSourceSet> builder) {
                builder.setLanguageName("java");
                builder.defaultImplementation(DefaultJavaLanguageSourceSet.class);
            }

        }
        apply plugin: JavaLangRuleSource
        """
    }

}
