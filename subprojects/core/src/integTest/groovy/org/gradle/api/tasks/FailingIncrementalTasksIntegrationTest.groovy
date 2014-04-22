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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class FailingIncrementalTasksIntegrationTest extends AbstractIntegrationSpec {

    def "consecutively failing task has correct up-to-date status and failure"() {
        buildFile << """
            task foo {
                outputs.file("out.txt")
                doLast {
                    if (project.file("out.txt").exists()) {
                        throw new RuntimeException("Boo!")
                    }
                    project.file("out.txt") << "xxx"
                }
            }
        """

        when:
        run "foo"
        file("out.txt") << "force rerun"
        def failure1 = runAndFail "foo"
        def failure2 = runAndFail "foo"

        then:
        failure1.assertHasCause("Boo!")
        failure2.assertHasCause("Boo!")
        //this exposes an issue we used to have with in-memory cache.
    }
}
