/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junitplatform

import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.junit.TestClassExecutionListener
import org.gradle.internal.actor.ActorFactory
import org.gradle.internal.id.IdGenerator
import org.gradle.internal.time.Clock
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import spock.lang.Specification
import spock.lang.Subject

class JUnitPlatformTestClassProcessorTest extends Specification {
    JUnitPlatformSpec spec = Mock()
    IdGenerator idGenerator = Mock()
    ActorFactory actorFactory = Mock()
    Clock clock = Mock()
    TestResultProcessor resultProcessor = Mock()
    TestClassExecutionListener listener = Mock()

    @Subject
    JUnitPlatformTestClassProcessor processor = new JUnitPlatformTestClassProcessor(spec, idGenerator, actorFactory, clock)

    def 'can exclude anonymous class'() {
        given:
        def anonymousInstance = new Object() {}
        def executor = processor.createTestExecutor(resultProcessor, listener)

        when:
        executor.execute(anonymousInstance.class.name)

        then:
        executor.testClasses.isEmpty()
    }

    class InnerClass {}

    static class NestedClass {}

    def 'can exclude inner class but include nested class'() {
        given:
        def executor = processor.createTestExecutor(resultProcessor, listener)

        when:
        executor.execute(InnerClass.name)
        executor.execute(NestedClass.name)

        then:
        executor.testClasses == [NestedClass.name]
    }

    def 'can exclude nested class in Enclosed runner'() {
        given:
        def executor = processor.createTestExecutor(resultProcessor, listener)

        when:
        executor.execute(EnclosedTest.NestedClass.name)

        then:
        executor.testClasses.isEmpty()
    }
}

@RunWith(Enclosed.class)
class EnclosedTest {
    static class NestedClass {
    }
}
