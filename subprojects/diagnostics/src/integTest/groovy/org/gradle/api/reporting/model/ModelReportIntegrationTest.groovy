/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.reporting.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class ModelReportIntegrationTest extends AbstractIntegrationSpec {

    def "displays basic structure of an empty project"() {
        given:
        buildFile

        when:
        run "model"

        then:
        def modelReportOutput = ModelReportOutput.from(output)
        modelReportOutput.hasNodeStructure({
            model() {
                tasks {
                    buildEnvironment()
                    components(nodeValue: "task ':components'", type: 'org.gradle.api.reporting.components.ComponentReport')
                    dependencies()
                    dependencyInsight()
                    dependentComponents()
                    javaToolchains()
                    help()
                    init()
                    model()
                    outgoingVariants()
                    prepareKotlinBuildScriptModel()
                    projects()
                    properties()
                    resovableVariants()
                    tasks()
                    wrapper()
                }
            }
        })
    }

    def "displays collections of scalar types in a human-readable format"() {
        given:
        buildFile << '''

@Managed
interface Container {
   List<String> getLabels()
   List<Integer> getIds()
   List<Double> getValues()
   void setValues(List<Double> values)
}

model {
    container(Container) {
        labels.add 'bug'
        labels.add 'blocker'
    }
}
'''
        when:
        run "model"

        then:
        ModelReportOutput.from(output).hasNodeStructure {
            container {
                ids(type: 'java.util.List<java.lang.Integer>', creator: 'container(Container) { ... } @ build.gradle line 12, column 5', nodeValue: '[]')
                labels(type: 'java.util.List<java.lang.String>', creator: 'container(Container) { ... } @ build.gradle line 12, column 5', nodeValue: "[bug, blocker]")
                values(type: 'java.util.List<java.lang.Double>', creator: 'container(Container) { ... } @ build.gradle line 12, column 5', nodeValue: 'null')
            }
        }
    }

    def "display unset primitive and null scalar values"() {
        given:
        buildFile << '''
            @Managed
            interface Container {

                boolean getPrimitiveBoolean()
                void setPrimitiveBoolean(boolean value)
                char getPrimitiveChar()
                void setPrimitiveChar(char value)
                byte getPrimitiveByte()
                void setPrimitiveByte(byte value)
                short getPrimitiveShort()
                void setPrimitiveShort(short value)
                int getPrimitiveInt()
                void setPrimitiveInt(int value)
                float getPrimitiveFloat()
                void setPrimitiveFloat(float value)
                long getPrimitiveLong()
                void setPrimitiveLong(long value)
                double getPrimitiveDouble()
                void setPrimitiveDouble(double value)

                Boolean getScalarBoolean()
                void setScalarBoolean(Boolean value)
                Character getScalarChar()
                void setScalarChar(Character value)
                Byte getScalarByte()
                void setScalarByte(Byte value)
                Short getScalarShort()
                void setScalarShort(Short value)
                Integer getScalarInt()
                void setScalarInt(Integer value)
                Float getScalarFloat()
                void setScalarFloat(Float value)
                Long getScalarLong()
                void setScalarLong(Long value)
                Double getScalarDouble()
                void setScalarDouble(Double value)

            }

            model {
                container(Container)
            }
            '''.stripIndent()

        when:
        run 'model'

        then:
        ModelReportOutput.from(output).hasNodeStructure {
            container {

                primitiveBoolean(nodeValue: 'false')
                primitiveByte(nodeValue: '0')
                primitiveChar(nodeValue: '')
                primitiveDouble(nodeValue: '0.0')
                primitiveFloat(nodeValue: '0.0')
                primitiveInt(nodeValue: '0')
                primitiveLong(nodeValue: '0')
                primitiveShort(nodeValue: '0')

                scalarBoolean(nodeValue: 'null')
                scalarByte(nodeValue: 'null')
                scalarChar(nodeValue: 'null')
                scalarDouble(nodeValue: 'null')
                scalarFloat(nodeValue: 'null')
                scalarInt(nodeValue: 'null')
                scalarLong(nodeValue: 'null')
                scalarShort(nodeValue: 'null')
            }
        }
    }

    def "displays basic values of a simple model graph with values"() {
        given:
        buildFile << """

@Managed
public interface PasswordCredentials {
    String getUsername()
    String getPassword()
    void setUsername(String s)
    void setPassword(String s)
}


${managedNumbers()}

model {
    primaryCredentials(PasswordCredentials){
        username = 'uname'
        password = 'hunter2'
    }

    nullCredentials(PasswordCredentials) { }
    numbers(Numbers){
        value = 5
        threshold = 0.8
    }
    unsetNumbers(Numbers) { }
}

"""
        buildFile
        when:
        run "model"

        then:
        ModelReportOutput.from(output).hasNodeStructure {
            nullCredentials {
                password(type: 'java.lang.String', creator: 'nullCredentials(PasswordCredentials) { ... } @ build.gradle line 27, column 5')
                username(type: 'java.lang.String', creator: 'nullCredentials(PasswordCredentials) { ... } @ build.gradle line 27, column 5')
            }
            numbers {
                threshold(nodeValue: "0.8")
                value(nodeValue: "5")
            }
            primaryCredentials {
                password(nodeValue: 'hunter2', type: 'java.lang.String', creator: 'primaryCredentials(PasswordCredentials) { ... } @ build.gradle line 22, column 5')
                username(nodeValue: 'uname', type: 'java.lang.String', creator: 'primaryCredentials(PasswordCredentials) { ... } @ build.gradle line 22, column 5')
            }
            unsetNumbers {
                threshold(nodeValue: '0.0')
                value(nodeValue: 'null')
            }
        }
    }

    // nb: specifically doesn't use the parsing fixture, so that the output is visualised
    //If you're changing this you will also need to change: src/snippets/modelRules/basicRuleSourcePlugin/basicRuleSourcePlugin-model-task.out
    def "displays a report in the correct format"() {
        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << """

@Managed
public interface PasswordCredentials {
    String getUsername()
    String getPassword()
    void setUsername(String s)
    void setPassword(String s)
}


${managedNumbers()}

model {
    primaryCredentials(PasswordCredentials){
        username = 'uname'
        password = 'hunter2'
    }

    nullCredentials(PasswordCredentials)
    numbers(Numbers){
        value = 5
        threshold = 0.8
    }
    unsetNumbers(Numbers) { }
}

"""
        buildFile
        when:
        run "model"

        then:
        def modelReportOutput = ModelReportOutput.from(output)
        modelReportOutput.hasTitle("Root project 'test'")

        and:
        modelReportOutput.nodeContentEquals('''
+ nullCredentials
      | Type:   \tPasswordCredentials
      | Creator: \tnullCredentials(PasswordCredentials) @ build.gradle line 27, column 5
    + password
          | Type:   \tjava.lang.String
          | Value:  \tnull
          | Creator: \tnullCredentials(PasswordCredentials) @ build.gradle line 27, column 5
    + username
          | Type:   \tjava.lang.String
          | Value:  \tnull
          | Creator: \tnullCredentials(PasswordCredentials) @ build.gradle line 27, column 5
+ numbers
      | Type:   \tNumbers
      | Creator: \tnumbers(Numbers) { ... } @ build.gradle line 28, column 5
    + threshold
          | Type:   \tdouble
          | Value:  \t0.8
          | Creator: \tnumbers(Numbers) { ... } @ build.gradle line 28, column 5
    + value
          | Type:   \tjava.lang.Integer
          | Value:  \t5
          | Creator: \tnumbers(Numbers) { ... } @ build.gradle line 28, column 5
+ primaryCredentials
      | Type:   \tPasswordCredentials
      | Creator: \tprimaryCredentials(PasswordCredentials) { ... } @ build.gradle line 22, column 5
    + password
          | Type:   \tjava.lang.String
          | Value:  \thunter2
          | Creator: \tprimaryCredentials(PasswordCredentials) { ... } @ build.gradle line 22, column 5
    + username
          | Type:   \tjava.lang.String
          | Value:  \tuname
          | Creator: \tprimaryCredentials(PasswordCredentials) { ... } @ build.gradle line 22, column 5
+ tasks
      | Type:   \torg.gradle.model.ModelMap<org.gradle.api.Task>
      | Creator: \tProject.<init>.tasks()
    + buildEnvironment
          | Type:   \torg.gradle.api.tasks.diagnostics.BuildEnvironmentReportTask
          | Value:  \ttask ':buildEnvironment\'
          | Creator: \tProject.<init>.tasks.buildEnvironment()
          | Rules:
             ⤷ copyToTaskContainer
    + components
          | Type:   \torg.gradle.api.reporting.components.ComponentReport
          | Value:  \ttask ':components\'
          | Creator: \tProject.<init>.tasks.components()
          | Rules:
             ⤷ copyToTaskContainer
    + dependencies
          | Type:   \torg.gradle.api.tasks.diagnostics.DependencyReportTask
          | Value:  \ttask ':dependencies\'
          | Creator: \tProject.<init>.tasks.dependencies()
          | Rules:
             ⤷ copyToTaskContainer
    + dependencyInsight
          | Type:   \torg.gradle.api.tasks.diagnostics.DependencyInsightReportTask
          | Value:  \ttask ':dependencyInsight\'
          | Creator: \tProject.<init>.tasks.dependencyInsight()
          | Rules:
             ⤷ copyToTaskContainer
    + dependentComponents
          | Type:   \torg.gradle.api.reporting.dependents.DependentComponentsReport
          | Value:  \ttask ':dependentComponents\'
          | Creator: \tProject.<init>.tasks.dependentComponents()
          | Rules:
             ⤷ copyToTaskContainer
    + help
          | Type:   \torg.gradle.configuration.Help
          | Value:  \ttask ':help\'
          | Creator: \tProject.<init>.tasks.help()
          | Rules:
             ⤷ copyToTaskContainer
    + init
          | Type:   \torg.gradle.buildinit.tasks.InitBuild
          | Value:  \ttask ':init\'
          | Creator: \tProject.<init>.tasks.init()
          | Rules:
             ⤷ copyToTaskContainer
    + javaToolchains
          | Type:   \torg.gradle.jvm.toolchain.internal.task.ShowToolchainsTask
          | Value:  \ttask ':javaToolchains\'
          | Creator: \tProject.<init>.tasks.javaToolchains()
          | Rules:
             ⤷ copyToTaskContainer
    + model
          | Type:   \torg.gradle.api.reporting.model.ModelReport
          | Value:  \ttask ':model\'
          | Creator: \tProject.<init>.tasks.model()
          | Rules:
             ⤷ copyToTaskContainer
    + outgoingVariants
          | Type:   \torg.gradle.api.tasks.diagnostics.OutgoingVariantsReportTask
          | Value:  \ttask ':outgoingVariants\'
          | Creator: \tProject.<init>.tasks.outgoingVariants()
          | Rules:
             ⤷ copyToTaskContainer
    + prepareKotlinBuildScriptModel
          | Type:   \torg.gradle.api.DefaultTask
          | Value:  \ttask ':prepareKotlinBuildScriptModel\'
          | Creator: \tProject.<init>.tasks.prepareKotlinBuildScriptModel()
          | Rules:
             ⤷ copyToTaskContainer
    + projects
          | Type:   \torg.gradle.api.tasks.diagnostics.ProjectReportTask
          | Value:  \ttask ':projects\'
          | Creator: \tProject.<init>.tasks.projects()
          | Rules:
             ⤷ copyToTaskContainer
    + properties
          | Type:   \torg.gradle.api.tasks.diagnostics.PropertyReportTask
          | Value:  \ttask ':properties\'
          | Creator: \tProject.<init>.tasks.properties()
          | Rules:
             ⤷ copyToTaskContainer
    + resolvableConfigurations
          | Type:   \torg.gradle.api.tasks.diagnostics.ResolvableConfigurationsReportTask
          | Value:  \ttask ':resolvableConfigurations\'
          | Creator: \tProject.<init>.tasks.resolvableConfigurations()
          | Rules:
             ⤷ copyToTaskContainer
    + tasks
          | Type:   \torg.gradle.api.tasks.diagnostics.TaskReportTask
          | Value:  \ttask ':tasks\'
          | Creator: \tProject.<init>.tasks.tasks()
          | Rules:
             ⤷ copyToTaskContainer
    + wrapper
          | Type:   \torg.gradle.api.tasks.wrapper.Wrapper
          | Value:  \ttask ':wrapper\'
          | Creator: \tProject.<init>.tasks.wrapper()
          | Rules:
             ⤷ copyToTaskContainer
+ unsetNumbers
      | Type:   \tNumbers
      | Creator: \tunsetNumbers(Numbers) { ... } @ build.gradle line 32, column 5
    + threshold
          | Type:   \tdouble
          | Value:  \t0.0
          | Creator: \tunsetNumbers(Numbers) { ... } @ build.gradle line 32, column 5
    + value
          | Type:   \tjava.lang.Integer
          | Value:  \tnull
          | Creator: \tunsetNumbers(Numbers) { ... } @ build.gradle line 32, column 5

''')
    }

    def "method rule sources have simple type names and correct order"() {
        given:
        buildFile << """
${managedNumbers()}

class NumberRules extends RuleSource {
    @Model("myNumbers")
    void createRule(Numbers n) {
       n.setValue(5)
       n.setThreshold(0.8)
    }
    @Defaults void defaultsRule(Numbers n) {}
    @Mutate void mutateRule(Numbers n) {}
    @Finalize void finalizeRule(Numbers n) {}
    @Validate void validateRule(Numbers n) {}
}

class ClassHolder {
    static class InnerRules extends RuleSource {
         @Mutate void mutateRule(Numbers n) {}
    }
}

apply plugin: NumberRules
apply plugin: ClassHolder.InnerRules
"""
        buildFile
        when:
        run "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        modelNode.myNumbers.@creator[0] == 'NumberRules#createRule(Numbers)'

        int i = 0
        def rules = modelNode.myNumbers.@rules[0]
        rules[i++] == 'NumberRules#defaultsRule(Numbers)'
        rules[i++] == 'NumberRules#mutateRule(Numbers)'
        rules[i++] == 'ClassHolder.InnerRules#mutateRule(Numbers)'
        rules[i++] == 'NumberRules#finalizeRule(Numbers)'
        rules[i] == 'NumberRules#validateRule(Numbers)'
    }

    def "hidden nodes are not displayed on the report"() {
        given:
        buildFile << """
        class Rules extends RuleSource {
            @org.gradle.model.internal.core.Hidden @Model
            String thingamajigger() {
                return "hello"
            }
        }
        apply plugin: Rules
"""

        when:
        run "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        !modelNode.thingamajigger
    }

    def "properties on internal views of custom component are hidden in the model report"() {
        given:
        buildFile << """
            interface UnmanagedComponentSpec extends ComponentSpec {}
            class DefaultUnmanagedComponentSpec extends BaseComponentSpec implements UnmanagedComponentSpec {}

            @Managed
            interface SampleComponentSpec extends UnmanagedComponentSpec {
                String getPublicData()
                void setPublicData(String data)
            }

            @Managed
            interface InternalSampleSpec {
                String getPublicData()
                void setPublicData(String data)
                String getInternalData()
                void setInternalData(String data)
            }

            class RegisterComponentRules extends RuleSource {
                @ComponentType
                void register1(TypeBuilder<UnmanagedComponentSpec> builder) {
                    builder.defaultImplementation(DefaultUnmanagedComponentSpec)
                }

                @ComponentType
                void register2(TypeBuilder<SampleComponentSpec> builder) {
                    builder.internalView(InternalSampleSpec)
                }
            }
            apply plugin: RegisterComponentRules

            model {
                components {
                    sample(SampleComponentSpec)
                }
            }
        """

        when:
        succeeds "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        modelNode.components.sample.publicData
        !modelNode.components.sample.internalData

        and:
        succeeds "model", "--showHidden"

        then:
        ModelReportOutput.from(output).modelNode.components.sample.internalData
    }

    def "properties on internal views of custom binaries are hidden in the model report"() {
        given:
        buildFile << """
            interface UnmanagedBinarySpec extends BinarySpec {}
            class DefaultUnmanagedBinarySpec extends BaseBinarySpec implements UnmanagedBinarySpec {}

            @Managed
            interface SampleBinarySpec extends UnmanagedBinarySpec {
                String getPublicData()
                void setPublicData(String data)
            }

            @Managed
            interface InternalSampleSpec {
                String getPublicData()
                void setPublicData(String data)
                String getInternalData()
                void setInternalData(String data)
            }

            class RegisterBinaryRules extends RuleSource {
                @ComponentType
                void register1(TypeBuilder<UnmanagedBinarySpec> builder) {
                    builder.defaultImplementation(DefaultUnmanagedBinarySpec)
                }

                @ComponentType
                void register2(TypeBuilder<SampleBinarySpec> builder) {
                    builder.internalView(InternalSampleSpec)
                }
            }
            apply plugin: RegisterBinaryRules

            model {
                binaries {
                    sample(SampleBinarySpec)
                }
            }
        """

        when:
        succeeds "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        modelNode.binaries.sample.publicData
        !modelNode.binaries.sample.internalData

        and:
        succeeds "model", "--showHidden"

        then:
        ModelReportOutput.from(output).modelNode.binaries.sample.internalData
    }

    def "managed reference properties are displayed with correct type"() {
        given:
        buildFile << """
            @Managed
            interface Person {
                Person getFather()
                void setFather(Person person)
            }

            class Rules extends RuleSource {
                @Model
                void father(Person father) {}

                @Model
                void person(Person child, @Path("father") Person father) {
                    child.father = father
                }
            }
            apply plugin: Rules
        """

        when:
        succeeds "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        modelNode.person.father.size() == 1
        modelNode.person.father[0].type == "Person"
        modelNode.person.father[0].nodeValue == "reference to element 'father'"
        modelNode.father.father.size() == 1
        modelNode.father.father[0].type == "Person"
        modelNode.father.father[0].nodeValue == "null"
    }

    def "renders cycle in model graph"() {
        given:
        buildFile << """
            @Managed
            interface Person {
                Person getFather()
                void setFather(Person person)
            }

            class Rules extends RuleSource {
                @Model
                void father(Person father) {
                    father.father = father
                }
            }
            apply plugin: Rules
        """

        when:
        succeeds "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        modelNode.father.father.size() == 1
        modelNode.father.father[0].type == "Person"
        modelNode.father.father[0].nodeValue == "reference to element 'father'"
    }

    def "renders sensible value for node whose toString() method returns null"() {
        given:
        buildFile << """
            @Managed abstract class SomeType {
                String toString() { null }
            }
            model {
                something(SomeType)
            }
        """.stripIndent()

        when:
        succeeds 'model'

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        modelNode.something[0].nodeValue == 'SomeType#toString() returned null'
    }

    private String managedNumbers() {
        return """@Managed
        public interface Numbers {
            Integer getValue()
            void setValue(Integer i)

            double getThreshold()
            void setThreshold(double d)
        }"""
    }
}
