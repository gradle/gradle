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

package org.gradle.internal.properties.annotations

import org.gradle.model.internal.asm.AsmConstants
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import spock.lang.Specification

class ClassSourceLocationResolverTest extends Specification {

    def resolver = new ClassSourceLocationResolver()

    static class SimpleClass {
        String getFoo() {
            return "foo"
        }
    }

    static class MultiPropertyClass {
        String getFirstProperty() {
            return "first"
        }

        int getSecondProperty() {
            return 2
        }
    }

    static abstract class AbstractGetterClass {
        abstract String getAbstractProperty()
    }

    static class BaseClass {
        String getInheritedProperty() {
            return "base"
        }
    }

    static class ChildClass extends BaseClass {
    }

    def "extracts source filename from class"() {
        when:
        def location = resolver.resolve(SimpleClass)

        then:
        location.sourceFilename == "ClassSourceLocationResolverTest.groovy"
    }

    def "extracts getter line number"() {
        given:
        def method = SimpleClass.getMethod("getFoo")

        when:
        def location = resolver.resolve(method)

        then:
        location.getLineNumber(method) != null
        location.getLineNumber(method) > 0
    }

    def "resolves multiple properties in the same class"() {
        given:
        def firstMethod = MultiPropertyClass.getMethod("getFirstProperty")
        def secondMethod = MultiPropertyClass.getMethod("getSecondProperty")

        when:
        def location = resolver.resolve(MultiPropertyClass)

        then:
        def firstLine = location.getLineNumber(firstMethod)
        def secondLine = location.getLineNumber(secondMethod)

        firstLine != null
        secondLine != null
        firstLine != secondLine
    }

    def "handles inherited getters by resolving the declaring class"() {
        given:
        def inheritedMethod = ChildClass.getMethod("getInheritedProperty")

        when:
        def location = resolver.resolve(inheritedMethod)

        then:
        inheritedMethod.declaringClass == BaseClass
        location.sourceFilename == "ClassSourceLocationResolverTest.groovy"
        location.getLineNumber(inheritedMethod) != null
    }

    def "handles abstract getters without line information gracefully"() {
        given:
        def method = AbstractGetterClass.getMethod("getAbstractProperty")

        when:
        def location = resolver.resolve(method)

        then:
        location.sourceFilename == "ClassSourceLocationResolverTest.groovy"
        location.getLineNumber(method) == null
    }

    def "matches methods using JVM descriptors"() {
        given:
        def byteCode = createBytecodeWithOverloads()
        def loader = new ByteArrayClassLoader(getClass().classLoader, "com.example.Overloaded", byteCode)
        def clazz = loader.loadClass("com.example.Overloaded")

        when:
        def location = resolver.resolve(clazz)

        then:
        location.sourceFilename == "Overloaded.java"
        location.getLineNumber("getValue", "()Ljava/lang/String;") == 10
        location.getLineNumber("getValue", "()I") == 20
        location.getLineNumber("nonExistent", "()V") == null
    }

    def "handles missing source information gracefully"() {
        given:
        def byteCode = createBytecodeWithoutSource()
        def loader = new ByteArrayClassLoader(getClass().classLoader, "com.example.NoSource", byteCode)
        def clazz = loader.loadClass("com.example.NoSource")

        when:
        def location = resolver.resolve(clazz)

        then:
        location.sourceFilename == null
        location.getLineNumber("getFoo", "()Ljava/lang/String;") == 30
    }

    def "handles missing class resources gracefully"() {
        given:
        def byteCode = createBytecodeWithoutSource()
        def loader = new ByteArrayClassLoader(getClass().classLoader, "com.example.NoSource", byteCode) {
            @Override
            InputStream getResourceAsStream(String name) {
                return null
            }
        }
        def clazz = loader.loadClass("com.example.NoSource")

        when:
        def location = resolver.resolve(clazz)

        then:
        location == ClassSourceLocation.UNKNOWN
        location.sourceFilename == null
        location.getLineNumber("getFoo", "()Ljava/lang/String;") == null
    }

    def "caches resolved metadata for the same class"() {
        when:
        def location1 = resolver.resolve(SimpleClass)
        def location2 = resolver.resolve(SimpleClass)

        then:
        location1.is(location2)
    }

    def "handles null class or method gracefully"() {
        expect:
        resolver.resolve((Class<?>) null) == ClassSourceLocation.UNKNOWN
        resolver.resolve((java.lang.reflect.Method) null) == ClassSourceLocation.UNKNOWN
    }

    private static byte[] createBytecodeWithOverloads() {
        ClassWriter cw = new ClassWriter(0)
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "com/example/Overloaded", null, "java/lang/Object", null)
        cw.visitSource("Overloaded.java", null)

        // Method 1: String getValue()
        def mv1 = cw.visitMethod(Opcodes.ACC_PUBLIC, "getValue", "()Ljava/lang/String;", null, null)
        mv1.visitCode()
        Label l1 = new Label()
        mv1.visitLabel(l1)
        mv1.visitLineNumber(10, l1)
        mv1.visitInsn(Opcodes.ACONST_NULL)
        mv1.visitInsn(Opcodes.ARETURN)
        mv1.visitMaxs(1, 1)
        mv1.visitEnd()

        // Method 2: int getValue()
        def mv2 = cw.visitMethod(Opcodes.ACC_PUBLIC, "getValue", "()I", null, null)
        mv2.visitCode()
        Label l2 = new Label()
        mv2.visitLabel(l2)
        mv2.visitLineNumber(20, l2)
        mv2.visitInsn(Opcodes.ICONST_0)
        mv2.visitInsn(Opcodes.IRETURN)
        mv2.visitMaxs(1, 1)
        mv2.visitEnd()

        cw.visitEnd()
        return cw.toByteArray()
    }

    private static byte[] createBytecodeWithoutSource() {
        ClassWriter cw = new ClassWriter(0)
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "com/example/NoSource", null, "java/lang/Object", null)
        // Note: visitSource is intentionally omitted

        def mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getFoo", "()Ljava/lang/String;", null, null)
        mv.visitCode()
        Label l = new Label()
        mv.visitLabel(l)
        mv.visitLineNumber(30, l)
        mv.visitInsn(Opcodes.ACONST_NULL)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        cw.visitEnd()
        return cw.toByteArray()
    }

    private static class ByteArrayClassLoader extends ClassLoader {
        private final String className
        private final byte[] bytes

        ByteArrayClassLoader(ClassLoader parent, String className, byte[] bytes) {
            super(parent)
            this.className = className
            this.bytes = bytes
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name == className) {
                return defineClass(name, bytes, 0, bytes.length)
            }
            return super.findClass(name)
        }

        @Override
        InputStream getResourceAsStream(String name) {
            if (name == className.replace('.', '/') + ".class") {
                return new ByteArrayInputStream(bytes)
            }
            return super.getResourceAsStream(name)
        }
    }
}
