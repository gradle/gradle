/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.logging

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.SystemProperties

class DryRunLoggingIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.withArgument('--dry-run')
        buildFile << """
            task foo {}
            task bar(dependsOn: foo) {}
            task baz(dependsOn: bar) {}

            // Make sure the task aren't been skipped due to no action
            def i = 0
            tasks.all {
                doLast {
                    ++i
                }
            }
"""
    }

    def "all tasks are printed when dry run mode is enabled using rich console"() {
        given:
        executer.withRichConsole()

        when:
        succeeds('baz')

        then:
        output =~ /(?s).*> Task :foo.*SKIPPED.*> Task :bar.*SKIPPED.*> Task :baz.*SKIPPED.*/
        result.groupedOutput.task(':foo').output.empty
        result.groupedOutput.task(':bar').output.empty
        result.groupedOutput.task(':baz').output.empty
    }

    def "all tasks are printed when dry run mode is enabled using plain console"() {
        given:
        executer.withArgument('--console=plain')

        when:
        succeeds('baz')

        then:
        result.assertTaskOrder(':foo', ':bar', ':baz')
        result.assertTasksSkipped(':foo', ':bar', ':baz')
    }
}
