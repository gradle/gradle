/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util

import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.jmock.integration.junit4.JMock
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

import static org.hamcrest.Matchers.anything
import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.*
import static org.objectweb.asm.Opcodes.*

@RunWith(JMock.class)
class DiffUtilTest {
    @Rule
    private final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    private TestFile distDir

    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before
    public void setup() {
        distDir = tmpDir.createDir("dist")
    }

    @After
    public void cleanup() {
        distDir.deleteDir()
    }

    @Test
    public void notifiesListenerOfElementAddedToSet() {
        ChangeListener<String> listener = context.mock(ChangeListener.class)
        Set<String> a = ['a', 'b'] as Set
        Set<String> b = ['a', 'b', 'c'] as Set

        context.checking {
            one(listener).added('c')
        }

        DiffUtil.diff(b, a, listener)
    }

    @Test
    public void notifiesListenerOfElementRemovedFromSet() {
        ChangeListener<String> listener = context.mock(ChangeListener.class)
        Set<String> a = ['a', 'b'] as Set
        Set<String> b = ['a', 'b', 'c'] as Set

        context.checking {
            one(listener).removed('c')
        }

        DiffUtil.diff(a, b, listener)
    }

    @Test
    public void notifiesListenerOfElementAddedToMap() {
        ChangeListener<Map.Entry<String, String>> listener = context.mock(ChangeListener.class)
        Map<String, String> a = [a: 'value a', b: 'value b']
        Map<String, String> b = [a: 'value a', b: 'value b', c: 'value c']

        context.checking {
            one(listener).added(withParam(anything()))
            will { entry ->
                assertThat(entry.key, equalTo('c'))
                assertThat(entry.value, equalTo('value c'))
            }
        }

        DiffUtil.diff(b, a, listener)
    }

    @Test
    public void notifiesListenerOfElementRemovedFromMap() {
        ChangeListener<Map.Entry<String, String>> listener = context.mock(ChangeListener.class)
        Map<String, String> a = [a: 'value a', b: 'value b']
        Map<String, String> b = [a: 'value a', b: 'value b', c: 'value c']

        context.checking {
            one(listener).removed(withParam(anything()))
            will { entry ->
                assertThat(entry.key, equalTo('c'))
                assertThat(entry.value, equalTo('value c'))
            }
        }

        DiffUtil.diff(a, b, listener)
    }

    @Test
    public void notifiesListenerOfChangedElementInMap() {
        ChangeListener<Map.Entry<String, String>> listener = context.mock(ChangeListener.class)
        Map<String, String> a = [a: 'value a', b: 'value b']
        Map<String, String> b = [a: 'value a', b: 'new b']

        context.checking {
            one(listener).changed(withParam(anything()))
            will { entry ->
                assertThat(entry.key, equalTo('b'))
                assertThat(entry.value, equalTo('new b'))
            }
        }

        DiffUtil.diff(b, a, listener)
    }

    @Test
    public void sameObjectsEqual() {
        Object o1 = new Object()
        assertTrue(DiffUtil.checkEquality(o1, o1))
    }

    @Test
    public void equalObjectsEqual() {
        String s1 = new String("Foo")
        String s2 = new String("Foo")
        assertTrue(DiffUtil.checkEquality(s1, s2))
    }

    @Test
    public void notEqualObjectsNotEqual() {
        String s1 = new String("Foo")
        String s2 = new String("Bar")
        assertFalse(DiffUtil.checkEquality(s1, s2))
    }

    private enum LocalEnum1 {
        DUCK, GOOSE
    }

    private enum LocalEnum2 {
        DUCK, GOOSE
    }

    @Test
    public void sameEnumerationEqual() {
        assertTrue(DiffUtil.checkEquality(LocalEnum1.DUCK, LocalEnum1.DUCK))
    }

    @Test
    public void sameEnumerationDifferentConstantsNotEqual() {
        assertFalse(DiffUtil.checkEquality(LocalEnum1.DUCK, LocalEnum1.GOOSE))
    }

    @Test
    public void differentEnumerationsNotEqual() {
        assertFalse(DiffUtil.checkEquality(LocalEnum1.DUCK, LocalEnum2.DUCK))
    }

    // We may want to change this behavior in the future, so don't be afraid to remove this test.
    @Test
    public void differentClassLoadersNotEqual() {
        TestFile jar = distDir.file("testclass-1.2.jar")
        createJarFile(jar)

        List<Closeable> loaders = []

        try {
            Class<?> clazz1 = loadClassFromJar(jar, "org.gradle.MyClass", loaders)
            Class<?> clazz2 = loadClassFromJar(jar, "org.gradle.MyClass", loaders)

            assertNotSame(clazz1, clazz2)

            Object o1 = clazz1.newInstance()
            Object o2 = clazz2.newInstance()

            assertNotEquals(o1, o2)
            assertFalse(DiffUtil.checkEquality(o1, o2))
        } finally {
            CompositeStoppable.stoppable(loaders).stop()
        }
    }

    @Test
    public void enumsWithDifferentClassLoadersEqual() {
        TestFile jar = distDir.file("testclass-1.2.jar")
        createJarFile(jar)

        List<Closeable> loaders = []

        try {
            Class<?> clazz1 = loadClassFromJar(jar, "org.gradle.MyEnum", loaders)
            Class<?> clazz2 = loadClassFromJar(jar, "org.gradle.MyEnum", loaders)

            assertNotSame(clazz1, clazz2)

            Object o1 = Enum.valueOf(clazz1, "OTTER")
            Object o2 = Enum.valueOf(clazz2, "OTTER")

            Object s1 = Enum.valueOf(clazz1, "SEAL")
            Object s2 = Enum.valueOf(clazz2, "SEAL")

            assertNotEquals(o1, o2)
            assertTrue(DiffUtil.checkEquality(o1, o2))
            assertFalse(DiffUtil.checkEquality(o1, s1))
            assertFalse(DiffUtil.checkEquality(o1, s2))
            assertFalse(DiffUtil.checkEquality(o2, s1))
            assertFalse(DiffUtil.checkEquality(o2, s2))
        } finally {
            CompositeStoppable.stoppable(loaders).stop()
        }
    }

    private void createJarFile(TestFile jar) {
        TestFile contents = tmpDir.createDir('contents')
        TestFile classFile = contents.createFile('org/gradle/MyClass.class')
        TestFile enumFile = contents.createFile('org/gradle/MyEnum.class')

        ClassNode myClass = createMyClass()
        ClassNode myEnum = createMyEnum()

        writeClassNode(myClass, classFile)
        writeClassNode(myEnum, enumFile)

        contents.zipTo(jar)
    }

    private static void writeClassNode(ClassNode classNode, TestFile classFile) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES)
        classNode.accept(cw)

        classFile.withDataOutputStream {
            it.write(cw.toByteArray())
        }
    }

    private Class loadClassFromJar(TestFile jar, String className, Collection<ClassLoader> loaders) {
        // This is to prevent the jar file being held open
        URL url = new URL("jar:file://valid_jar_url_syntax.jar!/")
        URLConnection urlConnection = url.openConnection()
        def original = urlConnection.getDefaultUseCaches()
        urlConnection.setDefaultUseCaches(false)

        try {
            URL[] urls = [new URL("jar:${jar.toURI().toURL()}!/")] as URL[]
            URLClassLoader ucl = new URLClassLoader(urls)
            if (ucl instanceof Closeable) {
                loaders << ucl
            }
            Class.forName(className, true, ucl)
        } finally {
            urlConnection.setDefaultUseCaches(original)
        }
    }

    private static ClassNode createMyClass() {
        ClassNode classNode = new ClassNode()
        classNode.version = V1_6
        classNode.access = ACC_PUBLIC
        classNode.name = 'org/gradle/MyClass'
        classNode.superName = 'java/lang/Object'

        // Default constructor for myClass.
        MethodNode constructor = new MethodNode(
            ACC_PUBLIC,
            "<init>",
            "()V",
            null,
            null);

        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, classNode.superName, "<init>", "()V");
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        constructor.accept(classNode)
        return classNode
    }

    private static ClassNode createMyEnum() {
        // Enums have some lengthy bytecode.

        ClassNode classNode = new ClassNode()
        FieldVisitor fv;
        MethodVisitor mv;
        AnnotationVisitor av0;

        classNode.visit(V1_5, ACC_PUBLIC + ACC_FINAL + ACC_SUPER + ACC_ENUM, "org/gradle/MyEnum", "Ljava/lang/Enum<Lorg/gradle/MyEnum;>;", "java/lang/Enum", null);

        fv = classNode.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC + ACC_ENUM, "OTTER", "Lorg/gradle/MyEnum;", null, null);
        fv.visitEnd();
        fv = classNode.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC + ACC_ENUM, "SEAL", "Lorg/gradle/MyEnum;", null, null);
        fv.visitEnd();

        fv = classNode.visitField(ACC_PRIVATE + ACC_FINAL + ACC_STATIC + ACC_SYNTHETIC, 'ENUM$VALUES', "[Lorg/gradle/MyEnum;", null, null);
        fv.visitEnd();

        mv = classNode.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitLineNumber(2, l0);
        mv.visitTypeInsn(NEW, "org/gradle/MyEnum");
        mv.visitInsn(DUP);
        mv.visitLdcInsn("OTTER");
        mv.visitInsn(ICONST_0);
        mv.visitMethodInsn(INVOKESPECIAL, "org/gradle/MyEnum", "<init>", "(Ljava/lang/String;I)V");
        mv.visitFieldInsn(PUTSTATIC, "org/gradle/MyEnum", "OTTER", "Lorg/gradle/MyEnum;");
        mv.visitTypeInsn(NEW, "org/gradle/MyEnum");
        mv.visitInsn(DUP);
        mv.visitLdcInsn("SEAL");
        mv.visitInsn(ICONST_1);
        mv.visitMethodInsn(INVOKESPECIAL, "org/gradle/MyEnum", "<init>", "(Ljava/lang/String;I)V");
        mv.visitFieldInsn(PUTSTATIC, "org/gradle/MyEnum", "SEAL", "Lorg/gradle/MyEnum;");
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLineNumber(1, l1);
        mv.visitInsn(ICONST_2);
        mv.visitTypeInsn(ANEWARRAY, "org/gradle/MyEnum");
        mv.visitInsn(DUP);
        mv.visitInsn(ICONST_0);
        mv.visitFieldInsn(GETSTATIC, "org/gradle/MyEnum", "OTTER", "Lorg/gradle/MyEnum;");
        mv.visitInsn(AASTORE);
        mv.visitInsn(DUP);
        mv.visitInsn(ICONST_1);
        mv.visitFieldInsn(GETSTATIC, "org/gradle/MyEnum", "SEAL", "Lorg/gradle/MyEnum;");
        mv.visitInsn(AASTORE);
        mv.visitFieldInsn(PUTSTATIC, "org/gradle/MyEnum", 'ENUM$VALUES', "[Lorg/gradle/MyEnum;");
        mv.visitInsn(RETURN);
        mv.visitMaxs(4, 0);
        mv.visitEnd();

        mv = classNode.visitMethod(ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
        mv.visitCode();
        l0 = new Label();
        mv.visitLabel(l0);
        mv.visitLineNumber(1, l0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V");
        mv.visitInsn(RETURN);
        l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLocalVariable("this", "Lorg/gradle/MyEnum;", null, l0, l1, 0);
        mv.visitMaxs(3, 3);
        mv.visitEnd();

        mv = classNode.visitMethod(ACC_PUBLIC + ACC_STATIC, "values", "()[Lorg/gradle/MyEnum;", null, null);
        mv.visitCode();
        l0 = new Label();
        mv.visitLabel(l0);
        mv.visitLineNumber(1, l0);
        mv.visitFieldInsn(GETSTATIC, "org/gradle/MyEnum", 'ENUM$VALUES', "[Lorg/gradle/MyEnum;");
        mv.visitInsn(DUP);
        mv.visitVarInsn(ASTORE, 0);
        mv.visitInsn(ICONST_0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ARRAYLENGTH);
        mv.visitInsn(DUP);
        mv.visitVarInsn(ISTORE, 1);
        mv.visitTypeInsn(ANEWARRAY, "org/gradle/MyEnum");
        mv.visitInsn(DUP);
        mv.visitVarInsn(ASTORE, 2);
        mv.visitInsn(ICONST_0);
        mv.visitVarInsn(ILOAD, 1);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V");
        mv.visitVarInsn(ALOAD, 2);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(5, 3);
        mv.visitEnd();

        mv = classNode.visitMethod(ACC_PUBLIC + ACC_STATIC, "valueOf", "(Ljava/lang/String;)Lorg/gradle/MyEnum;", null, null);
        mv.visitCode();
        l0 = new Label();
        mv.visitLabel(l0);
        mv.visitLineNumber(1, l0);
        mv.visitLdcInsn(Type.getType("Lorg/gradle/MyEnum;"));
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;");
        mv.visitTypeInsn(CHECKCAST, "org/gradle/MyEnum");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();

        classNode.visitEnd();
        return classNode
    }
}
