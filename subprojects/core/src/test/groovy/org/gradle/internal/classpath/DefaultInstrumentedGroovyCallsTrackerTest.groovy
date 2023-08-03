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

import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.GET_PROPERTY
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.INVOKE_METHOD

class DefaultInstrumentedGroovyCallsTrackerTest extends Specification {

    InstrumentedGroovyCallsTracker instance = new DefaultInstrumentedGroovyCallsTracker()

    def 'enters and leaves nested calls'() {
        given:
        def entryFoo = instance.enterCall("FooCallerClass", "foo", INVOKE_METHOD)
        def entryBar = instance.enterCall("BarCallerClass", "bar", INVOKE_METHOD)

        when:
        instance.leaveCall(entryBar)
        instance.leaveCall(entryFoo)

        then:
        noExceptionThrown()
    }

    def 'entry points are not interchangeable even if produced by similar calls'() {
        given:
        def entryFoo1 = instance.enterCall("FooCallerClass", "foo", INVOKE_METHOD)
        def entryFoo2 = instance.enterCall("FooCallerClass", "foo", INVOKE_METHOD)

        when:
        instance.leaveCall(entryFoo1)

        then:
        thrown(IllegalStateException)

        when:
        instance.leaveCall(entryFoo2)
        instance.leaveCall(entryFoo1)

        then:
        noExceptionThrown()
    }

    def 'throws an exception on leave if an entry point does not match because it #entryPointKind'() {
        given:
        def entryFoo = instance.enterCall("FooCallerClass", "foo", INVOKE_METHOD)
        def entryBar = instance.enterCall("BarCallerClass", "bar", INVOKE_METHOD)
        def entryBaz = instance.enterCall("BarCallerClass", "baz", INVOKE_METHOD)
        instance.leaveCall(entryBaz)
        def entryPointsToTest = [entryBaz, entryFoo]

        when:
        instance.leaveCall(entryPointsToTest[index])

        then:
        thrown(IllegalStateException)
        instance.leaveCall(entryBar)
        instance.leaveCall(entryFoo)

        where:
        entryPointKind                 | index
        "has already been popped"      | 0
        "is still deeper in the stack" | 1
    }

    def 'throws an exception on instance.markCurrentCallAsIntercepted if its #part does not match'() {
        given:
        def entryFoo = instance.enterCall("FooCallerClass", "foo", INVOKE_METHOD)

        when:
        instance.markCurrentCallAsIntercepted(name, kind)

        then:
        thrown(IllegalStateException)
        instance.leaveCall(entryFoo)

        where:
        part   | name  | kind
        "name" | "!!!" | INVOKE_METHOD
        "kind" | "foo" | GET_PROPERTY
    }

    def 'throws an exception when trying to match a call twice'() {
        given:
        def entryFoo = instance.enterCall("FooCallerClass", "foo", INVOKE_METHOD)
        instance.markCurrentCallAsIntercepted("foo", INVOKE_METHOD)

        when:
        instance.markCurrentCallAsIntercepted("foo", INVOKE_METHOD)

        then:
        thrown(IllegalStateException)
        instance.leaveCall(entryFoo)
    }

    def 'matches the innermost nested call'() {
        def entryFoo = instance.enterCall("FooCallerClass", "foo", INVOKE_METHOD)
        def entryBar = instance.enterCall("BarCallerClass", "bar", INVOKE_METHOD)

        when:
        def barCaller = instance.findCallerForCurrentCallIfNotIntercepted("bar", INVOKE_METHOD)

        then:
        barCaller == "BarCallerClass"

        when:
        def fooCaller = instance.findCallerForCurrentCallIfNotIntercepted("foo", INVOKE_METHOD)
        instance.leaveCall(entryBar)

        then:
        fooCaller == null

        when:
        fooCaller = instance.findCallerForCurrentCallIfNotIntercepted("foo", INVOKE_METHOD)
        instance.leaveCall(entryFoo)

        then:
        fooCaller == "FooCallerClass"
    }

    def 'does not match a call if it has been marked as intercepted'() {
        def entryFoo = instance.enterCall("FooCallerClass", "foo", INVOKE_METHOD)

        when:
        def before = instance.findCallerForCurrentCallIfNotIntercepted("foo", INVOKE_METHOD)
        instance.markCurrentCallAsIntercepted("foo", INVOKE_METHOD)
        def after = instance.findCallerForCurrentCallIfNotIntercepted("foo", INVOKE_METHOD)
        instance.leaveCall(entryFoo)

        then:
        before == "FooCallerClass"
        after == null
    }
}
