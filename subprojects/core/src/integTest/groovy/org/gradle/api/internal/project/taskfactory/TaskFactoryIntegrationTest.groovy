/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory

import org.gradle.api.tasks.Internal
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

import javax.inject.Inject

class TaskFactoryIntegrationTest extends AbstractIntegrationSpec {
    @Issue("GRADLE-3317")
    def "can generate a task when the inject annotation is not present on all of the methods in the hierarchy"() {
        given:
        buildFile """
            public interface InternalGetterInterface {
                @${Internal.class.getName()}
                ExecOperations getExecOperations()
            }
            public interface InjectGetterInterface extends InternalGetterInterface {
                @${Inject.class.getName()}
                ExecOperations getExecOperations()
            }
            public abstract class MyTask extends DefaultTask implements InjectGetterInterface {
                @TaskAction
                void doSomething() {
                    println(getExecOperations().toString())
                }
            }

            task myTask(type: MyTask) {}
            """
        expect:
        succeeds "myTask"
    }
}
