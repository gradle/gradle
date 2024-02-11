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


package org.gradle.integtests.tooling.r23

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.build.BuildEnvironment

class ModelBuilderCrossVersionSpec extends ToolingApiSpecification {

    def "empty list of tasks to execute when asking for BuildEnvironment is treated like null tasks and does not fail"() {
        projectDir.file('build.gradle') << """
            defaultTasks 'alpha'
            task alpha() { doLast { throw new RuntimeException() } }
        """

        when:
        BuildEnvironment model = toolingApi.withConnection {
            it.model(BuildEnvironment)
                .forTasks(new String[0])
                .get()
        }

        then:
        model != null
        result.assertTasksExecutedInOrder()
    }
}
