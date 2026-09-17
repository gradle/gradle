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


import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import spock.lang.Issue

import java.lang.reflect.Modifier

class ApiClassExtractorTest extends ApiClassExtractorTestSupport {

    // Records require Java 16, and the tests always run on a newer JVM than that
    private static final String RECORD_TARGET_VERSION = '17'

    def "should not remove public method"() {
        given:
        def api = toApi 'A': '''
            public class A {
                public void foo() {}
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        clazz.clazz.getDeclaredMethod('foo').modifiers == Modifier.PUBLIC
        hasMethod(extracted, 'foo')

        when:
        def o = createInstance(extracted)
        o.foo()

        then:
        def e = thrown(Exception)
        e.cause instanceof Error
    }

    def "should not remove protected method"() {
        given:
        def api = toApi 'A': '''
            public class A {
                protected void foo() {}
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        hasMethod(clazz.clazz, 'foo').modifiers == Modifier.PROTECTED
        hasMethod(extracted, 'foo')

        when:
        createInstance(extracted)

        then:
        def e = thrown(Exception)
        e.cause instanceof Error
    }

    def "should remove private method"() {
        given:
        def api = toApi 'A': '''
            public class A {
                private void foo() {}
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        hasMethod(clazz.clazz, 'foo').modifiers == Modifier.PRIVATE
        noSuchMethod(extracted, 'foo')

    }

    def "should not remove package private method if no API is defined"() {
        given:
        def api = toApi 'A': '''
            public class A {
                void foo() {}
                static void bar() {}
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        hasMethod(clazz.clazz, 'foo').modifiers == 0
        hasMethod(clazz.clazz, 'bar').modifiers == Opcodes.ACC_STATIC
        hasMethod(extracted, 'foo').modifiers == 0
        hasMethod(extracted, 'bar').modifiers == Opcodes.ACC_STATIC

    }

    def "should remove package private method if API is defined"() {
        given:
        def api = toApi([''], ['A': '''
            public class A {
                void foo() {}
                static void bar() {}
            }
        '''
        ])

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        hasMethod(clazz.clazz, 'foo').modifiers == 0
        hasMethod(clazz.clazz, 'bar').modifiers == Opcodes.ACC_STATIC
        noSuchMethod(extracted, 'foo')
        noSuchMethod(extracted, 'bar')

    }

    def "interface type should not generate implementation"() {
        given:
        def api = toApi 'A': '''
            public interface A {
                void foo();
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        hasMethod(clazz.clazz, 'foo').modifiers == Opcodes.ACC_ABSTRACT + Opcodes.ACC_PUBLIC
        hasMethod(extracted, 'foo').modifiers == Opcodes.ACC_ABSTRACT + Opcodes.ACC_PUBLIC

    }

    def "abstract class can have both implemented and non-implemented methods"() {
        given:
        def api = toApi(
            'com.acme.A': '''
                package com.acme;

                public abstract class A {
                    public static void STATIC_IN_A() {}
                    public abstract void foo();
                    public void bar() {}
                }
            ''',
            'com.acme.B': '''
                package com.acme;
                public class B extends A {
                    public static void STATIC_IN_B() {}
                    public void foo() {}
                }
            '''
        )

        when:
        def clazzA = api.classes['com.acme.A']
        def clazzB = api.classes['com.acme.B']
        def extractedA = api.extractAndLoadApiClassFrom(clazzA)
        def extractedB = api.extractAndLoadApiClassFrom(clazzB)

        then:
        hasMethod(clazzA.clazz, 'foo').modifiers == Opcodes.ACC_ABSTRACT + Opcodes.ACC_PUBLIC
        hasMethod(clazzA.clazz, 'bar').modifiers == Opcodes.ACC_PUBLIC
        hasMethod(extractedA, 'foo').modifiers == Opcodes.ACC_ABSTRACT + Opcodes.ACC_PUBLIC
        hasMethod(extractedA, 'bar').modifiers == Opcodes.ACC_PUBLIC

        and:
        hasMethod(clazzB.clazz, 'foo').modifiers == Opcodes.ACC_PUBLIC
        hasMethod(extractedB, 'foo').modifiers == Opcodes.ACC_PUBLIC

        when:
        createInstance(extractedB)

        then:
        def e = thrown(Exception)
        e.cause instanceof Error

        when:
        extractedA.STATIC_IN_A()

        then:
        thrown(Error)

        when:
        extractedB.STATIC_IN_B()

        then:
        thrown(Error)

    }

    void "static initializer is removed"() {
        given:
        def api = toApi 'com.acme.A': '''
            package com.acme;

            public abstract class A {
                public static void forceInit() {}

                static {
                    if (true) {
                        throw new RuntimeException("This is a static initializer");
                    }
                }
            }
        '''

        when:
        api.classes['com.acme.A'].clazz.forceInit()

        then:
        def ex = thrown(ExceptionInInitializerError)
        ex.cause.message == 'This is a static initializer'

        when:
        def clazz = api.extractAndLoadApiClassFrom(api.classes['com.acme.A'])
        clazz.forceInit()

        then:
        ex = thrown(Error)
        ex.message == null
    }

    void "field #modifiers initial value for #type is #expected"() {
        given:
        def api = toApi 'com.acme.A': """
            package com.acme;

            public abstract class A {
                public $modifiers $type CONSTANT = $value;
            }
        """

        when:
        def extracted = api.extractAndLoadApiClassFrom(api.classes['com.acme.A'])
        def extractedValue = extracted.CONSTANT

        then:
        extractedValue == expected

        where:
        type      | modifiers      | value          | expected
        'String'  | 'static'       | '"foo"'        | null
        'String'  | 'static'       | 'null'         | null
        'int'     | 'static'       | 123            | 0
        'Class'   | 'static'       | 'String.class' | null
        'boolean' | 'static'       | 'true'         | false
        'String'  | 'static final' | '"foo"'        | "foo"
        'String'  | 'static final' | 'null'         | null
        'int'     | 'static final' | 123            | 123
        'Class'   | 'static final' | 'String.class' | null
        'boolean' | 'static final' | 'true'         | true
    }

    void "target binary compatibility is maintained for Java version #target"() {
        given:
        def api = toApi(target, [A: 'public class A {}'])

        when:
        def cr = new ClassReader(api.extractApiClassFrom(api.classes.A))
        def stubVersion = 0
        cr.accept(new ClassVisitor(Opcodes.ASM7) {
            @Override
            void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                stubVersion = version
            }
        }, 0)

        then:
        stubVersion == expectedVersion

        where:
        target | expectedVersion
        '8'    | 52
        '11'   | 55
    }

    def "should not remove public field"() {
        given:
        def api = toApi 'A': '''
            public class A {
                public String foo;
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        hasField(clazz.clazz, 'foo', String).modifiers == Modifier.PUBLIC
        hasField(extracted, 'foo', String)

        when:
        def o = createInstance(extracted)
        o.foo()

        then:
        def e = thrown(Exception)
        e.cause instanceof Error
    }

    def "should not remove protected field"() {
        given:
        def api = toApi 'A': '''
            public class A {
                protected String foo;
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        hasField(clazz.clazz, 'foo', String).modifiers == Modifier.PROTECTED
        hasField(extracted, 'foo', String)

        when:
        createInstance(extracted)

        then:
        def e = thrown(Exception)
        e.cause instanceof Error
    }

    def "should remove private field"() {
        given:
        def api = toApi 'A': '''
            public class A {
                private String foo;
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        hasField(clazz.clazz, 'foo', String).modifiers == Modifier.PRIVATE
        noSuchField(extracted, 'foo', String)

    }

    def "should not remove package private field if no API is declared"() {
        given:
        def api = toApi 'A': '''
            public class A {
                String foo;
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        hasField(clazz.clazz, 'foo', String).modifiers == 0
        hasField(extracted, 'foo', String).modifiers == 0

    }

    def "should not remove package private members that have additional modifiers if no API is declared"() {
        given:
        def api = toApi 'A': '''
            public abstract class A {
                volatile int foo = 0;
                final int bar = 0;

                abstract int getFoo();
                synchronized void doBar() { }
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        hasField(clazz.clazz, 'foo', int).modifiers == Modifier.VOLATILE
        hasField(extracted, 'foo', int).modifiers == Modifier.VOLATILE
        hasField(clazz.clazz, 'bar', int).modifiers == Modifier.FINAL
        hasField(extracted, 'bar', int).modifiers == Modifier.FINAL
        hasMethod(clazz.clazz, 'getFoo').modifiers == Modifier.ABSTRACT
        hasMethod(extracted, 'getFoo').modifiers == Modifier.ABSTRACT
        hasMethod(clazz.clazz, 'doBar').modifiers == Modifier.SYNCHRONIZED
        hasMethod(extracted, 'doBar').modifiers == Modifier.SYNCHRONIZED

    }

    def "should remove package private field if API is declared"() {
        given:
        def api = toApi([''], ['A': '''
            public class A {
                String foo;
            }
        '''])

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        hasField(clazz.clazz, 'foo', String).modifiers == 0
        noSuchField(extracted, 'foo', String)

    }

    def "extracted class does not depend on the order in which members are declared"() {
        given: "the same API, with fields and methods declared in different order"
        def declaredInOneOrder = compileTo(new File(temporaryFolder, 'one'), ['com.acme.A': '''
            package com.acme;

            public class A {
                public int b = 1;
                public int a = 2;
                public void bar() {}
                public void foo() {}
            }
        '''], [])
        def declaredInAnotherOrder = compileTo(new File(temporaryFolder, 'another'), ['com.acme.A': '''
            package com.acme;

            public class A {
                public int a = 2;
                public int b = 1;
                public void foo() {}
                public void bar() {}
            }
        '''], [])

        when:
        def one = declaredInOneOrder.extractApiClassFrom(declaredInOneOrder.classes['com.acme.A'])
        def another = declaredInAnotherOrder.extractApiClassFrom(declaredInAnotherOrder.classes['com.acme.A'])

        then: "the extracted classes are byte-identical, so reordering members does not invalidate compile avoidance"
        one == another
    }

    def "stubs should not contain any source or debug information"() {
        given:
        def api = toApi 'com.acme.A': '''
            package com.acme;

            public abstract class A {
                public static int FOO = 666;
                public void hello(String message) {
                    System.out.println(message);
                }
            }
        '''

        when:
        def apiClassBytes = api.extractApiClassFrom(api.classes['com.acme.A'])
        def cr = new ClassReader(apiClassBytes)
        cr.accept(new ClassVisitor(Opcodes.ASM7) {
            @Override
            void visitSource(String source, String debug) {
                super.visitSource(source, debug)
                if (source) {
                    throw new AssertionError("Source information should not be visited, but found source [$source]")
                }
                if (debug) {
                    throw new AssertionError("Debug information should not be visited, but found debug [$debug]")
                }
            }

            @Override
            MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                new MethodVisitor(Opcodes.ASM7) {
                    @Override
                    void visitLineNumber(int line, Label start) {
                        throw new AssertionError("Should not produce any line number information but " +
                            "method $name$desc contains line $line label $start")
                    }

                    @Override
                    void visitLocalVariable(String lname, String ldesc, String lsignature,
                                            Label start, Label end, int index) {
                        throw new AssertionError("Should not visit any local variable, but " +
                            "found $lname in method $name$desc")
                    }
                }
            }
        }, 0)

        then:
        noExceptionThrown()
    }

    def "package private class belongs to API if no API declared"() {
        given:
        def api = toApi 'A': '''
            class A {
                String foo;
            }
        '''

        when:
        def clazz = api.classes.A

        then:
        api.isApiClassExtractedFrom(clazz)
    }

    def "package private class does not belong to API if API declared"() {
        given:
        def api = toApi([''], ['A': '''
            class A {
                String foo;
            }
        '''])

        when:
        def clazz = api.classes.A

        then:
        !api.isApiClassExtractedFrom(clazz)
    }

    @Issue("https://github.com/gradle/gradle/issues/38827")
    def "method parameter names are retained"() {
        given:
        additionalCompilerArgs = ['-parameters']
        def api = toApi([A: '''
            public class A {
                public void greet(String firstName, int repeatCount) {}
            }
        '''])

        when:
        def extracted = api.extractAndLoadApiClassFrom(api.classes.A)
        def parameters = extracted.getDeclaredMethod('greet', String, int).parameters

        then:
        parameters*.namePresent == [true, true]
        parameters*.name == ['firstName', 'repeatCount']
    }

    @Issue("https://github.com/gradle/gradle/issues/38827")
    def "extracted class changes when a method parameter is renamed"() {
        given:
        additionalCompilerArgs = ['-parameters']
        def before = toApi([A: 'public class A { public void greet(String firstName, int repeatCount) {} }'])
        def beforeBytes = before.extractApiClassFrom(before.classes.A)
        def after = toApi([A: 'public class A { public void greet(String givenName, int times) {} }'])
        def afterBytes = after.extractApiClassFrom(after.classes.A)

        expect:
        beforeBytes != afterBytes
    }

    @Issue("https://github.com/gradle/gradle/issues/38823")
    def "record components are retained in declaration order"() {
        given:
        def api = toApi(RECORD_TARGET_VERSION, [Point: 'public record Point(int x, int y) {}'])

        when:
        def extracted = api.extractAndLoadApiClassFrom(api.classes.Point)

        then:
        extracted.recordComponents*.name == ['x', 'y']
        extracted.recordComponents*.type == [int, int]
    }

    @Issue("https://github.com/gradle/gradle/issues/38823")
    def "extracted class changes when the record component order changes"() {
        given:
        def before = toApi(RECORD_TARGET_VERSION, [Point: 'public record Point(int x, int y) {}'])
        def beforeBytes = before.extractApiClassFrom(before.classes.Point)
        def after = toApi(RECORD_TARGET_VERSION, [Point: 'public record Point(int y, int x) {}'])
        def afterBytes = after.extractApiClassFrom(after.classes.Point)

        expect:
        beforeBytes != afterBytes
    }

    @Issue("https://github.com/gradle/gradle/issues/38825")
    def "type annotations in a throws clause stay on the exception they annotate"() {
        given:
        def api = toApi([
            A  : '''
                import java.io.IOException;

                public class A {
                    public void foo() throws @Ann ArithmeticException, IOException {}
                }
            ''',
            Ann: '''
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE_USE})
                public @interface Ann {}
            '''
        ])

        when:
        def extractedAnn = api.extractAndLoadApiClassFrom(api.classes.Ann)
        def extracted = api.extractAndLoadApiClassFrom(api.classes.A)
        // The extractor writes the exceptions sorted, so IOException comes first
        def annotatedExceptionTypes = extracted.getDeclaredMethod('foo').annotatedExceptionTypes

        then:
        annotatedExceptionTypes*.type == [IOException, ArithmeticException]
        annotatedExceptionTypes[0].annotations.length == 0
        annotatedExceptionTypes[1].annotations*.annotationType() == [extractedAnn]
    }

    @Issue("https://github.com/gradle/gradle/issues/38825")
    def "extracted class changes when a type annotation moves to another exception"() {
        given:
        def annotation = [Ann: '''
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.RUNTIME)
            @Target({ElementType.TYPE_USE})
            public @interface Ann {}
        ''']
        // Both variants annotate the first declared exception, and both extract to the same sorted
        // throws clause, so only a mapped type_index tells them apart
        def before = toApi(annotation + [A: '''
            import java.io.IOException;
            public class A { public void foo() throws @Ann ArithmeticException, IOException {} }
        '''])
        def beforeBytes = before.extractApiClassFrom(before.classes.A)
        def after = toApi(annotation + [A: '''
            import java.io.IOException;
            public class A { public void foo() throws @Ann IOException, ArithmeticException {} }
        '''])
        def afterBytes = after.extractApiClassFrom(after.classes.A)

        expect:
        beforeBytes != afterBytes
    }
}
