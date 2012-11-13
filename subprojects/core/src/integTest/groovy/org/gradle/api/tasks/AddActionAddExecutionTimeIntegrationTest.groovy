/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class AddActionAddExecutionTimeIntegrationTest extends AbstractIntegrationSpec {

    def "throws decent exception when action is added to a task at execution time"() {
        when:
        buildFile.text = """
            task foo1 << {
                doLast { println 'foo1' }
            }

            task foo2 << {
                doFirst { println 'foo2' }
            }
        """
        then:
        args("--continue")
        fails("foo1", "foo2")
        and:
        failure.assertHasCause("You cannot add a task action at execution time, please check the configuration of task task ':foo1'.")
        failure.assertHasCause("You cannot add a task action at execution time, please check the configuration of task task ':foo2'.")
    }
}
