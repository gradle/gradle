/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath

import spock.lang.Specification

import java.util.concurrent.CountDownLatch

import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.GET_PROPERTY
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.INVOKE_METHOD
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.enterCall
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.findCallerForCurrentCallIfNotIntercepted
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.leaveCall
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.markCurrentCallAsIntercepted

class InstrumentedGroovyCallsTrackerTest extends Specification {

    def 'enters and leaves nested calls'() {
        given:
        def entryFoo = enterCall("FooCallerClass", "foo", INVOKE_METHOD)
        def entryBar = enterCall("BarCallerClass", "bar", INVOKE_METHOD)

        when:
        leaveCall(entryBar)
        leaveCall(entryFoo)

        then:
        noExceptionThrown()
    }

    def 'entry points are not interchangeable even if produced by similar calls'() {
        given:
        def entryFoo1 = enterCall("FooCallerClass", "foo", INVOKE_METHOD)
        def entryFoo2 = enterCall("FooCallerClass", "foo", INVOKE_METHOD)

        when:
        leaveCall(entryFoo1)

        then:
        thrown(IllegalStateException)

        when:
        leaveCall(entryFoo2)
        leaveCall(entryFoo1)

        then:
        noExceptionThrown()
    }

    def 'throws an exception on leave if an entry point does not match because it #entryPointKind'() {
        given:
        def entryFoo = enterCall("FooCallerClass", "foo", INVOKE_METHOD)
        def entryBar = enterCall("BarCallerClass", "bar", INVOKE_METHOD)
        def entryBaz = enterCall("BarCallerClass", "baz", INVOKE_METHOD)
        leaveCall(entryBaz)
        def entryPointsToTest = [entryBaz, entryFoo]

        when:
        leaveCall(entryPointsToTest[index])

        then:
        thrown(IllegalStateException)
        leaveCall(entryBar)
        leaveCall(entryFoo)

        where:
        entryPointKind                 | index
        "has already been popped"      | 0
        "is still deeper in the stack" | 1
    }

    def 'throws an exception on markCurrentCallAsIntercepted if its #part does not match'() {
        given:
        def entryFoo = enterCall("FooCallerClass", "foo", INVOKE_METHOD)

        when:
        markCurrentCallAsIntercepted(name, kind)

        then:
        thrown(IllegalStateException)
        leaveCall(entryFoo)

        where:
        part   | name  | kind
        "name" | "!!!" | INVOKE_METHOD
        "kind" | "foo" | GET_PROPERTY
    }

    def 'throws an exception when trying to match a call twice'() {
        given:
        def entryFoo = enterCall("FooCallerClass", "foo", INVOKE_METHOD)
        markCurrentCallAsIntercepted("foo", INVOKE_METHOD)

        when:
        markCurrentCallAsIntercepted("foo", INVOKE_METHOD)

        then:
        thrown(IllegalStateException)
        leaveCall(entryFoo)
    }

    def 'matches the innermost nested call'() {
        def entryFoo = enterCall("FooCallerClass", "foo", INVOKE_METHOD)
        def entryBar = enterCall("BarCallerClass", "bar", INVOKE_METHOD)

        when:
        def barCaller = findCallerForCurrentCallIfNotIntercepted("bar", INVOKE_METHOD)

        then:
        barCaller == "BarCallerClass"

        when:
        def fooCaller = findCallerForCurrentCallIfNotIntercepted("foo", INVOKE_METHOD)
        leaveCall(entryBar)

        then:
        fooCaller == null

        when:
        fooCaller = findCallerForCurrentCallIfNotIntercepted("foo", INVOKE_METHOD)
        leaveCall(entryFoo)

        then:
        fooCaller == "FooCallerClass"
    }

    def 'tracks the calls per thread'() {
        def latchBefore = new CountDownLatch(1)
        def latchAfter = new CountDownLatch(1)
        boolean succeeded = false

        when:
        def thread1 = new Thread({
            def e = enterCall("T1", "t1", INVOKE_METHOD)
            latchBefore.await()
            leaveCall(e) // this should happen between enterCall and leaveCall in thread2
            latchAfter.countDown()
            succeeded = true
        })
        def thread2 = new Thread({
            def e = enterCall("T2", "t2", INVOKE_METHOD)
            latchBefore.countDown()
            latchAfter.await()
            leaveCall(e)
        })
        thread1.start()
        thread2.start()
        thread1.join()
        thread1.join()

        then:
        succeeded
    }

    def 'does not match a call if it has been marked as intercepted'() {
        def entryFoo = enterCall("FooCallerClass", "foo", INVOKE_METHOD)

        when:
        def before = findCallerForCurrentCallIfNotIntercepted("foo", INVOKE_METHOD)
        markCurrentCallAsIntercepted("foo", INVOKE_METHOD)
        def after = findCallerForCurrentCallIfNotIntercepted("foo", INVOKE_METHOD)
        leaveCall(entryFoo)

        then:
        before == "FooCallerClass"
        after == null
    }

}
