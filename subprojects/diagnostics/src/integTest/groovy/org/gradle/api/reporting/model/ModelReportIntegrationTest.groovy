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


@Managed
public interface Numbers {
    Integer getValue()
    void setValue(Integer i)
}

model {
    primaryCredentials(PasswordCredentials){
        username = 'uname'
        password = 'hunter2'
    }

    nullCredentials(PasswordCredentials) { }
    numbers(Numbers){
        value = 5
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
                    password(type: 'java.lang.String', creator: 'model.nullCredentials')
                    username(type: 'java.lang.String', creator: 'model.nullCredentials')
                }

                numbers {
                    value(nodeValue: "5")
                }
                primaryCredentials {
                    password(nodeValue: 'hunter2', type: 'java.lang.String', creator: 'model.primaryCredentials')
                    username(nodeValue: 'uname', type: 'java.lang.String', creator: 'model.primaryCredentials')
                }
                tasks {
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


@Managed
public interface Numbers {
    Integer getValue()
    void setValue(Integer i)
}

model {
    primaryCredentials(PasswordCredentials){
        username = 'uname'
        password = 'hunter2'
    }

    nullCredentials(PasswordCredentials) { }
    numbers(Numbers){
        value = 5
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
+ model
    + nullCredentials
          | Type:   \tPasswordCredentials |
          | Creator: \tmodel.nullCredentials |
          | Rules:  |
             ⤷ model.nullCredentials
             ⤷ model.nullCredentials
        + password
              | Type:   \tjava.lang.String |
              | Creator: \tmodel.nullCredentials |
              | Rules:  |
                 ⤷ model.nullCredentials
        + username
              | Type:   \tjava.lang.String |
              | Creator: \tmodel.nullCredentials |
              | Rules:  |
                 ⤷ model.nullCredentials
    + numbers
          | Type:   \tNumbers |
          | Creator: \tmodel.numbers |
          | Rules:  |
             ⤷ model.numbers
             ⤷ model.numbers
        + value
              | Type:   \tjava.lang.Integer |
              | Creator: \tmodel.numbers |
              | Value:  \t5 |
              | Rules:  |
                 ⤷ model.numbers
    + primaryCredentials
          | Type:   \tPasswordCredentials |
          | Creator: \tmodel.primaryCredentials |
          | Rules:  |
             ⤷ model.primaryCredentials
             ⤷ model.primaryCredentials
        + password
              | Type:   \tjava.lang.String |
              | Creator: \tmodel.primaryCredentials |
              | Value:  \thunter2 |
              | Rules:  |
                 ⤷ model.primaryCredentials
        + username
              | Type:   \tjava.lang.String |
              | Creator: \tmodel.primaryCredentials |
              | Value:  \tuname |
              | Rules:  |
                 ⤷ model.primaryCredentials
    + tasks
          | Type:   \torg.gradle.model.ModelMap<org.gradle.api.Task> |
          | Creator: \tProject.<init>.tasks() |
          | Rules:  |
             ⤷ Project.<init>.tasks()
        + components
              | Type:   \torg.gradle.api.reporting.components.ComponentReport |
              | Creator: \ttasks.addPlaceholderAction(components) |
              | Value:  \ttask ':components' |
              | Rules:  |
                 ⤷ tasks.addPlaceholderAction(components)
                 ⤷ copyToTaskContainer
        + dependencies
              | Type:   \torg.gradle.api.tasks.diagnostics.DependencyReportTask |
              | Creator: \ttasks.addPlaceholderAction(dependencies) |
              | Value:  \ttask ':dependencies' |
              | Rules:  |
                 ⤷ tasks.addPlaceholderAction(dependencies)
                 ⤷ copyToTaskContainer
        + dependencyInsight
              | Type:   \torg.gradle.api.tasks.diagnostics.DependencyInsightReportTask |
              | Creator: \ttasks.addPlaceholderAction(dependencyInsight) |
              | Value:  \ttask ':dependencyInsight' |
              | Rules:  |
                 ⤷ tasks.addPlaceholderAction(dependencyInsight)
                 ⤷ org.gradle.api.plugins.HelpTasksPlugin$Rules#addDefaultDependenciesReportConfiguration(org.gradle.api.tasks.diagnostics.DependencyInsightReportTask, org.gradle.internal.service.ServiceRegistry)
                 ⤷ copyToTaskContainer
        + help
              | Type:   \torg.gradle.configuration.Help |
              | Creator: \ttasks.addPlaceholderAction(help) |
              | Value:  \ttask ':help' |
              | Rules:  |
                 ⤷ tasks.addPlaceholderAction(help)
                 ⤷ copyToTaskContainer
        + init
              | Type:   \torg.gradle.buildinit.tasks.InitBuild |
              | Creator: \ttasks.addPlaceholderAction(init) |
              | Value:  \ttask ':init' |
              | Rules:  |
                 ⤷ tasks.addPlaceholderAction(init)
                 ⤷ copyToTaskContainer
        + model
              | Type:   \torg.gradle.api.reporting.model.ModelReport |
              | Creator: \ttasks.addPlaceholderAction(model) |
              | Value:  \ttask ':model' |
              | Rules:  |
                 ⤷ tasks.addPlaceholderAction(model)
                 ⤷ copyToTaskContainer
        + projects
              | Type:   \torg.gradle.api.tasks.diagnostics.ProjectReportTask |
              | Creator: \ttasks.addPlaceholderAction(projects) |
              | Value:  \ttask ':projects' |
              | Rules:  |
                 ⤷ tasks.addPlaceholderAction(projects)
                 ⤷ copyToTaskContainer
        + properties
              | Type:   \torg.gradle.api.tasks.diagnostics.PropertyReportTask |
              | Creator: \ttasks.addPlaceholderAction(properties) |
              | Value:  \ttask ':properties' |
              | Rules:  |
                 ⤷ tasks.addPlaceholderAction(properties)
                 ⤷ copyToTaskContainer
        + tasks
              | Type:   \torg.gradle.api.tasks.diagnostics.TaskReportTask |
              | Creator: \ttasks.addPlaceholderAction(tasks) |
              | Value:  \ttask ':tasks' |
              | Rules:  |
                 ⤷ tasks.addPlaceholderAction(tasks)
                 ⤷ copyToTaskContainer
        + wrapper
              | Type:   \torg.gradle.api.tasks.wrapper.Wrapper |
              | Creator: \ttasks.addPlaceholderAction(wrapper) |
              | Value:  \ttask ':wrapper' |
              | Rules:  |
                 ⤷ tasks.addPlaceholderAction(wrapper)
                 ⤷ copyToTaskContainer
''')
    }
}
