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
                    password(type: 'java.lang.String', origin: 'model.nullCredentials')
                    username(type: 'java.lang.String', origin: 'model.nullCredentials')
                }

                numbers {
                    value(nodeValue: "5")
                }
                primaryCredentials {
                    password(nodeValue: 'hunter2', type: 'java.lang.String', origin: 'model.primaryCredentials')
                    username(nodeValue: 'uname', type: 'java.lang.String', origin: 'model.primaryCredentials')
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
        modelReportOutput.nodeContentEquals("""
+ model
    + nullCredentials
          | Type:   \tPasswordCredentials |
          | Origin: \tmodel.nullCredentials |
        + password
              | Type:   \tjava.lang.String |
              | Origin: \tmodel.nullCredentials |
        + username
              | Type:   \tjava.lang.String |
              | Origin: \tmodel.nullCredentials |
    + numbers
          | Type:   \tNumbers |
          | Origin: \tmodel.numbers |
        + value
              | Type:   \tjava.lang.Integer |
              | Origin: \tmodel.numbers |
              | Value:  \t5 |
    + primaryCredentials
          | Type:   \tPasswordCredentials |
          | Origin: \tmodel.primaryCredentials |
        + password
              | Type:   \tjava.lang.String |
              | Origin: \tmodel.primaryCredentials |
              | Value:  \thunter2 |
        + username
              | Type:   \tjava.lang.String |
              | Origin: \tmodel.primaryCredentials |
              | Value:  \tuname |
    + tasks
          | Type:   \torg.gradle.model.ModelMap<org.gradle.api.Task> |
          | Origin: \tProject.<init>.tasks() |
        + components
              | Type:   \torg.gradle.api.reporting.components.ComponentReport |
              | Origin: \ttasks.addPlaceholderAction(components) |
              | Value:  \ttask ':components' |
        + dependencies
              | Type:   \torg.gradle.api.tasks.diagnostics.DependencyReportTask |
              | Origin: \ttasks.addPlaceholderAction(dependencies) |
              | Value:  \ttask ':dependencies' |
        + dependencyInsight
              | Type:   \torg.gradle.api.tasks.diagnostics.DependencyInsightReportTask |
              | Origin: \ttasks.addPlaceholderAction(dependencyInsight) |
              | Value:  \ttask ':dependencyInsight' |
        + help
              | Type:   \torg.gradle.configuration.Help |
              | Origin: \ttasks.addPlaceholderAction(help) |
              | Value:  \ttask ':help' |
        + init
              | Type:   \torg.gradle.buildinit.tasks.InitBuild |
              | Origin: \ttasks.addPlaceholderAction(init) |
              | Value:  \ttask ':init' |
        + model
              | Type:   \torg.gradle.api.reporting.model.ModelReport |
              | Origin: \ttasks.addPlaceholderAction(model) |
              | Value:  \ttask ':model' |
        + projects
              | Type:   \torg.gradle.api.tasks.diagnostics.ProjectReportTask |
              | Origin: \ttasks.addPlaceholderAction(projects) |
              | Value:  \ttask ':projects' |
        + properties
              | Type:   \torg.gradle.api.tasks.diagnostics.PropertyReportTask |
              | Origin: \ttasks.addPlaceholderAction(properties) |
              | Value:  \ttask ':properties' |
        + tasks
              | Type:   \torg.gradle.api.tasks.diagnostics.TaskReportTask |
              | Origin: \ttasks.addPlaceholderAction(tasks) |
              | Value:  \ttask ':tasks' |
        + wrapper
              | Type:   \torg.gradle.api.tasks.wrapper.Wrapper |
              | Origin: \ttasks.addPlaceholderAction(wrapper) |
              | Value:  \ttask ':wrapper' |
""")
    }
}
