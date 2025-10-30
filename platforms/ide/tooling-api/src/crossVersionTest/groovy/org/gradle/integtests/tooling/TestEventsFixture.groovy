/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.tooling

import groovy.transform.CompileStatic
import groovy.transform.SelfType
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.test.JvmTestOperationDescriptor

@SelfType(ToolingApiSpecification)
@CompileStatic
trait TestEventsFixture {
    abstract ProgressEvents getEvents()

    void testEvents(@DelegatesTo(value = TestEventsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> assertionSpec) {
        List<ProgressEvents.Operation> verifiedOperations = []
        TestEventsSpec spec = new DefaultTestEventsSpec(events, verifiedOperations)
        assertionSpec.delegate = spec
        assertionSpec.resolveStrategy = Closure.DELEGATE_FIRST
        assertionSpec()

        def remainingOperations = events.operations - verifiedOperations
        def potentiallyInteresting = remainingOperations.findAll {
            // If it's a task, check if it has any test related operations
            if (it.descriptor instanceof TaskOperationDescriptor) {
                // keep this in the list if it could be a task running tests
                return it.children*.descriptor instanceof JvmTestOperationDescriptor
            }
            // keep it if we don't recognize it as something we don't care about
            return true
        }
        assert potentiallyInteresting.isEmpty()
    }
}
