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
        new ModelReportOutput(output).hasNodeStructure({
            model() {
                tasks {
                    components(value: "task ':components'", type: 'org.gradle.api.reporting.components.ComponentReport')
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
        new ModelReportOutput(output).hasNodeStructure({
            model {
                nullCredentials {
                    password()
                    username()
                }

                numbers {
                    value(value: 5)
                }
                primaryCredentials {
                    password(value: 'hunter2')
                    username(value: 'uname')
                }
                tasks {
                    components(value: "task ':components'")
                    dependencies(value: "task ':dependencies'")
                    dependencyInsight(value: "task ':dependencyInsight'")
                    help(value: "task ':help'")
                    init(value: "task ':init'")
                    model(value: "task ':model'")
                    projects(value: "task ':projects'")
                    properties(value: "task ':properties'")
                    tasks(value: "task ':tasks'")
                    wrapper()
                }
            }
        })
    }

}
