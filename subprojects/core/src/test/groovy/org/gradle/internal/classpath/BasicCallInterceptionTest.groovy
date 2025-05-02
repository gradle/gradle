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
import static org.gradle.internal.classpath.JavaCallerForBasicCallInterceptorTest.doTestNoArg
import static org.gradle.internal.classpath.JavaCallerForBasicCallInterceptorTest.doTestSingleArg
import static org.gradle.internal.classpath.JavaCallerForBasicCallInterceptorTest.doTestSingleArgNull
import static org.gradle.internal.classpath.JavaCallerForBasicCallInterceptorTest.doTestVararg
import static org.gradle.internal.classpath.JavaCallerForBasicCallInterceptorTest.doTestVarargWithArray
import static org.gradle.internal.classpath.JavaCallerForBasicCallInterceptorTest.doTestVarargWithNullItem
import static org.gradle.internal.classpath.intercept.JvmBytecodeInterceptorFactoryProvider.ClassLoaderSourceJvmBytecodeInterceptorFactoryProvider
import static org.gradle.internal.classpath.intercept.JvmBytecodeInterceptorFactoryProvider.DEFAULT

class BasicCallInterceptionTest extends AbstractCallInterceptionTest {
    @Override
    protected Predicate<String> shouldInstrumentAndReloadClassByName() {
        nestedClassesOf(BasicCallInterceptionTest) | isEqual(JavaCallerForBasicCallInterceptorTest.class.name)
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
    def originalMetaClass = null

    def setup() {
        interceptorTestReceiverClassLock.lock()
        originalMetaClass = InterceptorTestReceiver.metaClass
    }

    def cleanup() {
        InterceptorTestReceiver.metaClass = originalMetaClass
        interceptorTestReceiverClassLock.unlock()
    }

    String interceptedWhen(
        boolean shouldDelegate,
        @ClosureParams(value = SimpleType, options = "InterceptorTestReceiver") Closure<?> call
    ) {
        interceptedWhen(new InterceptorTestReceiver(), shouldDelegate, call)
    }

    String interceptedWhen(
        InterceptorTestReceiver receiver,
        boolean shouldDelegate,
        @ClosureParams(value = SimpleType, options = "InterceptorTestReceiver") Closure<?> call
    ) {
        def closure = instrumentedClasses.instrumentedClosure(call)
        if (shouldDelegate) {
            closure.delegate = receiver
            closure.call()
        } else {
            closure.call(receiver)
        }
        receiver.intercepted
    }

    def 'intercepts a basic instance call with #method from #caller'() {
        when:
        def intercepted = interceptedWhen(shouldDelegate, invocation)

        then:
        intercepted == expected

        where:
        method                  | caller                    | invocation                                | shouldDelegate | expected
        "no argument"           | "Java"                    | { doTestNoArg(it) }                       | false          | "test()"
        "one argument"          | "Java"                    | { doTestSingleArg(it) }                   | false          | "test(InterceptorTestReceiver)"
        "one null argument"     | "Java"                    | { doTestSingleArgNull(it) }               | false          | "test(InterceptorTestReceiver)"
        "vararg"                | "Java"                    | { doTestVararg(it) }                      | false          | "testVararg(Object...)"
        "vararg with array"     | "Java"                    | { doTestVarargWithArray(it) }             | false          | "testVararg(Object...)"
        "vararg with null item" | "Java"                    | { doTestVarargWithNullItem(it) }          | false          | "testVararg(Object...)"

        "no argument"           | "Groovy callsite"         | { it.test() }                             | false          | "test()"
        "one argument"          | "Groovy callsite"         | { it.test(it) }                           | false          | "test(InterceptorTestReceiver)"
        "one null argument"     | "Groovy callsite"         | { it.test(null) }                         | false          | "test(InterceptorTestReceiver)"
        "vararg"                | "Groovy callsite"         | { it.testVararg(it, it, it) }             | false          | "testVararg(Object...)"
        "vararg with array"     | "Groovy callsite"         | { it.testVararg([it, it, it].toArray()) } | false          | "testVararg(Object...)"
        "vararg with null item" | "Groovy callsite"         | { it.testVararg(null) }                   | false          | "testVararg(Object...)"
        "non-existent-method"   | "Groovy callsite"         | { it.nonExistent(null) }                  | false          | "nonExistent(String)-non-existent"

        "no argument"           | "Groovy dynamic dispatch" | { test() }                                | true           | "test()"
        "one argument"          | "Groovy dynamic dispatch" | { test(it) }                              | true           | "test(InterceptorTestReceiver)"
        "one null argument"     | "Groovy dynamic dispatch" | { test(null) }                            | true           | "test(InterceptorTestReceiver)"
        "vararg"                | "Groovy dynamic dispatch" | { testVararg(it, it, it) }                | true           | "testVararg(Object...)"
        "vararg with array"     | "Groovy dynamic dispatch" | { testVararg([it, it, it].toArray()) }    | true           | "testVararg(Object...)"
        "vararg with null item" | "Groovy dynamic dispatch" | { testVararg(null) }                      | true           | "testVararg(Object...)"
        "non-existent-method"   | "Groovy dynamic dispatch" | { nonExistent(null) }                     | true          | "nonExistent(String)-non-existent"
    }

    def 'access to a #kind of a Groovy property from a #caller caller is intercepted as #expected'() {
        when:
        def intercepted = interceptedWhen(shouldDelegate, invocation)

        then:
        intercepted == expected

        where:
        kind                  | caller      | expected                                      | invocation                           | shouldDelegate
        "normal getter"       | "call site" | "getTestString()"                             | { it.testString }                    | false
        "boolean getter"      | "call site" | "isTestFlag()"                                | { it.testFlag }                      | false
        "non-existent getter" | "call site" | "getNonExistentProperty()-non-existent"       | { it.nonExistentProperty }           | false

        "normal getter"       | "dynamic"   | "getTestString()"                             | { testString }                       | true
        "boolean getter"      | "dynamic"   | "isTestFlag()"                                | { testFlag }                         | true
        "non-existent getter" | "dynamic"   | "getNonExistentProperty()-non-existent"       | { nonExistentProperty }              | true

        "normal setter"       | "call site" | "setTestString(String)"                       | { it.testString = "value" }          | false
        "boolean setter"      | "call site" | "setTestFlag(boolean)"                        | { it.testFlag = true }               | false
        "non-existent setter" | "call site" | "setNonExistentProperty(String)-non-existent" | { it.nonExistentProperty = "value" } | false

        "normal setter"       | "dynamic"   | "setTestString(String)"                       | { testString = "value" }             | true
        "boolean setter"      | "dynamic"   | "setTestFlag(boolean)"                        | { testFlag = true }                  | true
        "non-existent setter" | "dynamic"   | "setNonExistentProperty(String)-non-existent" | { nonExistentProperty = "value" }    | true
    }
}
