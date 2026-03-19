/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r31


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.tooling.model.GradleTask

class BuildActionCrossVersionSpec extends ToolingApiSpecification {

    @Requires(
        value = IntegTestPreconditions.NotEmbeddedExecutor,
        reason = "FilteringClassLoader from TAPI process is improperly deserialized in daemon. To resolve this we must execute in-process daemons on-top of the proper daemon classloader instead of on-top of the TAPI classloader"
    )
    def "can round trip a model queried by a build action"() {
        when:
        GradleTask task = withConnection { c -> c.action(new FetchTaskAction()).run() }
        task = withConnection { c -> c.action(new ReturnValueAction(task)).run() }
        withConnection { c -> c.newBuild().forTasks(task).run() }

        then:
        noExceptionThrown()
    }
}
