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

import com.google.common.util.concurrent.Runnables
import org.gradle.api.Action
import org.gradle.internal.Actions
import org.gradle.internal.Factories
import org.gradle.testing.internal.util.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(DefaultMutationGuard)
class DefaultMutationGuardTest extends Specification {
    def target = Stub(Object)
    def guard = new DefaultMutationGuard()
    def element = Stub(Object)

    def called = false
    def actionCallingDisallowedMethod = new Action<Object>() {
        @Override
        void execute(Object obj) {
            called = true
            disallowedMethod()
        }
    }

    def runnableCallingDisallowedMethod = new Runnable() {
        @Override
        void run() {
            called = true
            disallowedMethod()
        }
    }

    def factoryCallingDisallowedMethod = new org.gradle.internal.Factory<Object>() {
        @Override
        Object create() {
            called = true
            disallowedMethod()
            return null
        }
    }

    @Unroll
    def "does not throw exception when calling a disallowed method when allowed using #description"() {
        when:
        closure(this)

        then:
        noExceptionThrown()
        called

        where:
        description                      | closure
        "withMutationEnabled(Action)"    | { it.guard.withMutationEnabled(it.actionCallingDisallowedMethod).execute(new Object()) }
        "whileMutationEnabled(Runnable)" | { it.guard.whileMutationEnabled(it.runnableCallingDisallowedMethod) }
        "whileMutationEnabled(Factory)"  | { it.guard.whileMutationEnabled(it.factoryCallingDisallowedMethod) }
    }

    @Unroll
    def "throws IllegalStateException when calling a disallowed method when disallowed using #description"() {
        when:
        closure(this)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "${element.class.simpleName}#someProtectedMethod() on ${target.toString()} cannot be executed in the current context."

        where:
        description                      | closure
        "withMutationDisabled(Action)"    | { it.guard.withMutationDisabled(it.actionCallingDisallowedMethod).execute(new Object()) }
        "whileMutationDisabled(Runnable)" | { it.guard.whileMutationDisabled(it.runnableCallingDisallowedMethod) }
        "whileMutationDisabled(Factory)"  | { it.guard.whileMutationDisabled(it.factoryCallingDisallowedMethod) }
    }

    def "doesn't throw exception when calling disallowed method when allowed"() {
        when:
        disallowedMethod()

        then:
        noExceptionThrown()
    }

    @Unroll
    def "call to #description inside withMutationDisabled(Action) does not disable disallow check"() {
        def c = closure

        when:
        guard.withMutationDisabled(new Action<Object>() {
            @Override
            void execute(Object obj) {
                c(guard)
                disallowedMethod()
            }
        }).execute(element)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "${element.class.simpleName}#someProtectedMethod() on ${target.toString()} cannot be executed in the current context."

        where:
        description                       | closure
        "withMutationDisabled(Action)"    | { it.withMutationDisabled(Actions.doNothing()).execute(new Object()) }
        "whileMutationDisabled(Runnable)" | { it.whileMutationDisabled(Runnables.doNothing()) }
        "whileMutationDisabled(Factory)"  | { it.whileMutationDisabled(Factories.toFactory(Runnables.doNothing())) }
        "withMutationEnabled(Action)"     | { it.withMutationEnabled(Actions.doNothing()).execute(new Object()) }
        "whileMutationEnabled(Runnable)"  | { it.whileMutationEnabled(Runnables.doNothing()) }
        "whileMutationEnabled(Factory)"   | { it.whileMutationEnabled(Factories.toFactory(Runnables.doNothing())) }
    }

    @Unroll
    def "call to #description inside withMutationEnabled(Action) does enable disallow check outside scope"() {
        def c = closure

        when:
        guard.withMutationEnabled(new Action<Object>() {
            @Override
            void execute(Object obj) {
                c(guard)
                disallowedMethod()
            }
        }).execute(element)

        then:
        noExceptionThrown()

        where:
        description                       | closure
        "withMutationDisabled(Action)"    | { it.withMutationDisabled(Actions.doNothing()).execute(new Object()) }
        "whileMutationDisabled(Runnable)" | { it.whileMutationDisabled(Runnables.doNothing()) }
        "whileMutationDisabled(Factory)"  | { it.whileMutationDisabled(Factories.toFactory(Runnables.doNothing())) }
        "withMutationEnabled(Action)"     | { it.withMutationEnabled(Actions.doNothing()).execute(new Object()) }
        "whileMutationEnabled(Runnable)"  | { it.whileMutationEnabled(Runnables.doNothing()) }
        "whileMutationEnabled(Factory)"   | { it.whileMutationEnabled(Factories.toFactory(Runnables.doNothing())) }
    }

    @Unroll
    def "call to #description inside whileMutationDisabled(Runnable) does not disable disallow check"() {
        def c = closure

        when:
        guard.whileMutationDisabled(new Runnable() {
            @Override
            void run() {
                c(guard)
                disallowedMethod()
            }
        })

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "${element.class.simpleName}#someProtectedMethod() on ${target.toString()} cannot be executed in the current context."

        where:
        description                       | closure
        "withMutationDisabled(Action)"    | { it.withMutationDisabled(Actions.doNothing()).execute(new Object()) }
        "whileMutationDisabled(Runnable)" | { it.whileMutationDisabled(Runnables.doNothing()) }
        "whileMutationDisabled(Factory)"  | { it.whileMutationDisabled(Factories.toFactory(Runnables.doNothing())) }
        "withMutationEnabled(Action)"     | { it.withMutationEnabled(Actions.doNothing()).execute(new Object()) }
        "whileMutationEnabled(Runnable)"  | { it.whileMutationEnabled(Runnables.doNothing()) }
        "whileMutationEnabled(Factory)"   | { it.whileMutationEnabled(Factories.toFactory(Runnables.doNothing())) }
    }

    @Unroll
    def "call to #description inside whileMutationEnabled(Runnable) does enable disallow check outside scope"() {
        def c = closure

        when:
        guard.whileMutationEnabled(new Runnable() {
            @Override
            void run() {
                c(guard)
                disallowedMethod()
            }
        })

        then:
        noExceptionThrown()

        where:
        description                       | closure
        "withMutationDisabled(Action)"    | { it.withMutationDisabled(Actions.doNothing()).execute(new Object()) }
        "whileMutationDisabled(Runnable)" | { it.whileMutationDisabled(Runnables.doNothing()) }
        "whileMutationDisabled(Factory)"  | { it.whileMutationDisabled(Factories.toFactory(Runnables.doNothing())) }
        "withMutationEnabled(Action)"     | { it.withMutationEnabled(Actions.doNothing()).execute(new Object()) }
        "whileMutationEnabled(Runnable)"  | { it.whileMutationEnabled(Runnables.doNothing()) }
        "whileMutationEnabled(Factory)"   | { it.whileMutationEnabled(Factories.toFactory(Runnables.doNothing())) }
    }

    @Unroll
    def "call to #description inside whileMutationDisabled(Factory) does not disable disallow check"() {
        def c = closure

        when:
        guard.whileMutationDisabled(new org.gradle.internal.Factory<Object>() {
            @Override
            Object create() {
                c(guard)
                disallowedMethod()
                return null
            }
        })

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "${element.class.simpleName}#someProtectedMethod() on ${target.toString()} cannot be executed in the current context."

        where:
        description                       | closure
        "withMutationDisabled(Action)"    | { it.withMutationDisabled(Actions.doNothing()).execute(new Object()) }
        "whileMutationDisabled(Runnable)" | { it.whileMutationDisabled(Runnables.doNothing()) }
        "whileMutationDisabled(Factory)"  | { it.whileMutationDisabled(Factories.toFactory(Runnables.doNothing())) }
        "withMutationEnabled(Action)"     | { it.withMutationEnabled(Actions.doNothing()).execute(new Object()) }
        "whileMutationEnabled(Runnable)"  | { it.whileMutationEnabled(Runnables.doNothing()) }
        "whileMutationEnabled(Factory)"   | { it.whileMutationEnabled(Factories.toFactory(Runnables.doNothing())) }
    }

    @Unroll
    def "call to #description inside whileMutationEnabled(Factory) does enable disallow check outside scope"() {
        def c = closure

        when:
        guard.whileMutationEnabled(new org.gradle.internal.Factory<Object>() {
            @Override
            Object create() {
                c(guard)
                disallowedMethod()
                return null
            }
        })

        then:
        noExceptionThrown()

        where:
        description                       | closure
        "withMutationDisabled(Action)"    | { it.withMutationDisabled(Actions.doNothing()).execute(new Object()) }
        "whileMutationDisabled(Runnable)" | { it.whileMutationDisabled(Runnables.doNothing()) }
        "whileMutationDisabled(Factory)"  | { it.whileMutationDisabled(Factories.toFactory(Runnables.doNothing())) }
        "withMutationEnabled(Action)"     | { it.withMutationEnabled(Actions.doNothing()).execute(new Object()) }
        "whileMutationEnabled(Runnable)"  | { it.whileMutationEnabled(Runnables.doNothing()) }
        "whileMutationEnabled(Factory)"   | { it.whileMutationEnabled(Factories.toFactory(Runnables.doNothing())) }
    }

    def "doesn't protect across thread boundaries"() {
        given:
        def catchedException = null
        def action = guard.withMutationDisabled(new Action<Object>() {
            @Override
            void execute(Object obj) {
                def thread = new Thread(new Runnable() {
                    @Override
                    void run() {
                        try {
                            actionCallingDisallowedMethod.execute(obj)
                        } catch (Throwable t) {
                            catchedException = t
                        }
                    }
                })
                thread.start()
                thread.join()
            }
        })

        when:
        action.execute(element)

        then:
        noExceptionThrown()
        called
        catchedException == null
    }

    private void disallowedMethod() {
        guard.assertMutationAllowed("someProtectedMethod()", target)
    }
}
