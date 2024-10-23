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


import org.gradle.internal.classpath.transforms.InstrumentingClassTransform
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter

import java.util.function.Predicate

class InstrumentedClasses {

    private final Predicate<String> shouldInstrumentClassByName

    private final TestInstrumentedClassLoader loader

    InstrumentedClasses(
            ClassLoader source,
            Predicate<String> shouldInstrumentClassByName,
            BytecodeInterceptorFilter interceptorFilter,
            InstrumentationTypeRegistry typeRegistry
    ) {
        this.shouldInstrumentClassByName = shouldInstrumentClassByName
        loader = new TestInstrumentedClassLoader(
            source,
            shouldInstrumentClassByName,
            new InstrumentingClassTransform(interceptorFilter, typeRegistry)
        )
    }

    static Predicate<String> nestedClassesOf(Class<?> theClass) {
        return { className -> className.startsWith(theClass.name + "\$")}
    }

    Class<?> instrumentedClass(Class<?> originalClass) {
        if (!shouldInstrumentClassByName.test(originalClass.name)) {
            throw new IllegalArgumentException(originalClass.name + " is not instrumented")
        }
        loader.loadClass(originalClass.name)
    }

    Closure<?> instrumentedClosure(Closure<?> originalClosure) {
        def capturedParams = originalClosure.class.declaredConstructors[0].parameters.drop(2)
        if (capturedParams.size() != 0) {
            // TODO support captured args in some way?
            throw new IllegalArgumentException("closures with captured arguments are not supported yet; please use the arguments and return value")
        }
        instrumentedClass(originalClosure.class).getDeclaredConstructor(Object, Object).newInstance(originalClosure.thisObject, originalClosure.owner) as Closure<?>
    }
}
