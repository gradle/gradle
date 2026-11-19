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

package org.gradle.internal.cc.impl

class ConfigurationCacheTaskLifecycleListenerIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "listener accessing Task.project in #method is reported as a problem"() {
        buildFile """
            abstract class ProjectAccessingListener implements TaskLifecycleListener<Task> {
                void $method {
                    println "listener sees project \${task.project.name}"
                }
            }

            tasks.register("foo") {
                addLifecycleListener(ProjectAccessingListener)
            }
        """

        when:
        configurationCacheFails "foo"

        then:
        // The access fails the task, so the problem is reported without failing the build a second time
        failure.assertHasCause("Invocation of 'Task.project' by task ':foo' at execution time is unsupported with the configuration cache.")
        problems.assertResultHasProblems(failure) {
            withProblem("Build file 'build.gradle': line 4: invocation of 'Task.project' at execution time is unsupported with the configuration cache.")
        }

        where:
        method << ["beforeExecution(Task task)", "afterExecution(Task task, TaskState state)"]
    }
}
