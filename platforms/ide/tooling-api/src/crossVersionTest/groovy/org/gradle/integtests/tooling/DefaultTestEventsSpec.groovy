/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.integtests.tooling.fixture.ProgressEvents

@CompileStatic
class DefaultTestEventsSpec implements TestEventsSpec {
    private final ProgressEvents events
    private final List<ProgressEvents.Operation> verifiedOperations

    DefaultTestEventsSpec(ProgressEvents events, List<ProgressEvents.Operation> verifiedOperations) {
        this.events = events
        this.verifiedOperations = verifiedOperations
    }

    @Override
    void task(String path, @DelegatesTo(value = GroupTestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> assertionSpec) {
        def entryPoint = events.operation("Task $path")
        def spec = new DefaultTestEventSpec(entryPoint, verifiedOperations)
        assertionSpec.delegate = spec
        assertionSpec.resolveStrategy = Closure.DELEGATE_FIRST
        assertionSpec()
        // If we've made it this far, this operation has been verified
        verifiedOperations << entryPoint
    }
}
