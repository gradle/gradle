/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit

import groovy.transform.CompileStatic
import junit.framework.TestCase
import junit.framework.TestSuite
import org.junit.Ignore
import org.junit.Test
import org.junit.internal.runners.InitializationError
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.internal.runners.JUnit4ClassRunner
import org.junit.internal.runners.SuiteMethod
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runner.Runner
import org.junit.runner.notification.RunNotifier
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import spock.lang.Specification

import java.lang.annotation.IncompleteAnnotationException
import java.lang.invoke.MethodHandles

/**
 * Tests {@link IgnoredTestDescriptorProvider}.
 */
class IgnoredTestDescriptorProviderTest extends Specification {

    def "can get individual test descriptions for ignored test classes"() {
        expect:
        describe(RunWithSpec.class)*.getDisplayName() == ["CHILD(SUITE)"]
        describe(SuiteMethodSpec.class)*.getDisplayName() == ["testSomething(org.gradle.api.internal.tasks.testing.junit.IgnoredTestDescriptorProviderTest\$TestCaseSpec)"]
        describe(TestCaseSpec.class)*.getDisplayName() == ["testSomething(org.gradle.api.internal.tasks.testing.junit.IgnoredTestDescriptorProviderTest\$TestCaseSpec)"]
        describe(JUnit4Spec.class)*.getDisplayName() == ["doTest(org.gradle.api.internal.tasks.testing.junit.IgnoredTestDescriptorProviderTest\$JUnit4Spec)"]
    }

    private List<Description> describe(Class<?> testClass) {
        IgnoredTestDescriptorProvider.getAllDescriptions(Description.createSuiteDescription(testClass), testClass.getName())
    }

    /**
     * Generates a class annotated with {@code @RunWith} but without its mandatory {@code value},
     * so that reading it reflectively throws {@link IncompleteAnnotationException}. Neither Groovy 5
     * nor javac will compile such an annotation from source.
     *
     * <p>{@code @CompileStatic} is required so the caller-sensitive {@link MethodHandles#lookup()}
     * resolves to this test class rather than the Groovy runtime, keeping the generated class in the
     * lookup's package.
     */
    @CompileStatic
    private static Class<?> emptyRunWithClass() {
        String name = "${IgnoredTestDescriptorProviderTest.class.name}\$GeneratedEmptyRunWithSpec"
        ClassWriter cw = new ClassWriter(0)
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name.replace('.', '/'), null, "java/lang/Object", null)

        AnnotationVisitor av = cw.visitAnnotation(Type.getDescriptor(RunWith), true)
        // deliberately omit the 'value' member to produce an incomplete annotation
        av.visitEnd()

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()
        cw.visitEnd()

        return MethodHandles.lookup().defineClass(cw.toByteArray())
    }

    def "can get @RunWith runner through legacy means"() {
        expect:
        IgnoredTestDescriptorProvider.getRunnerLegacy(RunWithSpec.class) instanceof CustomRunner

        when:
        IgnoredTestDescriptorProvider.getRunnerLegacy(emptyRunWithClass())

        then:
        thrown IncompleteAnnotationException

        when:
        IgnoredTestDescriptorProvider.getRunnerLegacy(MissingConstructorRunWithSpec.class)

        then:
        thrown InitializationError
    }

    def "can get SuiteMethod runner through legacy means"() {
        expect:
        IgnoredTestDescriptorProvider.getRunnerLegacy(SuiteMethodSpec.class) instanceof SuiteMethod
    }

    def "can get JUnit 3 runner through legacy means"() {
        expect:
        IgnoredTestDescriptorProvider.getRunnerLegacy(TestCaseSpec.class) instanceof JUnit38ClassRunner
    }

    def "can get JUnit 4 runner through legacy means"() {
        expect:
        IgnoredTestDescriptorProvider.getRunnerLegacy(JUnit4Spec.class) instanceof JUnit4ClassRunner
    }

    @Ignore
    @RunWith(CustomRunner.class)
    static class RunWithSpec {
        void doTest() {}
    }

    @Ignore
    @RunWith(MissingConstructorRunner.class)
    static class MissingConstructorRunWithSpec {
        void doTest() {}
    }

    @Ignore
    static class SuiteMethodSpec {
        static TestSuite suite() {
            return new SuiteMethodSuite()
        }
        void doTest() {}
    }

    static class SuiteMethodSuite extends TestSuite {
        SuiteMethodSuite() {
            super(TestCaseSpec.class)
        }
    }

    @Ignore
    static class TestCaseSpec extends TestCase {
        void testSomething() {}
    }

    @Ignore
    static class JUnit4Spec {
        @Test
        void doTest() {}
    }

    static class CustomRunner extends Runner {
        CustomRunner(Class<?> clazz) {}

        @Override
        Description getDescription() {
            Description desc = Description.createSuiteDescription("SUITE")
            desc.addChild(Description.createTestDescription("SUITE", "CHILD"))
            return desc
        }

        @Override
        void run(RunNotifier notifier) {}
    }

    static class MissingConstructorRunner extends Runner {
        @Override
        Description getDescription() {
            return Description.createSuiteDescription("DESCRIPTION")
        }

        @Override
        void run(RunNotifier notifier) {}
    }
}
