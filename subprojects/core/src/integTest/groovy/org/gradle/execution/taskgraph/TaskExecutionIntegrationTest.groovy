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

package org.gradle.execution.taskgraph

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskExecutionIntegrationTest extends AbstractIntegrationSpec {
    def 'cancel execution when using --parallel'() {
        executer.withArguments('--parallel')
        settingsFile << """
            include 'a', 'b'
        """
        buildFile << """
import org.gradle.initialization.BuildCancellationToken;
import java.util.concurrent.CountDownLatch;

ext {
  latch = new CountDownLatch(2);
}
subprojects {
  task cancel(type: CancellingTask)
}
task build(dependsOn: [':a:cancel', ':b:cancel'])

class CancellingTask extends DefaultTask {
    private final BuildCancellationToken cancellationToken;

    public CancellingTask() {
        this.cancellationToken = getServices().get(BuildCancellationToken.class);
    }

    @TaskAction
    def cancelBuild() {
        project.rootProject.latch.countDown();
        project.rootProject.latch.await();
        cancellationToken.doCancel();
    }
}"""

        when:
        fails 'build'

        then:
        executedTasks as Set == [':a:cancel', ':b:cancel'] as Set
        failure.assertHasDescription "Build cancelled."
    }
}
