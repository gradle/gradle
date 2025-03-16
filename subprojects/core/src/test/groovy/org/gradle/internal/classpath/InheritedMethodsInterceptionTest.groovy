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

import org.gradle.internal.classpath.intercept.JvmBytecodeInterceptorFactoryProvider
import org.gradle.internal.classpath.types.ExternalPluginsInstrumentationTypeRegistry
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry

import java.util.function.Predicate

import static java.util.function.Predicate.isEqual
import static org.gradle.internal.classpath.BasicCallInterceptionTestInterceptorsDeclaration.TEST_GENERATED_CLASSES_PACKAGE
import static org.gradle.internal.classpath.GroovyCallInterceptorsProvider.ClassLoaderSourceGroovyCallInterceptorsProvider
import static org.gradle.internal.classpath.InstrumentedClasses.nestedClassesOf
import static org.gradle.internal.classpath.intercept.JvmBytecodeInterceptorFactoryProvider.*

/**
 * See {@link BasicCallInterceptionTest} for a basic example
 */
class InheritedMethodsInterceptionTest extends AbstractCallInterceptionTest {
    @Override
    protected Predicate<String> shouldInstrumentAndReloadClassByName() {
        nestedClassesOf(InheritedMethodsInterceptionTest.class) | nestedClassesOf(InheritedMethodTestReceiver.class) | isEqual(JavaCallerForBasicCallInterceptorTest.class.name)
    }

    @Override
    protected JvmBytecodeInterceptorFactoryProvider jvmBytecodeInterceptorSet() {
        return new ClassLoaderSourceJvmBytecodeInterceptorFactoryProvider(this.class.classLoader, TEST_GENERATED_CLASSES_PACKAGE)
    }

    @Override
    protected GroovyCallInterceptorsProvider groovyCallInterceptors() {
        return new ClassLoaderSourceGroovyCallInterceptorsProvider(this.getClass().classLoader, TEST_GENERATED_CLASSES_PACKAGE)
    }

    @Override
    protected InstrumentationTypeRegistry typeRegistry() {
        return new ExternalPluginsInstrumentationTypeRegistry([
            "org/gradle/internal/classpath/InheritedMethodTestReceiver\$A": ["org/gradle/internal/classpath/InheritedMethodTestReceiver"] as Set,
            "org/gradle/internal/classpath/InheritedMethodTestReceiver\$B": ["org/gradle/internal/classpath/InheritedMethodTestReceiver\$A"] as Set
        ], InstrumentationTypeRegistry.empty())
    }

    String interceptedFor(InheritedMethodTestReceiver receiver) {
        def call = { JavaCallerForBasicCallInterceptorTest.doCallSayHello(it) }
        return instrumentedClasses.instrumentedClosure(call).call(receiver)
    }

    def 'intercepts inherited method for #description'() {
        when:
        def intercepted = interceptedFor(interceptionReceiver)

        then:
        intercepted == expected

        where:
        description                       | interceptionReceiver                                               | expected
        "base class"                      | new InheritedMethodTestReceiver()                                  | "Hello from: InheritedMethodTestReceiver"
        "direct subclass"                 | new InheritedMethodTestReceiver.A()                                | "Hello from: InheritedMethodTestReceiver\$A"
        "transient subclass"              | new InheritedMethodTestReceiver.B()                                | "Hello from: InheritedMethodTestReceiver\$B"
        "down-casted direct subclass"     | new InheritedMethodTestReceiver.A() as InheritedMethodTestReceiver | "Hello from: InheritedMethodTestReceiver\$A"
        "down-casted transitive subclass" | new InheritedMethodTestReceiver.B() as InheritedMethodTestReceiver | "Hello from: InheritedMethodTestReceiver\$B"
    }
}
