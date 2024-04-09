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

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.internal.classpath.intercept.JvmBytecodeInterceptorFactoryProvider

import java.util.function.Predicate

import static java.util.function.Predicate.isEqual
import static org.gradle.internal.classpath.BasicCallInterceptionTestInterceptorsDeclaration.TEST_GENERATED_CLASSES_PACKAGE
import static org.gradle.internal.classpath.GroovyCallInterceptorsProvider.ClassLoaderSourceGroovyCallInterceptorsProvider
import static org.gradle.internal.classpath.InstrumentedClasses.nestedClassesOf
import static org.gradle.internal.classpath.intercept.JvmBytecodeInterceptorFactoryProvider.ClassLoaderSourceJvmBytecodeInterceptorFactoryProvider
import static org.gradle.internal.classpath.intercept.JvmBytecodeInterceptorFactoryProvider.DEFAULT

class CompositeCallInterceptionTest extends AbstractCallInterceptionTest {
    @Override
    protected Predicate<String> shouldInstrumentAndReloadClassByName() {
        nestedClassesOf(CompositeCallInterceptionTest)
    }

    @Override
    protected JvmBytecodeInterceptorFactoryProvider jvmBytecodeInterceptorSet() {
        return DEFAULT + new ClassLoaderSourceJvmBytecodeInterceptorFactoryProvider(this.class.classLoader, TEST_GENERATED_CLASSES_PACKAGE)
    }

    @Override
    protected GroovyCallInterceptorsProvider groovyCallInterceptors() {
        return new ClassLoaderSourceGroovyCallInterceptorsProvider(this.getClass().classLoader, TEST_GENERATED_CLASSES_PACKAGE)
    }

    // We don't want to interfere with other tests that modify the meta class
    def interceptorTestReceiverClassLock = new ClassBasedLock(InterceptorTestReceiver)
    def interceptorTestReceiverOriginalMetaClass = null
    def compositeInterceptorTestReceiverClassLock = new ClassBasedLock(CompositeInterceptorTestReceiver)
    def compositeInterceptorTestReceiverOriginalMetaClass = null

    def setup() {
        interceptorTestReceiverClassLock.lock()
        interceptorTestReceiverOriginalMetaClass = InterceptorTestReceiver.metaClass
        compositeInterceptorTestReceiverClassLock.lock()
        compositeInterceptorTestReceiverOriginalMetaClass = CompositeInterceptorTestReceiver.metaClass
    }

    def cleanup() {
        InterceptorTestReceiver.metaClass = interceptorTestReceiverOriginalMetaClass
        interceptorTestReceiverClassLock.unlock()
        CompositeInterceptorTestReceiver.metaClass = compositeInterceptorTestReceiverOriginalMetaClass
        compositeInterceptorTestReceiverClassLock.unlock()
    }

    String interceptedInterceptorTestReceiver(
        boolean shouldDelegate,
        @ClosureParams(value = SimpleType, options = "InterceptorTestReceiver") Closure<?> call
    ) {
        def receiver = new InterceptorTestReceiver()
        def closure = instrumentedClasses.instrumentedClosure(call)
        if (shouldDelegate) {
            closure.delegate = receiver
            closure.call()
        } else {
            closure.call(receiver)
        }
        receiver.intercepted
    }

    String interceptedCompositeInterceptorTestReceiver(
        boolean shouldDelegate,
        @ClosureParams(value = SimpleType, options = "CompositeInterceptorTestReceiver") Closure<?> call
    ) {
        def receiver = new CompositeInterceptorTestReceiver()
        def closure = instrumentedClasses.instrumentedClosure(call)
        if (shouldDelegate) {
            closure.delegate = receiver
            closure.call()
        } else {
            closure.call(receiver)
        }
        receiver.intercepted
    }

    def 'intercepts a basic instance call with #method from #caller for multiple types with the same method names'() {
        when:
        def firstIntercepted = interceptedInterceptorTestReceiver(shouldDelegate, invocation)
        def secondIntercepted = interceptedCompositeInterceptorTestReceiver(shouldDelegate, invocation)

        then:
        firstIntercepted == expected
        secondIntercepted == "composite.$expected"

        where:
        method         | caller                    | invocation      | shouldDelegate | expected
        "no argument"  | "Groovy callsite"         | { it.test() }   | false          | "test()"
        "one argument" | "Groovy callsite"         | { it.test(it) } | false          | "test(InterceptorTestReceiver)"

        "no argument"  | "Groovy dynamic dispatch" | { test() }      | true           | "test()"
        "one argument" | "Groovy dynamic dispatch" | { test(it) }    | true           | "test(InterceptorTestReceiver)"
    }
}
