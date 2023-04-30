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

class BasicCallInterceptionTest extends AbstractCallInterceptionTest {
    @Override
    protected Predicate<String> shouldInstrumentAndReloadClassByName() {
        nestedClassesOf(BasicCallInterceptionTest) | isEqual(JavaCallerForBasicCallInterceptorTest.class.name)
    }

    @Override
    protected JvmBytecodeInterceptorSet jvmBytecodeInterceptorSet() {
        return { [BasicCallInterceptionTestInterceptorsDeclaration.JVM_BYTECODE_GENERATED_CLASS] }
    }

    @Override
    protected GroovyCallInterceptorsProvider groovyCallInterceptors() {
        return { [BasicCallInterceptionTestInterceptorsDeclaration.GROOVY_GENERATED_CLASS] }
    }

    String interceptedWhen(@ClosureParams(value = SimpleType, options = "InterceptorTestReceiver") Closure<?> call) {
        def receiver = new InterceptorTestReceiver()
        instrumentedClasses.instrumentedClosure(call).call(receiver)
        receiver.intercepted
    }

    def 'intercepts a basic instance call with $name'() {
        when:
        def intercepted = interceptedWhen(invocation)

        then:
        intercepted == expected

        where:
        // TODO: the set of the test cases should be extended; the ones listed currently are an example
        name                       | invocation                                                    | expected
        "no argument from Java"    | { JavaCallerForBasicCallInterceptorTest.doCallNoArg(it) }     | "call()"
        "one argument from Java"   | { JavaCallerForBasicCallInterceptorTest.doCallSingleArg(it) } | "call(InterceptorTestReceiver)"
        "vararg from Java"         | { JavaCallerForBasicCallInterceptorTest.doCallVararg(it) }    | "callVararg(Object...)"

        "no argument from Groovy"  | { it.call() }                                                 | "call()"
        "one argument from Groovy" | { it.call(it) }                                               | "call(InterceptorTestReceiver)"
        "vararg from Groovy"       | { it.callVararg(it, it, it) }                                 | "callVararg(Object...)"
    }
}
