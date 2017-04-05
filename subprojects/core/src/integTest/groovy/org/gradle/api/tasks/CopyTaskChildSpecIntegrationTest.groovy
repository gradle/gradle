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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.LocalBuildCacheFixture
import spock.lang.Ignore

class CopyTaskChildSpecIntegrationTest extends AbstractIntegrationSpec implements LocalBuildCacheFixture {

    @Ignore("Must fix for 4.0")
    def "changing child specs of the copy task while executing is deprecated"() {
        given:
        setupCopyTaskModifyingChildSpecsAtExecutionTime()

        when:
        executer.expectDeprecationWarning()
        succeeds "copy"

        then:
        output.contains("Configuring child specs of a copy task at execution time of the task has been deprecated and is scheduled to be removed in Gradle 4.0. " +
            "Consider configuring the spec during configuration time, or using a separate task to do the configuration.")
    }

    def "changing child specs of the copy task is disallowed if caching is enabled"() {
        given:
        setupCopyTaskModifyingChildSpecsAtExecutionTime()

        when:
        withBuildCache().fails "copy"

        then:
        failure.assertHasCause("It is not allowed to modify child specs of the task at execution time when task output caching is enabled. " +
            "Consider configuring the spec during configuration time, or using a separate task to do the configuration.")
    }

    void setupCopyTaskModifyingChildSpecsAtExecutionTime() {
        buildScript """
            task copy(type: Copy) {
                outputs.cacheIf { true }
                from ("some-dir")
                into ("build/output")

                doFirst {
                    from ("some-other-dir") {
                        exclude "non-existent-file"
                    }
                }
            }
        """
        file("some-dir/first-file") << "first file"
    }
}
