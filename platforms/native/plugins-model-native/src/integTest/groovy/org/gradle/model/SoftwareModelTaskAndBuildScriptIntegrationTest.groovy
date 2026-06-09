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

    def "methods defined in project build script are not visible to descendant projects when script contains only methods and model block"() {
        createDirs("child1")
        settingsFile << """
rootProject.name = 'root'
include 'child1'
"""
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
        fails("hello")
        failure.assertHasCause("Could not find method doSomething() for arguments [11] on project ':child1' of type org.gradle.api.Project.")
    }
}
