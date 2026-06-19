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

package org.gradle.api.internal.tasks.testing.junit.result

import org.gradle.api.internal.tasks.testing.GroupTestEventReporterInternal
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.tasks.testing.TestEventReporter
import spock.lang.Issue
import spock.lang.Specification

import java.util.function.Function

@Issue("https://github.com/gradle/gradle/issues/38193")
class TestEventReporterAsListenerTest extends Specification {

    /*
     * Negative regression test: pins the consumer-side invariant that the producer-side
     * fix in JUnitTestEventAdapter.addDescriptorAndChildren now relies on.
     *
     * The cast at TestEventReporterAsListener.java:57-58 is intentionally unguarded.
     * The producer side guarantees that any descriptor which will acquire children
     * is registered as composite from the start, so the parent-slot reporter is always
     * a GroupTestEventReporterInternal. If that producer-side invariant is ever broken
     * (a future change emits a non-composite parent slot) the cast will throw
     * ClassCastException — and this test goes red as the canary.
     */
    def "throws ClassCastException when fed a non-composite parent — pins the consumer-side invariant the producer-side fix relies on"() {
        given:
        def rootGroupReporter = Mock(GroupTestEventReporterInternal)
        def specClassReporter = Mock(TestEventReporter)
        Function<TestDescriptorInternal, GroupTestEventReporterInternal> rootCreator = { desc -> rootGroupReporter }
        def listener = new TestEventReporterAsListener(rootCreator)

        def root = Mock(TestDescriptorInternal) {
            getId() >> "root"
            getParent() >> null
            isComposite() >> true
        }
        def specClass = Mock(TestDescriptorInternal) {
            getId() >> "specClass"
            getParent() >> root
            isComposite() >> false
        }
        def classMethod = Mock(TestDescriptorInternal) {
            getId() >> "classMethod"
            getParent() >> specClass
            isComposite() >> false
        }

        and: "the spec-class descriptor is reported as a non-composite leaf under the root group"
        rootGroupReporter.reportTestDirectly(specClass) >> specClassReporter

        and: "the root and the spec-class start normally"
        listener.started(root, new TestStartEvent(0L))
        listener.started(specClass, new TestStartEvent(0L))

        when: "a synthetic child descriptor is started under the (now-leaf) spec-class"
        listener.started(classMethod, new TestStartEvent(0L))

        then: "the unguarded cast throws ClassCastException naming GroupTestEventReporterInternal"
        def e = thrown(ClassCastException)
        e.message.contains("GroupTestEventReporterInternal")
    }
}
