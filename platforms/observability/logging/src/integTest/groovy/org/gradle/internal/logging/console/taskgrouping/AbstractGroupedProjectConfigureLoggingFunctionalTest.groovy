/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.logging.console.taskgrouping

import org.gradle.integtests.fixtures.console.AbstractConsoleGroupedTaskFunctionalTest
import org.gradle.integtests.fixtures.executer.LogContent

abstract class AbstractGroupedProjectConfigureLoggingFunctionalTest extends AbstractConsoleGroupedTaskFunctionalTest {
    def "project configuration messages are grouped"() {
        given:
        settingsFile << """
            include 'a', 'b'
        """
        buildFile << """
            println "root project"
            // Some nested build operations
            allprojects {
                println "configure \$path"
            }
            tasks.register("thing") {
                println "create \$path"
            }
            tasks.thing.description = "does something"
        """
        file("a/build.gradle") << """
            println "project a"
        """
        file("b/build.gradle") << """
            println "project b"
        """

        when:
        run()

        then:
        def normalizedOutput = LogContent.of(result.output).ansiCharsToPlainText().withNormalizedEol()
        normalizedOutput.contains("""
> Configure project :
root project
configure :
configure :a
configure :b
create :thing
""")
        normalizedOutput.contains("""
> Configure project :a
project a
""")
        normalizedOutput.contains("""
> Configure project :b
project b
""")
    }
}
