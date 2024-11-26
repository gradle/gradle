/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.tools.api

import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import org.gradle.internal.tools.api.impl.JavaApiMemberWriter
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import javax.tools.DiagnosticCollector
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

class ApiClassExtractorTestSupport extends Specification {

    private static class JavaSourceFromString extends SimpleJavaFileObject {

        private final String code

        JavaSourceFromString(String name, String code) {
            super(URI.create("string:///${ApiClassExtractorTestSupport.toFileName(name)}"), JavaFileObject.Kind.SOURCE)
            this.code = code
        }

        @Override
        CharSequence getCharContent(boolean ignoreEncodingErrors) {
            code
        }
    }

    @CompileStatic
    private static class DefiningClassLoader extends URLClassLoader {

        DefiningClassLoader() {
            super(new URL[0], systemClassLoader.parent)
        }

        Class<?> loadClassFromBytes(byte[] bytes) {
            defineClass(null, bytes, 0, bytes.length)
        }
    }

    @CompileStatic
    static class ClassContainer {
        private final DefiningClassLoader classLoader = new DefiningClassLoader()
        private final ApiClassExtractor apiClassExtractor

        public final Map<String, GeneratedClass> classes

        ClassContainer(List<String> packages, boolean includePackagePrivate, Map<String, GeneratedClass> classes) {
            this.apiClassExtractor = ApiClassExtractor.withWriter(JavaApiMemberWriter.adapter()).with {
                if (!packages.empty) {
                    includePackagesMatching { packages.contains(it) }
                }
                if (includePackagePrivate) {
                    includePackagePrivateMembers()
                }
                build()
            }
            this.classes = classes
        }

        protected Class<?> extractAndLoadApiClassFrom(GeneratedClass clazz) {
            classLoader.loadClassFromBytes(apiClassExtractor.extractApiClassFrom(clazz.bytes).get())
        }

        protected byte[] extractApiClassFrom(GeneratedClass clazz) {
            apiClassExtractor.extractApiClassFrom(clazz.bytes).get()
        }

        protected boolean isApiClassExtractedFrom(GeneratedClass clazz) {
            apiClassExtractor.extractApiClassFrom(clazz.bytes).isPresent()
        }
    }

    @TupleConstructor
    @CompileStatic
    static class GeneratedClass {
        final byte[] bytes
        final Class<?> clazz
    }

    @CompileStatic
    static String toFileName(String name, boolean clazz = false) {
        "${name.replace('.', '/')}.${clazz ? 'class' : 'java'}"
    }

    @Shared
    public JavaCompiler compiler = ToolProvider.systemJavaCompiler

    @TempDir
    File temporaryFolder

    // The default target version can be updated to a new version when necessary, as long as
    // you also update `ApiClassExtractorTest#target binary compatibility is maintained` with new assumptions.
    private static final String DEFAULT_TARGET_VERSION = '8'

    protected ClassContainer toApi(Map<String, String> sources) {
        toApi(DEFAULT_TARGET_VERSION, [], sources)
    }

    protected ClassContainer toApi(String targetVersion, Map<String, String> sources) {
        toApi(targetVersion, [], sources)
    }

    protected ClassContainer toApi(List<String> packages, Map<String, String> sources) {
        toApi(DEFAULT_TARGET_VERSION, packages, sources)
    }

    protected ClassContainer toApi(String targetVersion, List<String> packages, Map<String, String> sources) {
        return compileTo(new File(temporaryFolder, 'api'), targetVersion, packages, sources, [])
    }

    protected ClassContainer compileTo(File dir, Map<String, String> sources, List<File> classpath) {
        return compileTo(dir, DEFAULT_TARGET_VERSION, [], sources, classpath)
    }

    protected ClassContainer compileTo(File dir, String targetVersion, List<String> packages, Map<String, String> sources, List<File> classpath) {
        dir.mkdirs()
        def fileManager = compiler.getStandardFileManager(null, null, null)
        fileManager.setLocation(StandardLocation.CLASS_PATH, classpath);
        def diagnostics = new DiagnosticCollector<JavaFileObject>()
        def task = compiler.getTask(
            null,
            fileManager,
            diagnostics,
            ['-d', dir.absolutePath, '-source', targetVersion, '-target', targetVersion],
            [],
            sources.collect { fqn, src -> new JavaSourceFromString(fqn, src) })
        fileManager.close()
        if (task.call()) {
            def classLoader = new URLClassLoader([dir.toURI().toURL()] as URL[], ClassLoader.systemClassLoader.parent)
            // Load the class from the classloader by name....
            Map<String, GeneratedClass> entries = [:].withDefault { String cn ->
                def f = new File(dir, toFileName(cn, true))
                if (f.exists()) {
                    return new GeneratedClass(f.bytes, classLoader.loadClass(cn))
                }
                throw new AssertionError("Cannot find class $cn. Test is very likely not written correctly.")
            }
            return new ClassContainer(packages, packages.empty, entries)
        }

        StringBuilder sb = new StringBuilder("Error in compilation of test sources:\n")
        diagnostics.diagnostics.each {
            sb.append("In $it\n")
        }

        throw new RuntimeException(sb.toString())
    }

    @CompileStatic
    protected GeneratedClass toClass(String fqn, String script) {
        toApi([(fqn): script]).classes[fqn]
    }

    protected void noSuchMethod(Class c, String name, Class... argTypes) {
        try {
            c.getDeclaredMethod(name, argTypes)
        } catch (NoSuchMethodException ex) {
            return
        }
        throw new AssertionError("Should not have found method $name(${Arrays.toString(argTypes)}) on class $c")
    }

    protected static Method hasMethod(Class c, String name, Class... argTypes) {
        try {
            c.getDeclaredMethod(name, argTypes)
        } catch (NoSuchMethodException ex) {
            throw new AssertionError("Should have found method $name(${Arrays.toString(argTypes)}) on class $c")
        }
    }

    protected static void noSuchField(Class c, String name, Class type) {
        try {
            def f = c.getDeclaredField(name)
            if (f.type != type) {
                throw new AssertionError("Field $name was found on class $c but " +
                    "with a different type: ${f.type} instead of $type")
            }
        } catch (NoSuchFieldException ex) {
            return
        }
        throw new AssertionError("Should not have found field $name of type $type on class $c")
    }

    protected static Field hasField(Class c, String name, Class type) {
        try {
            def f = c.getDeclaredField(name)
            if (f.type != type) {
                throw new AssertionError("Field $name was found on class $c but " +
                    "with a different type: ${f.type} instead of $type")
            }
            return f
        } catch (NoSuchFieldException ex) {
            throw new AssertionError("Should have found field $name on class $c")
        }
    }

    protected static <T> T createInstance(Class<T> c) {
        Constructor<T> constructor = c.getDeclaredConstructor()
        constructor.setAccessible(true)
        return constructor.newInstance()
    }
}
