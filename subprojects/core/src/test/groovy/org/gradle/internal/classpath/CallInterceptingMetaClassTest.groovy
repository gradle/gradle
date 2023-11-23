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

import org.gradle.internal.classpath.intercept.DefaultCallSiteDecorator
import org.gradle.internal.classpath.intercept.DefaultCallSiteInterceptorSet
import org.gradle.internal.classpath.intercept.CallSiteInterceptorSet
import spock.lang.Specification

import static org.gradle.internal.classpath.BasicCallInterceptionTestInterceptorsDeclaration.TEST_GENERATED_CLASSES_PACKAGE
import static org.gradle.internal.classpath.GroovyCallInterceptorsProvider.ClassLoaderSourceGroovyCallInterceptorsProvider
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.GET_PROPERTY
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.INVOKE_METHOD
import static org.gradle.internal.classpath.InstrumentedGroovyCallsTracker.CallKind.SET_PROPERTY
import static org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter.ALL

class CallInterceptingMetaClassTest extends Specification {

    CallSiteInterceptorSet callInterceptors = new DefaultCallSiteInterceptorSet(new ClassLoaderSourceGroovyCallInterceptorsProvider(this.class.classLoader, TEST_GENERATED_CLASSES_PACKAGE));
    private MetaClass originalMetaClass = null
    private static InterceptorTestReceiver instance = null

    // We don't want to interfere with other tests that modify the meta class
    def interceptorTestReceiverClassLock = new ClassBasedLock(InterceptorTestReceiver)

    def callTracker = new DefaultInstrumentedGroovyCallsTracker()

    def setup() {
        interceptorTestReceiverClassLock.lock()
        originalMetaClass = InterceptorTestReceiver.metaClass
        def interceptors = callInterceptors.getCallInterceptors(ALL)
        def callSiteResolver = new DefaultCallSiteDecorator(interceptors)
        GroovySystem.metaClassRegistry.setMetaClass(
            InterceptorTestReceiver,
            new CallInterceptingMetaClass(GroovySystem.metaClassRegistry, InterceptorTestReceiver, InterceptorTestReceiver.metaClass, callTracker, callSiteResolver)
        )
        instance = new InterceptorTestReceiver()
    }

    def cleanup() {
        GroovySystem.metaClassRegistry.setMetaClass(InterceptorTestReceiver, originalMetaClass)
        instance = null
        interceptorTestReceiverClassLock.unlock()
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
            instance.invokeMethod("test", [].toArray())
        }

        then:
        instance.intercepted == "test()"
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

    def 'method invocation with #name on a metaClass is intercepted: #intercepted'() {
        MissingMethodException thrown = null

        when:
        withEntryPoint(INVOKE_METHOD, "test") {
            try {
                closure()
            } catch (MissingMethodException e) {
                thrown = e
            }
        }

        then:
        (thrown != null) == missing
        instance.intercepted == (intercepted ? "test()" : null)

        where:
        name                                                                  | closure                                                                                       | intercepted | missing
        "invokeMethod(receiver, methodName, (Object[]) arguments)"            | { instance.metaClass.invokeMethod(instance, "test", [].toArray()) }                           | true        | false
        "invokeMethod(receiver, methodName, (Object) arguments)"              | { instance.metaClass.invokeMethod(instance, "test", (Object) [].toArray()) }                  | true        | false
        "invokeMethod(sender, receiver, methodName, arguments, false, false)" | { instance.metaClass.invokeMethod(getClass(), instance, "test", [].toArray(), false, false) } | true        | false

        // These should not be intercepted because of isCallToSuper and fromInsideClass
        "invokeMethod(sender, receiver, methodName, arguments, true, false)"  | { instance.metaClass.invokeMethod(getClass(), instance, "test", [].toArray(), true, false) }  | false       | true
        "invokeMethod(sender, receiver, methodName, arguments, false, true)"  | { instance.metaClass.invokeMethod(getClass(), instance, "test", [].toArray(), false, true) }  | false       | true
        "invokeMethod(sender, receiver, methodName, arguments, true, true)"   | { instance.metaClass.invokeMethod(getClass(), instance, "test", [].toArray(), true, true) }   | false       | true
    }

    def 'non-intercepted invokeMethod calls invoke the original method'() {
        when:
        withEntryPoint(INVOKE_METHOD, "callNonIntercepted") {
            instance.callNonIntercepted()
        }

        then:
        instance.intercepted == "callNotIntercepted()-not-intercepted"
    }

    def 'a metamethod obtained within an entry point scope does not break out of the scope'() {
        MetaMethod method = null

        when:
        withEntryPoint(INVOKE_METHOD, "test") {
            method = instance.metaClass.getMetaMethod("test")
        }
        method.invoke(instance, [].toArray())

        then: 'the call should not be intercepted'
        instance.intercepted == null
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
            instance.metaClass.pickMethod(name, args.collect { it == null ? null : it.class }.toArray(new Class[0]) as Class[])
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

    def 'property access via #method property from closure is intercepted: #intercepted'() {
        MissingPropertyException missingPropertyException = null

        when:
        def result = withEntryPoint(GET_PROPERTY, "testString") {
            try {
                propertyAccess()
            } catch (MissingPropertyException e) {
                missingPropertyException = e
            }
        }

        then:
        missing == (missingPropertyException != null)
        missing || (result == (intercepted ? "testString-intercepted" : "testString"))
        instance.intercepted == (intercepted ? "getTestString()" : null)

        where:
        method                                             | propertyAccess                                                                       | intercepted | missing
        "getProperty(Object, String)"                      | { instance.metaClass.getProperty(instance, "testString") }                           | true        | false
        "getProperty(Class, Object, String, false, false)" | { instance.metaClass.getProperty(getClass(), instance, "testString", false, false) } | true        | false

        // These are not supported because of isCallToSuper or fromInsideClass
        "getProperty(Class, Object, String, true, false)"  | { instance.metaClass.getProperty(getClass(), instance, "testString", true, false) }  | false       | true
        "getProperty(Class, Object, String, false, true)"  | { instance.metaClass.getProperty(getClass(), instance, "testString", false, true) }  | false       | false
        "getProperty(Class, Object, String, true, true)"   | { instance.metaClass.getProperty(getClass(), instance, "testString", true, true) }   | false       | true
    }

    def 'only one property read is intercepted per entry point'() {
        when:
        def result = withEntryPoint(GET_PROPERTY, "testString") {
            [
                instance.metaClass.getProperty(instance, "testString"),
                instance.metaClass.getProperty(instance, "testString")
            ]
        }

        then:
        result == ["testString-intercepted", "testString"]
    }

    def 'intercepts setting #type property #method from closure'() {
        given:
        setExpr.delegate = instance

        when:
        withEntryPoint(SET_PROPERTY, propertyName) {
            setExpr()
        }

        then:
        instance.intercepted == intercepting

        where:
        method            | type      | intercepting                                  | propertyName          | setExpr
        "directly"        | "string"  | "setTestString(String)"                       | "testString"          | { testString = "!" }
        "directly"        | "boolean" | "setTestFlag(boolean)"                        | "testFlag"            | { testFlag = true }
        "directly"        | "string"  | "setNonExistentProperty(String)-non-existent" | "nonExistentProperty" | { nonExistentProperty = "!" }
        "via setProperty" | "string"  | "setTestString(String)"                       | "testString"          | { instance.metaClass.setProperty(null, instance, "testString", "!", false, false) }
        "via setProperty" | "boolean" | "setTestFlag(boolean)"                        | "testFlag"            | { instance.metaClass.setProperty(null, instance, "testFlag", true, false, false) }
        "via setProperty" | "string"  | "setNonExistentProperty(String)-non-existent" | "nonExistentProperty" | { instance.metaClass.setProperty(null, instance, "nonExistentProperty", "!", false, false) }
    }

    def 'intercepts getMetaProperty for matching properties'() {
        MetaProperty property = null

        when:
        withEntryPoint(GET_PROPERTY, propertyName) {
            property = instance.metaClass.getMetaProperty(propertyName)
        }

        then:
        property != null
        (property instanceof CallInterceptingMetaClass.InterceptedMetaProperty) == shouldIntercept

        where:
        propertyName | shouldIntercept
        "testString" | true
        "metaClass"  | false
    }

    def 'successive calls to getMetaProperty in the scope of an entry point all return the intercepted property'() {
        List<MetaProperty> properties = []

        when:
        withEntryPoint(GET_PROPERTY, "testString") {
            properties.add(instance.metaClass.getMetaProperty("testString"))
            properties.add(instance.metaClass.getMetaProperty("testString"))
        }
        withEntryPoint(SET_PROPERTY, "testString") {
            properties.add(instance.metaClass.getMetaProperty("testString"))
            properties.add(instance.metaClass.getMetaProperty("testString"))
        }

        then:
        properties.size() == 4
        properties.every { it instanceof CallInterceptingMetaClass.InterceptedMetaProperty }
    }

    private Object withEntryPoint(InstrumentedGroovyCallsTracker.CallKind kind, String name, Closure<?> call) {
        def entryPoint = callTracker.enterCall("from-test", name, kind)
        try {
            return call()
        } finally {
            callTracker.leaveCall(entryPoint)
        }
    }
}
