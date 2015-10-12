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
                    buildscriptDependencies()
                    components(nodeValue: "task ':components'", type: 'org.gradle.api.reporting.components.ComponentReport')
                    dependencies()
                    dependencyInsight()
                    help()
                    init()
                    model()
                    projects()
                    properties()
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
        ModelReportOutput.from(output).hasNodeStructure({
            model {
                container {
                    ids(type: 'java.util.List<java.lang.Integer>', creator: 'model.container @ build.gradle line 12, column 5')
                    labels(type: 'java.util.List<java.lang.String>', creator: 'model.container @ build.gradle line 12, column 5', nodeValue: "[bug, blocker]")
                    values(type: 'java.util.List<java.lang.Double>', creator: 'model.container @ build.gradle line 12, column 5')
                }
                tasks {
                    buildscriptDependencies(nodeValue: "task ':buildscriptDependencies'")
                    components(nodeValue: "task ':components'")
                    dependencies(nodeValue: "task ':dependencies'")
                    dependencyInsight(nodeValue: "task ':dependencyInsight'")
                    help(nodeValue: "task ':help'")
                    init(nodeValue: "task ':init'")
                    model(nodeValue: "task ':model'")
                    projects(nodeValue: "task ':projects'")
                    properties(nodeValue: "task ':properties'")
                    tasks(nodeValue: "task ':tasks'")
                    wrapper()
                }
            }
        })
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
}

"""
        buildFile
        when:
        run "model"

        then:
        ModelReportOutput.from(output).hasNodeStructure({
            model {
                nullCredentials {
                    password(type: 'java.lang.String', creator: 'model.nullCredentials @ build.gradle line 27, column 5')
                    username(type: 'java.lang.String', creator: 'model.nullCredentials @ build.gradle line 27, column 5')
                }

                numbers {
                    threshold(nodeValue: "0.8")
                    value(nodeValue: "5")
                }
                primaryCredentials {
                    password(nodeValue: 'hunter2', type: 'java.lang.String', creator: 'model.primaryCredentials @ build.gradle line 22, column 5')
                    username(nodeValue: 'uname', type: 'java.lang.String', creator: 'model.primaryCredentials @ build.gradle line 22, column 5')
                }
                tasks {
                    buildscriptDependencies(nodeValue: "task ':buildscriptDependencies'")
                    components(nodeValue: "task ':components'")
                    dependencies(nodeValue: "task ':dependencies'")
                    dependencyInsight(nodeValue: "task ':dependencyInsight'")
                    help(nodeValue: "task ':help'")
                    init(nodeValue: "task ':init'")
                    model(nodeValue: "task ':model'")
                    projects(nodeValue: "task ':projects'")
                    properties(nodeValue: "task ':properties'")
                    tasks(nodeValue: "task ':tasks'")
                    wrapper()
                }
            }
        })
    }

    // nb: specifically doesn't use the parsing fixture, so that the output is visualised
    //If you're changing this you will also need to change: src/samples/userguideOutput/basicRuleSourcePlugin-model-task.out
    def "displays a report in the correct format"() {
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
}

"""
        buildFile
        when:
        run "model"

        then:
        def modelReportOutput = ModelReportOutput.from(output)
        modelReportOutput.hasTitle("Root project")

        and:
        modelReportOutput.nodeContentEquals('''
+ nullCredentials
      | Type:   \tPasswordCredentials
      | Creator: \tmodel.nullCredentials @ build.gradle line 27, column 5
    + password
          | Type:   \tjava.lang.String
          | Creator: \tmodel.nullCredentials @ build.gradle line 27, column 5
    + username
          | Type:   \tjava.lang.String
          | Creator: \tmodel.nullCredentials @ build.gradle line 27, column 5
+ numbers
      | Type:   \tNumbers
      | Creator: \tmodel.numbers @ build.gradle line 28, column 5
    + threshold
          | Type:   \tdouble
          | Value:  \t0.8
          | Creator: \tmodel.numbers @ build.gradle line 28, column 5
    + value
          | Type:   \tjava.lang.Integer
          | Value:  \t5
          | Creator: \tmodel.numbers @ build.gradle line 28, column 5
+ primaryCredentials
      | Type:   \tPasswordCredentials
      | Creator: \tmodel.primaryCredentials @ build.gradle line 22, column 5
    + password
          | Type:   \tjava.lang.String
          | Value:  \thunter2
          | Creator: \tmodel.primaryCredentials @ build.gradle line 22, column 5
    + username
          | Type:   \tjava.lang.String
          | Value:  \tuname
          | Creator: \tmodel.primaryCredentials @ build.gradle line 22, column 5
+ tasks
      | Type:   \torg.gradle.model.ModelMap<org.gradle.api.Task>
      | Creator: \tProject.<init>.tasks()
    + buildscriptDependencies
          | Type:   \torg.gradle.api.tasks.diagnostics.BuildscriptDependencyReportTask
          | Value:  \ttask ':buildscriptDependencies\'
          | Creator: \ttasks.addPlaceholderAction(buildscriptDependencies)
          | Rules:
             ⤷ copyToTaskContainer
    + components
          | Type:   \torg.gradle.api.reporting.components.ComponentReport
          | Value:  \ttask ':components'
          | Creator: \ttasks.addPlaceholderAction(components)
          | Rules:
             ⤷ copyToTaskContainer
    + dependencies
          | Type:   \torg.gradle.api.tasks.diagnostics.DependencyReportTask
          | Value:  \ttask ':dependencies'
          | Creator: \ttasks.addPlaceholderAction(dependencies)
          | Rules:
             ⤷ copyToTaskContainer
    + dependencyInsight
          | Type:   \torg.gradle.api.tasks.diagnostics.DependencyInsightReportTask
          | Value:  \ttask ':dependencyInsight'
          | Creator: \ttasks.addPlaceholderAction(dependencyInsight)
          | Rules:
             ⤷ HelpTasksPlugin.Rules#addDefaultDependenciesReportConfiguration
             ⤷ copyToTaskContainer
    + help
          | Type:   \torg.gradle.configuration.Help
          | Value:  \ttask ':help'
          | Creator: \ttasks.addPlaceholderAction(help)
          | Rules:
             ⤷ copyToTaskContainer
    + init
          | Type:   \torg.gradle.buildinit.tasks.InitBuild
          | Value:  \ttask ':init'
          | Creator: \ttasks.addPlaceholderAction(init)
          | Rules:
             ⤷ copyToTaskContainer
    + model
          | Type:   \torg.gradle.api.reporting.model.ModelReport
          | Value:  \ttask ':model'
          | Creator: \ttasks.addPlaceholderAction(model)
          | Rules:
             ⤷ copyToTaskContainer
    + projects
          | Type:   \torg.gradle.api.tasks.diagnostics.ProjectReportTask
          | Value:  \ttask ':projects'
          | Creator: \ttasks.addPlaceholderAction(projects)
          | Rules:
             ⤷ copyToTaskContainer
    + properties
          | Type:   \torg.gradle.api.tasks.diagnostics.PropertyReportTask
          | Value:  \ttask ':properties'
          | Creator: \ttasks.addPlaceholderAction(properties)
          | Rules:
             ⤷ copyToTaskContainer
    + tasks
          | Type:   \torg.gradle.api.tasks.diagnostics.TaskReportTask
          | Value:  \ttask ':tasks'
          | Creator: \ttasks.addPlaceholderAction(tasks)
          | Rules:
             ⤷ copyToTaskContainer
    + wrapper
          | Type:   \torg.gradle.api.tasks.wrapper.Wrapper
          | Value:  \ttask ':wrapper'
          | Creator: \ttasks.addPlaceholderAction(wrapper)
          | Rules:
             ⤷ copyToTaskContainer
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
        modelNode.myNumbers.@creator[0] == 'NumberRules#createRule'

        int i = 0
        def rules = modelNode.myNumbers.@rules[0]
        rules[i++] == 'NumberRules#defaultsRule'
        rules[i++] == 'NumberRules#mutateRule'
        rules[i++] == 'ClassHolder.InnerRules#mutateRule'
        rules[i++] == 'NumberRules#finalizeRule'
        rules[i] == 'NumberRules#validateRule'
    }

    def "service nodes are not displayed on the report"() {
        given:
        buildFile << """
        class Rules extends RuleSource {
            @Service
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
