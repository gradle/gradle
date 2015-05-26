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

package org.gradle.api.reporting.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ModelReportFormatIntegrationTest extends AbstractIntegrationSpec {

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
        def modelReportOutput = new ModelReportOutput(output)
        modelReportOutput.hasTitle("Root project")

        and:
        modelReportOutput.nodeContentEquals("""
+ model
    + nullCredentials
          | Type: \t PasswordCredentials |
        + password
              | Type: \t java.lang.String |
        + username
              | Type: \t java.lang.String |
    + numbers
          | Type: \t Numbers |
        + value
              | Value: \t 5 |
              | Type: \t java.lang.Integer |
    + primaryCredentials
          | Type: \t PasswordCredentials |
        + password
              | Value: \t hunter2 |
              | Type: \t java.lang.String |
        + username
              | Value: \t uname |
              | Type: \t java.lang.String |
    + tasks
          | Type: \t org.gradle.model.ModelMap<org.gradle.api.Task> |
        + components
              | Value: \t task ':components' |
              | Type: \t org.gradle.api.reporting.components.ComponentReport |
        + dependencies
              | Value: \t task ':dependencies' |
              | Type: \t org.gradle.api.tasks.diagnostics.DependencyReportTask |
        + dependencyInsight
              | Value: \t task ':dependencyInsight' |
              | Type: \t org.gradle.api.tasks.diagnostics.DependencyInsightReportTask |
        + help
              | Value: \t task ':help' |
              | Type: \t org.gradle.configuration.Help |
        + init
              | Value: \t task ':init' |
              | Type: \t org.gradle.buildinit.tasks.InitBuild |
        + model
              | Value: \t task ':model' |
              | Type: \t org.gradle.api.reporting.model.ModelReport |
        + projects
              | Value: \t task ':projects' |
              | Type: \t org.gradle.api.tasks.diagnostics.ProjectReportTask |
        + properties
              | Value: \t task ':properties' |
              | Type: \t org.gradle.api.tasks.diagnostics.PropertyReportTask |
        + tasks
              | Value: \t task ':tasks' |
              | Type: \t org.gradle.api.tasks.diagnostics.TaskReportTask |
        + wrapper
              | Value: \t task ':wrapper' |
              | Type: \t org.gradle.api.tasks.wrapper.Wrapper |
""")
    }
}
