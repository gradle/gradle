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
        GroovyCallInterceptorsProvider.DEFAULT + { [BasicCallInterceptionTestInterceptorsDeclaration.GROOVY_GENERATED_CLASS] }
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

    def 'invoking an intercepted method on a BeanDynamicObject replaces the metaclass of the bean'() {
        when:
        instrumentedClasses.instrumentedClosure { receiver ->
            new BeanDynamicObject(receiver).invokeMethod("test")
        }(new InterceptorTestReceiver())

        then:
        GroovySystem.metaClassRegistry.getMetaClass(InterceptorTestReceiver) instanceof CallInterceptingMetaClass
    }
}
