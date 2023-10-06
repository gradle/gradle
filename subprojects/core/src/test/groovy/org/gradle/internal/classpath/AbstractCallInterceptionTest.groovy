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

import org.gradle.internal.classpath.types.InstrumentingTypeRegistry
import org.gradle.internal.classpath.intercept.GroovyInterceptorsSubstitution
import spock.lang.Specification

import java.util.function.Predicate

abstract class AbstractCallInterceptionTest extends Specification {
    protected abstract Predicate<String> shouldInstrumentAndReloadClassByName()

    protected abstract JvmBytecodeInterceptorSet jvmBytecodeInterceptorSet()

    protected abstract GroovyCallInterceptorsProvider groovyCallInterceptors()

    protected InstrumentingTypeRegistry typeRegistry() {
        InstrumentingTypeRegistry.empty()
    }

    protected InstrumentedClasses instrumentedClasses

    private GroovyInterceptorsSubstitution groovyInterceptorsSubstitution

    def setup() {
        instrumentedClasses = new InstrumentedClasses(
            getClass().classLoader,
            shouldInstrumentAndReloadClassByName(),
            jvmBytecodeInterceptorSet(),
            typeRegistry()
        )
        groovyInterceptorsSubstitution = new GroovyInterceptorsSubstitution(groovyCallInterceptors())
        groovyInterceptorsSubstitution.setupForCurrentThread()
    }

    def cleanup() {
        groovyInterceptorsSubstitution.cleanupForCurrentThread()
    }
}
