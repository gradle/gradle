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

import org.gradle.internal.classpath.intercept.GroovyInterceptorsSubstitution
import org.gradle.internal.classpath.intercept.JvmBytecodeInterceptorFactoryProvider
import org.gradle.internal.classpath.intercept.JvmInterceptorsSubstitution
import org.gradle.internal.classpath.types.InstrumentingTypeRegistry
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter
import spock.lang.Specification

import java.util.function.Predicate

abstract class AbstractCallInterceptionTest extends Specification {
    protected BytecodeInterceptorFilter bytecodeInterceptorFilter = BytecodeInterceptorFilter.ALL

    protected abstract Predicate<String> shouldInstrumentAndReloadClassByName()

    protected abstract JvmBytecodeInterceptorFactoryProvider jvmBytecodeInterceptorSet()

    protected abstract GroovyCallInterceptorsProvider groovyCallInterceptors()

    protected InstrumentingTypeRegistry typeRegistry() {
        InstrumentingTypeRegistry.empty()
    }

    protected InstrumentedClasses instrumentedClasses

    private JvmInterceptorsSubstitution jvmInterceptorsSubstitution
    private GroovyInterceptorsSubstitution groovyInterceptorsSubstitution

    def setup() {
        // Substitutions should be set before the InstrumentedClasses is constructed
        jvmInterceptorsSubstitution = new JvmInterceptorsSubstitution(jvmBytecodeInterceptorSet())
        jvmInterceptorsSubstitution.setupForCurrentThread()
        groovyInterceptorsSubstitution = new GroovyInterceptorsSubstitution(groovyCallInterceptors())
        groovyInterceptorsSubstitution.setupForCurrentThread()
        instrumentedClasses = new InstrumentedClasses(
            getClass().classLoader,
            shouldInstrumentAndReloadClassByName(),
            bytecodeInterceptorFilter,
            typeRegistry()
        )
    }

    def cleanup() {
        jvmInterceptorsSubstitution.cleanupForCurrentThread()
        groovyInterceptorsSubstitution.cleanupForCurrentThread()
    }

    def resetInterceptors() {
        cleanup()
        setup()
    }
}
