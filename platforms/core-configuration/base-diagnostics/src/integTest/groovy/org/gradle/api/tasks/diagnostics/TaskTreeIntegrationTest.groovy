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

package org.gradle.api.tasks.diagnostics

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskTreeIntegrationTest extends AbstractIntegrationSpec {

    def "shows tree of tasks"() {
        given:
        settingsFile """rootProject.name = 'my-root'"""
        buildFile """
            plugins {
                id 'java'
            }
        """

        when:
        succeeds("compileJava", "--task-tree")

        then:
        outputContains("""
Tasks:

------------------------------------------------------------
Root project 'my-root'
------------------------------------------------------------

task ':compileJava'
+--- task ':p1'
\\--- task ':p2'
     \\--- task ':p2:p22'
""")
    }
}
