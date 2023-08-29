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

import org.gradle.internal.metaobject.BeanDynamicObject

import java.lang.reflect.Constructor
import java.util.function.Predicate

import static org.gradle.internal.classpath.BasicCallInterceptionTestInterceptorsDeclaration.TEST_GENERATED_CLASSES_PACKAGE
import static org.gradle.internal.classpath.GroovyCallInterceptorsProvider.ClassLoaderSourceGroovyCallInterceptorsProvider
import static org.gradle.internal.classpath.GroovyCallInterceptorsProvider.DEFAULT

class GroovyDynamicDispatchingInterceptingTest extends AbstractCallInterceptionTest {

    // We don't want to interfere with other tests that modify the meta class
    def interceptorTestReceiverClassLock = new ClassBasedLock(InterceptorTestReceiver)
    def originalMetaClass = null

    def setup() {
        interceptorTestReceiverClassLock.lock()
        originalMetaClass = InterceptorTestReceiver.metaClass
    }

    def cleanup() {
        InterceptorTestReceiver.metaClass = originalMetaClass
        interceptorTestReceiverClassLock.unlock()
    }

    @Override
    protected Predicate<String> shouldInstrumentAndReloadClassByName() {
        InstrumentedClasses.nestedClassesOf(this.class) | Predicate.isEqual(JavaCallerForBasicCallInterceptorTest.class.name)
    }

    @Override
    protected JvmBytecodeInterceptorSet jvmBytecodeInterceptorSet() {
        JvmBytecodeInterceptorSet.DEFAULT
    }

    @Override
    protected GroovyCallInterceptorsProvider groovyCallInterceptors() {
        return DEFAULT + new ClassLoaderSourceGroovyCallInterceptorsProvider(this.getClass().classLoader, TEST_GENERATED_CLASSES_PACKAGE)
    }

    // Define a nested classes so that we can freely modify the metaClass of it loaded in the child class loader
    class NestedClass {}

    def 'setting a null delegate in a closure is safe'() {
        given:
        def transformedClosure = instrumentedClasses.instrumentedClosure {
            if (delegate != null) {
                test()
            }
            return null
        }
        def receiver = new InterceptorTestReceiver()

        when:
        transformedClosure.delegate = receiver
        transformedClosure()

        then: 'the closure is an effectively instrumented one now, so we can test its null safety'
        GroovySystem.metaClassRegistry.getMetaClass(InterceptorTestReceiver) instanceof CallInterceptingMetaClass

        when:
        transformedClosure.delegate = null
        transformedClosure()

        then:
        noExceptionThrown()
    }

    def 'setting a delegate of a closure before invoking an intercepted method in it replaces the metaclass of the delegate'() {
        given:
        def transformedClosure = instrumentedClasses.instrumentedClosure {
            test()
        }
        def receiver = new InterceptorTestReceiver()

        when:
        transformedClosure.delegate = receiver
        transformedClosure()

        then:
        GroovySystem.metaClassRegistry.getMetaClass(InterceptorTestReceiver) instanceof CallInterceptingMetaClass
    }

    def 'setting a delegate of a closure after invoking an intercepted method replaces the metaclass of the new delegate'() {
        given:
        def transformedClosure = instrumentedClasses.instrumentedClosure {
            test()
            delegate = new NestedClass()
            return GroovySystem.metaClassRegistry.getMetaClass(NestedClass) instanceof CallInterceptingMetaClass
        }
        transformedClosure.delegate = new InterceptorTestReceiver()

        when:
        def isNewDelegateMetaclassReplaced = transformedClosure()

        then:
        isNewDelegateMetaclassReplaced
        GroovySystem.metaClassRegistry.getMetaClass(InterceptorTestReceiver) instanceof CallInterceptingMetaClass
    }

    def 'invoking a closure constructor with a specific owner replaces the metaclass of the delegate'() {
        given:
        def transformedClosureClass = instrumentedClasses.instrumentedClosure {
            test()
        }
        def constructor = transformedClosureClass.class.getDeclaredConstructor(Object.class, Object.class)

        when:
        def modifiedMetaClasses = instrumentedClasses.instrumentedClosure { Constructor<?> ctor, Object receiver ->
            def closure = ctor.newInstance(new InterceptorTestReceiver(), new NestedClass())
            closure.delegate = receiver
            closure()
            [
                GroovySystem.metaClassRegistry.getMetaClass(InterceptorTestReceiver) instanceof CallInterceptingMetaClass,
                GroovySystem.metaClassRegistry.getMetaClass(NestedClass) instanceof CallInterceptingMetaClass
            ]
        }(constructor, new InterceptorTestReceiver())

        then:
        modifiedMetaClasses.every()
    }

    def 'closure does not erroneously become effectively instrumented after it threw an exception'() {
        given:
        def transformedThrowingClosure = instrumentedClasses.instrumentedClosure {
            throw new RuntimeException()
        }
        transformedThrowingClosure.delegate = new InterceptorTestReceiver()
        def transformedInterceptedClosure = instrumentedClasses.instrumentedClosure {
            test()
        }
        transformedInterceptedClosure.delegate = new FalseInterceptorTestReceiver()

        when: 'a closure throwing an exception is called, then a closure that hits an instrumented call is called'
        try {
            transformedThrowingClosure()
            // this calls throws an exception, but we expect the closure to be still correctly removed from control flow tracking
        } catch (RuntimeException ignored) {}
        transformedInterceptedClosure()

        then: 'the closure that threw an exception should not have become effectively instrumented'
        !(GroovySystem.metaClassRegistry.getMetaClass(InterceptorTestReceiver) instanceof CallInterceptingMetaClass)
    }

    /**
     * Features some APIs similar to {@link InterceptorTestReceiver}, but we don't have interceptors for this class as a receiver.
     */
    private class FalseInterceptorTestReceiver {
        void test() { }
    }

    def 'invoking an intercepted method on a BeanDynamicObject replaces the metaclass of the bean'() {
        when:
        instrumentedClasses.instrumentedClosure { receiver ->
            new BeanDynamicObject(receiver).invokeMethod("test")
        }(new InterceptorTestReceiver())

        then:
        GroovySystem.metaClassRegistry.getMetaClass(InterceptorTestReceiver) instanceof CallInterceptingMetaClass
    }

    def 'interceptors persist after dynamic invocations'() {
        given:
        def receiver = new InterceptorTestReceiver()
        def transformedClosure = instrumentedClasses.instrumentedClosure { test() }
        transformedClosure.delegate = receiver

        when: "the call site has been hit before"
        transformedClosure()
        receiver.intercepted = null

        and: "another call goes through the call site"
        transformedClosure()

        then:
        receiver.intercepted == "test()"

        when: "one more call is done, which may work differently after call site initialization"
        receiver.intercepted = null
        transformedClosure()

        then:
        receiver.intercepted == "test()"
    }
}
