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

package org.gradle.api.internal.tasks.testing.junit

import org.gradle.api.InvalidUserDataException
import org.gradle.internal.concurrent.ThreadSafe
import org.junit.runner.notification.RunListener
import spock.lang.Specification

class JUnitTestClassExecutorTest extends Specification {

    class TestClassExecution implements TestClassExecutionListener, ThreadSafe {

        @Override
        void testClassStarted(String testClassName) {
        }

        @Override
        void testClassFinished(Throwable failure) {
        }
    }

    JUnitTestClassExecuter withListenerClass(ClassLoader applicationClassLoader, Class listenerClass) {
        def spec = new JUnitSpec([] as Set, [] as Set, [listenerClass.canonicalName] as Set, "")
        new JUnitTestClassExecuter(applicationClassLoader, spec, null, new TestClassExecution())
    }
    JUnitTestClassExecuter withListenerClass(ClassLoader applicationClassLoader, Set<String> listenerClasses) {
        def spec = new JUnitSpec([] as Set, [] as Set, listenerClasses, "")
        new JUnitTestClassExecuter(applicationClassLoader, spec, new RunListener(), new TestClassExecution())
    }

    def "custom request class not specified does not break execution"() {
        given: "empty spec"
        def spec = new JUnitSpec([] as Set, [] as Set, [] as Set, "")

        when:
        new JUnitTestClassExecuter(this.class.classLoader, spec, new RunListener(), new TestClassExecution())
            .execute(ATestClass.canonicalName)

        then: noExceptionThrown()
    }

    def "non-existing custom request class throws exception"() {
        given: "non-existing custom request class"
        def spec = new JUnitSpec([] as Set, [] as Set, [] as Set, "this.class.does.not.exist")

        when: "executer executes the test class"
        new JUnitTestClassExecuter(this.class.classLoader, spec, new RunListener(), new TestClassExecution())
            .execute(ATestClass.canonicalName)

        then: thrown(InvalidUserDataException)
    }

    def "unsupported custom request class"() {
        given: 'custom request class with no-class constructor'
        def spec = new JUnitSpec([] as Set, [] as Set, [] as Set, RuequestClassWithoutClassConstructor.canonicalName)

        when: "executer executes the test class"
        new JUnitTestClassExecuter(this.class.classLoader, spec, new RunListener(), new TestClassExecution())
            .execute(ATestClass.canonicalName)

        then:
        def e = thrown(InvalidUserDataException)
        e.message.contains('constructor')
    }

    def "custom request class is used"() {
        given:
        def spec = new JUnitSpec([] as Set, [] as Set, [] as Set, ValidRequestClass.canonicalName)
        def listener = new TestClassExecution() {
            Throwable err
            @Override void testClassFinished(Throwable failure) { err = failure }
        }

        when:
        new JUnitTestClassExecuter(this.class.classLoader, spec, new RunListener(), listener)
            .execute(ATestClass.canonicalName)

        then:
        listener.err.message == 'bang!'
    }
}
