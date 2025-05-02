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

package org.gradle.api.internal


import org.gradle.api.Action
import org.gradle.internal.Actions
import org.spockframework.runtime.UnallowedExceptionThrownError
import spock.lang.Specification
import spock.lang.Subject

@Subject(DefaultMutationGuard)
class DefaultMutationGuardTest extends Specification {
    final MutationGuard guard = new DefaultMutationGuard()

    def target = new Object()

    def "does not throw exception when calling a disallowed method when allowed using #methodUnderTest(#callableClass.type)"() {
        def callable = callableClass.newInstance(this)

        when:
        ensureExecuted(guard."${methodUnderTest}"(callable))

        then:
        noExceptionThrown()
        callable.called

        where:
        methodUnderTest    | callableClass
        "wrapEagerAction"  | ActionCallingDisallowedMethod
    }

    def "throws IllegalStateException when calling a disallowed method when disallowed using #methodUnderTest(#callableClass.type)"() {
        def callable = callableClass.newInstance(this)

        when:
        ensureExecuted(guard."${methodUnderTest}"(callable))

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "${target.class.simpleName}#someProtectedMethod() on ${target.toString()} cannot be executed in the current context."

        where:
        methodUnderTest         | callableClass
        "wrapLazyAction"  | ActionCallingDisallowedMethod
    }

    def "doesn't throw exception when calling disallowed method when allowed"() {
        when:
        disallowedMethod()

        then:
        noExceptionThrown()
    }

    def "call to #methodUnderTest(#callableType) inside wrapLazyAction(Action) does not disable disallow check"() {
        def aMethodUnderTest = methodUnderTest
        def aCallable = callable

        when:
        guard.wrapLazyAction(new Action<Void>() {
            @Override
            void execute(Void aVoid) {
                ensureExecuted(guard."${aMethodUnderTest}"(aCallable))
                disallowedMethod()
            }
        }).execute(null)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "${target.class.simpleName}#someProtectedMethod() on ${target.toString()} cannot be executed in the current context."

        where:
        methodUnderTest     | callableType | callable
        "wrapLazyAction"    | "Action"     | Actions.doNothing()
        "wrapEagerAction"   | "Action"     | Actions.doNothing()
    }

    def "call to #methodUnderTest(#callableType) inside wrapEagerAction(Action) does enable disallow check outside scope"() {
        def aMethodUnderTest = methodUnderTest
        def aCallable = callable

        when:
        guard.wrapEagerAction(new Action<Void>() {
            @Override
            void execute(Void aVoid) {
                ensureExecuted(guard."${aMethodUnderTest}"(aCallable))
                disallowedMethod()
            }
        }).execute(null)

        then:
        noExceptionThrown()

        where:
        methodUnderTest     | callableType | callable
        "wrapLazyAction"    | "Action"     | Actions.doNothing()
        "wrapEagerAction"   | "Action"     | Actions.doNothing()
    }

    def "doesn't protect across thread boundaries"() {
        given:
        def callable = new ActionCallingDisallowedMethod()
        def action = guard.wrapLazyAction(new Action<Void>() {
            @Override
            void execute(Void aVoid) {
                def thread = new Thread(new Runnable() {
                    @Override
                    void run() {
                        callable.execute(aVoid)
                    }
                })
                thread.start()
                thread.join()
            }
        })

        when:
        action.execute(null)

        then:
        noExceptionThrown()
        callable.noExceptionThrown()
        callable.called
    }

    private void disallowedMethod() {
        guard.assertEagerContext("someProtectedMethod()", target)
    }

    protected void ensureExecuted(def callable) {
        if (callable instanceof Action) {
            callable.execute(null)
        }
    }

    abstract class AbstractCallingDisallowedMethod {
        private boolean called = false
        private Throwable thrown = null

        protected void call() {
            try {
                called = true
                disallowedMethod()
            } catch (Throwable ex) {
                thrown = ex
                throw thrown
            }
        }

        void noExceptionThrown() {
            if (thrown == null) {
                return
            }
            throw new UnallowedExceptionThrownError(null, thrown)
        }

        boolean isCalled() {
            return called
        }
    }

    class ActionCallingDisallowedMethod extends AbstractCallingDisallowedMethod implements Action<Void> {
        static String getType() {
            return "Action"
        }

        @Override
        void execute(Void aVoid) {
            call()
        }
    }

    class RunnableCallingDisallowedMethod extends AbstractCallingDisallowedMethod implements Runnable {
        static String getType() {
            return "Runnable"
        }

        @Override
        void run() {
            call()
        }
    }

    class FactoryCallingDisallowedMethod extends AbstractCallingDisallowedMethod implements org.gradle.internal.Factory<Void> {
        static String getType() {
            return "Factory"
        }

        @Override
        Void create() {
            call()
            return null
        }
    }
}
