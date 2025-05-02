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
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter

import java.util.function.Predicate

import static java.util.function.Predicate.isEqual
import static org.gradle.internal.classpath.BasicCallInterceptionTestInterceptorsDeclaration.TEST_GENERATED_CLASSES_PACKAGE
import static org.gradle.internal.classpath.GroovyCallInterceptorsProvider.ClassLoaderSourceGroovyCallInterceptorsProvider
import static org.gradle.internal.classpath.InstrumentedClasses.nestedClassesOf
import static org.gradle.internal.classpath.JavaCallerForCallInterceptionFilteringTest.doTestBytecodeUpgrade
import static org.gradle.internal.classpath.JavaCallerForCallInterceptionFilteringTest.doTestInstrumentation
import static org.gradle.internal.classpath.intercept.JvmBytecodeInterceptorFactoryProvider.ClassLoaderSourceJvmBytecodeInterceptorFactoryProvider
import static org.gradle.internal.classpath.intercept.JvmBytecodeInterceptorFactoryProvider.DEFAULT

class CallInterceptionFilteringTest extends AbstractCallInterceptionTest {
    @Override
    protected Predicate<String> shouldInstrumentAndReloadClassByName() {
        nestedClassesOf(CallInterceptionFilteringTest) | isEqual(JavaCallerForCallInterceptionFilteringTest.class.name)
    }

    @Override
    protected JvmBytecodeInterceptorFactoryProvider jvmBytecodeInterceptorSet() {
        return DEFAULT + new ClassLoaderSourceJvmBytecodeInterceptorFactoryProvider(this.class.classLoader, TEST_GENERATED_CLASSES_PACKAGE)
    }

    @Override
    protected GroovyCallInterceptorsProvider groovyCallInterceptors() {
        return new ClassLoaderSourceGroovyCallInterceptorsProvider(this.getClass().classLoader, TEST_GENERATED_CLASSES_PACKAGE)
    }

    def originalMetaClass = null

    def setup() {
        originalMetaClass = CallInterceptionFilteringTestReceiver.metaClass
    }

    def cleanup() {
        CallInterceptionFilteringTestReceiver.metaClass = originalMetaClass
    }

    String interceptedWhen(
        boolean shouldDelegate,
        @ClosureParams(value = SimpleType, options = "CallInterceptionFilteringTestReceiver") Closure<?> call
    ) {
        def receiver = new CallInterceptionFilteringTestReceiver()
        def closure = instrumentedClasses.instrumentedClosure(call)
        if (shouldDelegate) {
            closure.delegate = receiver
            closure.call()
        } else {
            closure.call(receiver)
        }
        receiver.intercepted
    }

    def 'intercepts a basic instance call with #method from #caller with `instrumentation only` filter'() {
        given:
        bytecodeInterceptorFilter = BytecodeInterceptorFilter.INSTRUMENTATION_ONLY
        resetInterceptors()

        when:
        def intercepted = interceptedWhen(shouldDelegate, invocation)

        then:
        intercepted == expected

        where:
        method             | caller                     | invocation                    | shouldDelegate | expected
        "instrumentation"  | "Java"                     | { doTestInstrumentation(it) } | false          | "testInstrumentation()"
        "bytecode upgrade" | "Java"                     | { doTestBytecodeUpgrade(it) } | false          | null

        "instrumentation"  | "Groovy callsite"          | { it.testInstrumentation() }  | false          | "testInstrumentation()"
        "bytecode upgrade" | "Groovy callsite"          | { it.testBytecodeUpgrade() }  | false          | null

        "instrumentation " | "Groovy dynamic dispatch " | { testInstrumentation() }     | true           | "testInstrumentation()"
        "bytecode upgrade" | "Groovy dynamic dispatch"  | { testBytecodeUpgrade() }     | true           | null
    }

    def 'intercepts a basic instance call with #method from #caller with `all` filter'() {
        given:
        bytecodeInterceptorFilter = BytecodeInterceptorFilter.ALL
        resetInterceptors()

        when:
        def intercepted = interceptedWhen(shouldDelegate, invocation)

        then:
        intercepted == expected

        where:
        method             | caller                     | invocation                    | shouldDelegate | expected
        "instrumentation"  | "Java"                     | { doTestInstrumentation(it) } | false          | "testInstrumentation()"
        "bytecode upgrade" | "Java"                     | { doTestBytecodeUpgrade(it) } | false          | "testBytecodeUpgrade()"

        "instrumentation"  | "Groovy callsite"          | { it.testInstrumentation() }  | false          | "testInstrumentation()"
        "bytecode upgrade" | "Groovy callsite"          | { it.testBytecodeUpgrade() }  | false          | "testBytecodeUpgrade()"

        "instrumentation " | "Groovy dynamic dispatch " | { testInstrumentation() }     | true           | "testInstrumentation()"
        "bytecode upgrade" | "Groovy dynamic dispatch"  | { testBytecodeUpgrade() }     | true           | "testBytecodeUpgrade()"
    }
}
