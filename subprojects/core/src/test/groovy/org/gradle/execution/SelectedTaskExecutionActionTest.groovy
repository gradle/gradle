/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.execution

import spock.lang.Specification
import org.gradle.api.internal.GradleInternal

class SelectedTaskExecutionActionTest extends Specification {
    final SelectedTaskExecutionAction action = new SelectedTaskExecutionAction()
    final BuildExecutionContext context = Mock()
    final TaskGraphExecuter executer = Mock()
    final GradleInternal gradleInternal = Mock()

    def setup() {
        _ * context.gradle >> gradleInternal
        _ * gradleInternal.taskGraph >> executer
    }

    def "executes selected tasks"() {
        when:
        action.execute(context)

        then:
        1 * executer.execute()
    }
}
