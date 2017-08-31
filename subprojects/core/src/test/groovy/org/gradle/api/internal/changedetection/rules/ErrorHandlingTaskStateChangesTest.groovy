/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.rules

import com.google.common.collect.AbstractIterator
import org.gradle.api.GradleException
import org.gradle.api.Task
import spock.lang.Specification

class ErrorHandlingTaskStateChangesTest extends Specification {
    def task = Mock(Task)
    def delegate = Mock(TaskStateChanges)
    def changes = new ErrorHandlingTaskStateChanges(task, delegate)

    def "iterator error reports task path"() {
        when:
        changes.iterator()
        then:
        def ex = thrown GradleException
        ex.message == "Cannot determine task state changes for Mock for type 'Task' named 'task'"
        ex.cause.message == "Error!"
        1 * delegate.iterator() >> { throw new RuntimeException("Error!") }
    }

    def "iteration error reports task path"() {
        when:
        def iterator = changes.iterator()
        then:
        1 * delegate.iterator() >> { new AbstractIterator<String>() {
            @Override
            protected String computeNext() {
                throw new RuntimeException("Error!")
            }
        } }

        when:
        iterator.next()
        then:
        def ex = thrown GradleException
        ex.message == "Cannot determine task state changes for Mock for type 'Task' named 'task'"
        ex.cause.message == "Error!"
    }
}
