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

import java.util.function.Predicate

import static java.util.function.Predicate.isEqual
import static org.gradle.internal.classpath.InstrumentedClasses.nestedClassesOf
import static org.gradle.internal.classpath.JavaCallerForBasicCallInterceptorTest.*

class BasicCallInterceptionTest extends AbstractCallInterceptionTest {
    @Override
    protected Predicate<String> shouldInstrumentAndReloadClassByName() {
        nestedClassesOf(BasicCallInterceptionTest) | isEqual(JavaCallerForBasicCallInterceptorTest.class.name)
    }

    @Override
    protected JvmBytecodeInterceptorSet jvmBytecodeInterceptorSet() {
        return JvmBytecodeInterceptorSet.DEFAULT + { [BasicCallInterceptionTestInterceptorsDeclaration.JVM_BYTECODE_GENERATED_CLASS] }
    }

    @Override
    protected GroovyCallInterceptorsProvider groovyCallInterceptors() {
        return { [BasicCallInterceptionTestInterceptorsDeclaration.GROOVY_GENERATED_CLASS] }
    }

    String interceptedWhen(
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

        "no argument"           | "Groovy dynamic dispatch" | { test() }                                | true           | "test()"
        "one argument"          | "Groovy dynamic dispatch" | { test(it) }                              | true           | "test(InterceptorTestReceiver)"
        "one null argument"     | "Groovy dynamic dispatch" | { test(null) }                            | true           | "test(InterceptorTestReceiver)"
        "vararg"                | "Groovy dynamic dispatch" | { testVararg(it, it, it) }                | true           | "testVararg(Object...)"
        "vararg with array"     | "Groovy dynamic dispatch" | { testVararg([it, it, it].toArray()) }    | true           | "testVararg(Object...)"
        "vararg with null item" | "Groovy dynamic dispatch" | { testVararg(null) }                      | true           | "testVararg(Object...)"
    }
}
