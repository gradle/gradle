/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

class SoftwareModelTaskAndBuildScriptIntegrationTest extends AbstractIntegrationSpec {

    def "can use camel case to match software model tasks"() {
        buildFile << """
            model {
                tasks {
                    "sayHelloToUser"(DefaultTask) {
                    }
                }
            }
        """
        when:
        run "sHTU"

        then:
        result.assertTasksScheduled(":sayHelloToUser")
    }

    @ToBeFixedForIsolatedProjects(because = "project cannot dynamically look up a method in the parent project")
    def "methods defined in project build script are visible to descendant projects when script contains only methods and model block"() {
        createDirs("child1")
        settingsFile << "rootProject.name = 'root'\ninclude 'child1'"
        buildFile << """
def doSomething(def value) {
    return value.toString()
}

model {
    tasks {
        hello(Task)
    }
}
"""
        file("child1/build.gradle") << """
println "child: " + doSomething(11)
"""

        expect:
        // Invoke twice to exercise script caching
        expectParentMethodAccessDeprecation('doSomething', ':child1', "root project 'root'")
        succeeds("hello")
        outputContains("child: 11")

        and:
        if (GradleContextualExecuter.notConfigCache) {
            expectParentMethodAccessDeprecation('doSomething', ':child1', "root project 'root'")
        }
        succeeds("hello")
        if (GradleContextualExecuter.notConfigCache) {
            outputContains("child: 11")
        } else {
            outputDoesNotContain("child:")
        }
    }

    private void expectParentMethodAccessDeprecation(String methodName, String childPath, String parentDisplayName) {
        executer.expectDocumentedDeprecationWarning("Accessing a method from a parent project has been deprecated. " +
            "This will fail with an error in Gradle 10. " +
            "Method '${methodName}' was not found in project '${childPath}' and was dynamically resolved from ${parentDisplayName}. " +
            "Consult the upgrading guide for further information: " +
            "https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_accessing_parent_project_properties")
    }
}
