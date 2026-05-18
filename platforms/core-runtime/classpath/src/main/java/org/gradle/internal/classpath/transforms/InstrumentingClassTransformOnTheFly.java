/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.classpath.transforms;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.classpath.OnTheFlyClassTransform;
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;

import java.io.IOException;

/**
 * Runs {@link InstrumentingClassTransform} at class-load time against bytes supplied
 * by the JVM, so that Gradle's instrumentation composes with any third-party
 * {@link java.lang.instrument.ClassFileTransformer} that ran earlier.
 */
public final class InstrumentingClassTransformOnTheFly implements OnTheFlyClassTransform {

    private final ClassTransform inner;

    public InstrumentingClassTransformOnTheFly(BytecodeInterceptorFilter filter, InstrumentationTypeRegistry typeRegistry) {
        this.inner = new InstrumentingClassTransform(filter, typeRegistry);
    }

    @Override
    public byte[] transform(String className, byte[] classfileBuffer) {
        try {
            return ClassTransforms.apply(inner, SyntheticClassEntry.of(className, classfileBuffer)).right;
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
