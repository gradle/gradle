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

import org.gradle.internal.classpath.intercept.CallInterceptorResolver
import org.gradle.internal.classpath.intercept.CallInterceptorsSet
import spock.lang.Specification

import static org.gradle.internal.classpath.BasicCallInterceptionTestInterceptorsDeclaration.GROOVY_GENERATED_CLASS
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.INVOKE_METHOD

class CallInterceptingMetaClassTest extends Specification {

    CallInterceptorResolver callInterceptors = new CallInterceptorsSet(GroovyCallInterceptorsProvisionTools.getInterceptorsFromClass(GROOVY_GENERATED_CLASS).stream())
    private MetaClass originalMetaClass = null
    private static InterceptorTestReceiver instance = null

    def setup() {
        originalMetaClass = InterceptorTestReceiver.metaClass
        GroovySystem.metaClassRegistry.setMetaClass(InterceptorTestReceiver, new CallInterceptingMetaClass(GroovySystem.metaClassRegistry, InterceptorTestReceiver, InterceptorTestReceiver.metaClass, callInterceptors))
        instance = new InterceptorTestReceiver()
    }

    def cleanup() {
        GroovySystem.metaClassRegistry.setMetaClass(InterceptorTestReceiver, originalMetaClass)
        instance = null
    }

    def 'intercepts a dynamic call with no argument'() {
        when:
        withEntryPoint(INVOKE_METHOD, "test") {
            instance.invokeMethod("test", [].toArray())
        }

        then:
        instance.intercepted == "test()"
    }

    def 'intercepts a dynamic call with argument'() {
        when:
        withEntryPoint(INVOKE_METHOD, "test") {
            instance.test(instance)
        }

        then:
        instance.intercepted == "test(InterceptorTestReceiver)"
    }

    def 'intercepts a dynamic call to a non-existent method'() {
        when:
        withEntryPoint(INVOKE_METHOD, "nonExistent") {
            instance.nonExistent("test")
        }

        then:
        instance.intercepted == "nonExistent(String)-non-existent"

        when: "calling it outside the entry point scope"
        instance.nonExistent("test")

        then: "it should throw an exception as usual"
        thrown(MissingMethodException)
    }

    def 'intercepts only one call in a succession with a single entry point'() {
        when:
        withEntryPoint(INVOKE_METHOD, "test") {
            instance.test()
            assert instance.intercepted == "test()"
            instance.intercepted = null
            instance.test()
            instance.test(instance) // also call a different signature
        }

        then:
        instance.intercepted == null
    }

    def 'non-intercepted invokeMethod calls invoke the original method'() {
        when:
        withEntryPoint(INVOKE_METHOD, "callNonIntercepted") {
            instance.callNonIntercepted()
        }

        then:
        instance.intercepted == "callNotIntercepted()-not-intercepted"
    }

    def 'successive pickMethod invocations in the entry point scope return the intercepted method'() {
        when:
        def method1 = null
        def method2 = null
        withEntryPoint(INVOKE_METHOD, "test") {
            method1 = instance.metaClass.pickMethod("test", new Class[]{})
            method2 = instance.metaClass.pickMethod("test", new Class[]{InterceptorTestReceiver})
        }
        // not in the scope of an entry point, so should not be an intercepted method
        def method3 = instance.metaClass.pickMethod("test", new Class[]{String})

        then:
        method1 instanceof CallInterceptingMetaClass.InterceptedMetaMethod
        method2 instanceof CallInterceptingMetaClass.InterceptedMetaMethod
        !(method3 instanceof CallInterceptingMetaClass.InterceptedMetaMethod)
    }

    def 'intercepts invokeMethod in a closure'() {
        given:
        def closure = {
            testVararg()
        }
        closure.delegate = instance

        when:
        withEntryPoint(INVOKE_METHOD, "testVararg") {
            closure()
        }

        then:
        instance.intercepted == "testVararg(Object...)"
    }

    def 'intercepts invokeMethod in a nested closure'() {
        given:
        def closure = {
            withEntryPoint(INVOKE_METHOD, "testVararg") {
                testVararg()
            }
        }
        closure.delegate = instance

        when:
        closure()

        then:
        instance.intercepted == "testVararg(Object...)"
    }

    def 'intercepts getMetaMethod and pickMethod for matching signatures only'() {
        when:
        def methodByArgs = withEntryPoint(INVOKE_METHOD, name) {
            instance.metaClass.getMetaMethod(name, args.toArray())
        }
        then:
        (methodByArgs instanceof CallInterceptingMetaClass.InterceptedMetaMethod) == intercepted

        and:
        def methodByTypes = withEntryPoint(INVOKE_METHOD, name) {
            instance.metaClass.pickMethod(name, args.collect { it == null ? it : it.class }.toArray(Class[]::new))
        }
        intercepted == methodByTypes instanceof CallInterceptingMetaClass.InterceptedMetaMethod

        where:
        name             | args                                                           | intercepted

        "test"           | []                                                             | true
        "test"           | [new InterceptorTestReceiver()]                                | true
        "test"           | [null]                                                         | true
        "testVararg"     | [new InterceptorTestReceiver(), new InterceptorTestReceiver()] | true
        "testVararg"     | [new InterceptorTestReceiver(), null]                          | true
        "testVararg"     | [new InterceptorTestReceiver[]{new InterceptorTestReceiver()}] | true
        // can intercept non-existent methods:
        "nonExistent"    | ["test"]                                                       | true

        "unrelated"      | [1, 2, 3]                                                      | false
        "notIntercepted" | []                                                             | false
    }

    private static Object withEntryPoint(InstrumentedGroovyCallsTracker.CallKind kind, String name, Closure<?> call) {
        def entryPoint = InstrumentedGroovyCallsTracker.enterCall("from-test", name, kind)
        try {
            return call()
        } finally {
            InstrumentedGroovyCallsTracker.leaveCall(entryPoint)
        }
    }
}
