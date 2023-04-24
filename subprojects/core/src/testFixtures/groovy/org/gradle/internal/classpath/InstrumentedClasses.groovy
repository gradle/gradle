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

import org.gradle.api.file.RelativePath
import org.gradle.internal.classloader.TransformingClassLoader
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter

import java.util.function.Predicate

class InstrumentedClasses {

    private final Predicate<String> shouldInstrumentClassByName

    private final InstrumentedClassLoader loader

    InstrumentedClasses(
        ClassLoader source,
        Predicate<String> shouldInstrumentClassByName,
        JvmBytecodeInterceptorSet interceptors
    ) {
        this.shouldInstrumentClassByName = shouldInstrumentClassByName
        loader = new InstrumentedClassLoader(
            source,
            shouldInstrumentClassByName,
            new InstrumentingTransformer(interceptors)
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
            throw new IllegalArgumentException("closures with captured arguments are not supported yet")
        }
        instrumentedClass(originalClosure.class).getDeclaredConstructor(Object, Object).newInstance(originalClosure.thisObject, originalClosure.owner) as Closure<?>
    }

    private static class InstrumentedClassLoader extends TransformingClassLoader {
        private final CachedClasspathTransformer.Transform transform
        private final Predicate<String> shouldLoadTransformedClass
        private final ClassLoader source

        InstrumentedClassLoader(
            ClassLoader source,
            Predicate<String> shouldLoadTransformedClass,
            CachedClasspathTransformer.Transform transform
        ) {
            super("test-transformed-loader", source, [])
            this.shouldLoadTransformedClass = shouldLoadTransformedClass
            this.transform = transform
            this.source = source
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (shouldLoadTransformedClass.test(name)) {
                def result = findLoadedClass(name)
                if (result == null) {
                    result = findClass(name)
                }
                if (resolve) {
                    resolveClass(result)
                }
                return result
            }
            return super.loadClass(name, resolve)
        }

        @Override
        URL findResource(String name) {
            source.findResource(name)
        }

        @Override
        protected byte[] transform(String className, byte[] bytes) {
            def path = name.replace(".", "/") + ".class"
            ClasspathEntryVisitor.Entry classEntry = new ClasspathEntryVisitor.Entry() {
                @Override
                String getName() { name }

                @Override
                RelativePath getPath() { RelativePath.parse(true, path) }

                @Override
                ClasspathEntryVisitor.Entry.CompressionMethod getCompressionMethod() { ClasspathEntryVisitor.Entry.CompressionMethod.STORED }

                @Override
                byte[] getContent() { bytes }
            }
            ClassReader originalReader = new ClassReader(bytes)
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS)
            def pathAndVisitor = transform.apply(classEntry, writer, new ClassData(originalReader))
            originalReader.accept(pathAndVisitor.right(), ClassReader.EXPAND_FRAMES)

            return writer.toByteArray()
        }
    }
}
