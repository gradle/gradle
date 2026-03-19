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

package org.gradle.api.internal.tasks

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.taskfactory.TestTaskIdentities
import org.gradle.api.tasks.VerificationException
import org.gradle.internal.Describables
import org.gradle.internal.code.DefaultUserCodeSource
import org.gradle.internal.code.UserCodeSource
import spock.lang.Specification

class TaskProvenanceUtilTest extends Specification {

    // region getProvenance

    def "unknown source returns empty provenance"() {
        given:
        def task = stubTask(UserCodeSource.UNKNOWN)

        expect:
        !TaskProvenanceUtil.getProvenance(task).isPresent()
    }

    def "known source returns '#expected'"() {
        given:
        def task = stubTask(source)

        expect:
        TaskProvenanceUtil.getProvenance(task).get() == expected

        where:
        source                                                                         | expected
        UserCodeSource.BY_RULE                                                         | "registered by Rule"
        new DefaultUserCodeSource(Describables.of("plugin ':my-plugin'"), "my-plugin") | "registered by plugin ':my-plugin'"
        new DefaultUserCodeSource(Describables.of("build file 'build.gradle'"), null)  | "registered in build file 'build.gradle'"
    }

    // endregion getProvenance

    // region buildFailureMessage

    def "failure message includes provenance for non-verification failures"() {
        given:
        def task = stubTask(new DefaultUserCodeSource(Describables.of("build file 'build.gradle'"), null), "task ':myTask'")
        def cause = new RuntimeException("boom")

        expect:
        TaskProvenanceUtil.buildFailureMessage(task, cause) == "Execution failed for task ':myTask' (registered in build file 'build.gradle')."
    }

    def "failure message omits provenance for verification failures"() {
        given:
        def task = stubTask(new DefaultUserCodeSource(Describables.of("build file 'build.gradle'"), null), "task ':myTask'")
        def cause = new VerificationException("test failed")

        expect:
        TaskProvenanceUtil.buildFailureMessage(task, cause) == "Execution failed for task ':myTask'."
    }

    def "failure message omits provenance when source is unknown"() {
        given:
        def task = stubTask(UserCodeSource.UNKNOWN, "task ':myTask'")
        def cause = new RuntimeException("boom")

        expect:
        TaskProvenanceUtil.buildFailureMessage(task, cause) == "Execution failed for task ':myTask'."
    }

    // endregion buildFailureMessage

    private TaskInternal stubTask(UserCodeSource source, String taskToString = "task ':test'") {
        def identity = TestTaskIdentities.createWithSource(source)
        return Stub(TaskInternal) {
            getTaskIdentity() >> identity
            toString() >> taskToString
        }
    }
}
