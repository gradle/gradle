/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests

import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Ignore

class WrapperUNCIntegrationTest extends AbstractWrapperIntegrationSpec {
    @Requires(TestPrecondition.WINDOWS)
    @Ignore("Test Framework does not support UNC Paths")
    def "wrapper propertly supports UNC paths on windows"() {
        given:
        file("build.gradle") << """
        task hello {
            println 'hello'
        }
        """
        file('settings.gradle') << ''

        prepareWrapper()

        def exec = wrapperExecuter

        def workingDir = exec.workingDir

        exec.inDirectory(new File('\\\\?\\' + workingDir))

        exec.withTasks("hello")

        when:
        def success = exec.run()

        then:
        success.output.contains('BUILD SUCCESSFUL')
    }
}
