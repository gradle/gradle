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

package org.gradle.language.base.internal.tasks.apigen

import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import spock.lang.Unroll

import java.lang.reflect.Modifier

@Requires(TestPrecondition.JDK6_OR_LATER)
class ApiStubGeneratorTest extends ApiStubGeneratorTestSupport {

    def "should not remove public method"() {
        given:
        def api = toApi 'A': '''public class A {
    public void foo() {}
}'''

        when:
        def clazz = api.classes.A
        def stubbed = api.loadStub(clazz)

        then:
        clazz.clazz.getDeclaredMethod('foo').modifiers == Modifier.PUBLIC
        hasMethod(stubbed, 'foo')

        when:
        def o = stubbed.newInstance()
        o.foo()

        then:
        thrown(UnsupportedOperationException)

    }

    def "should not remove protected method"() {
        given:
        def api = toApi 'A': '''public class A {
    protected void foo() {}
}'''

        when:
        def clazz = api.classes.A
        def stubbed = api.loadStub(clazz)

        then:
        hasMethod(clazz.clazz, 'foo').modifiers == Modifier.PROTECTED
        hasMethod(stubbed, 'foo')

        when:
        stubbed.newInstance()

        then:
        thrown(UnsupportedOperationException)

    }

    def "should remove private method"() {
        given:
        def api = toApi 'A': '''public class A {
    private void foo() {}
}'''

        when:
        def clazz = api.classes.A
        def stubbed = api.loadStub(clazz)

        then:
        hasMethod(clazz.clazz, 'foo').modifiers == Modifier.PRIVATE
        noSuchMethod(stubbed, 'foo')

    }

    def "should remove package private method"() {
        given:
        def api = toApi 'A': '''public class A {
    void foo() {}
}'''

        when:
        def clazz = api.classes.A
        def stubbed = api.loadStub(clazz)

        then:
        hasMethod(clazz.clazz, 'foo').modifiers == 0
        noSuchMethod(stubbed, 'foo')

    }

    def "interface type should not generate implementation"() {
        given:
        def api = toApi 'A': '''public interface A {
    void foo();
}'''

        when:
        def clazz = api.classes.A
        def stubbed = api.loadStub(clazz)

        then:
        hasMethod(clazz.clazz, 'foo').modifiers == Opcodes.ACC_ABSTRACT + Opcodes.ACC_PUBLIC
        hasMethod(stubbed, 'foo').modifiers == Opcodes.ACC_ABSTRACT + Opcodes.ACC_PUBLIC

    }

    def "abstract class can have both implemented and non-implemented methods"() {
        given:
        def api = toApi(
            'com.acme.A': '''package com.acme;

public abstract class A {
    public static void STATIC_IN_A() {}
    public abstract void foo();
    public void bar() {}
}''',
            'com.acme.B': '''package com.acme;
public class B extends A {
    public static void STATIC_IN_B() {}
    public void foo() {}
}''')

        when:
        def clazzA = api.classes['com.acme.A']
        def clazzB = api.classes['com.acme.B']
        def stubbedA = api.loadStub(clazzA)
        def stubbedB = api.loadStub(clazzB)

        then:
        hasMethod(clazzA.clazz, 'foo').modifiers == Opcodes.ACC_ABSTRACT + Opcodes.ACC_PUBLIC
        hasMethod(clazzA.clazz, 'bar').modifiers == Opcodes.ACC_PUBLIC
        hasMethod(stubbedA, 'foo').modifiers == Opcodes.ACC_ABSTRACT + Opcodes.ACC_PUBLIC
        hasMethod(stubbedA, 'bar').modifiers == Opcodes.ACC_PUBLIC

        and:
        hasMethod(clazzB.clazz, 'foo').modifiers == Opcodes.ACC_PUBLIC
        hasMethod(stubbedB, 'foo').modifiers == Opcodes.ACC_PUBLIC

        when:
        stubbedB.newInstance()

        then:
        thrown(UnsupportedOperationException)

        when:
        stubbedA.STATIC_IN_A()

        then:
        thrown(UnsupportedOperationException)

        when:
        stubbedB.STATIC_IN_B()

        then:
        thrown(UnsupportedOperationException)

    }

    void "static initializer is removed"() {
        given:
        def api = toApi(
            'com.acme.A': '''package com.acme;

public abstract class A {
    public static void forceInit() {}

    static {
        if (true) {
            throw new RuntimeException("This is a static initializer");
        }
    }
}''')
        when:
        api.classes['com.acme.A'].clazz.forceInit()

        then:
        def ex = thrown(ExceptionInInitializerError)
        ex.cause.message == 'This is a static initializer'

        when:
        def clazz = api.loadStub(api.classes['com.acme.A'])
        clazz.forceInit()

        then:
        ex = thrown(UnsupportedOperationException)
        ex.message =~ /You tried to call a method on an API class/
    }

    @Unroll
    void "constant initial value for #type is #expected"() {
        given:
        def api = toApi(
            'com.acme.A': """package com.acme;

public abstract class A {
    public static $type CONSTANT = $value;
}""")
        when:
        def stubbed = api.loadStub(api.classes['com.acme.A'])
        def stubbedValue = stubbed.CONSTANT

        then:
        stubbedValue == expected

        where:
        type      | value          | expected
        'String'  | '"foo"'        | null
        'String'  | 'null'         | null
        'int'     | 123            | 0
        'Class'   | 'String.class' | null
        'boolean' | 'true'         | false
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    void "target binary compatibility is maintained"() {
        given:
        def api = toApi(target, [A: 'public class A {}'])

        when:
        def cr = new ClassReader(api.getStubBytes(api.classes.A))
        def stubVersion = 0
        cr.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                stubVersion = version
            }
        }, 0)

        then:
        stubVersion == expectedVersion

        where:
        target | expectedVersion
        '1.6'  | 50
        '1.7'  | 51
    }

    void "annotations are retained"() {
        given:
        def api = toApi([
            A: '@Ann public class A {}',
            Ann: '''import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Ann {}
'''
        ])

        when:
        def clazz = api.classes.A.clazz
        def annotations = clazz.annotations
        def stubbed = api.loadStub(api.classes.A)
        def stubbedAnn = api.loadStub(api.classes.Ann)
        def stubbedAnnotations = stubbed.annotations

        then:
        annotations.size() == 1
        annotations[0].annotationType().name == 'Ann'
        stubbedAnnotations.size() == 1
        stubbedAnnotations[0].annotationType() == stubbedAnn
    }


    def "should not remove public field"() {
        given:
        def api = toApi 'A': '''public class A {
    public String foo;
}'''

        when:
        def clazz = api.classes.A
        def stubbed = api.loadStub(clazz)

        then:
        hasField(clazz.clazz, 'foo', String).modifiers == Modifier.PUBLIC
        hasField(stubbed, 'foo', String)

        when:
        def o = stubbed.newInstance()
        o.foo()

        then:
        thrown(UnsupportedOperationException)

    }

    def "should not remove protected field"() {
        given:
        def api = toApi 'A': '''public class A {
    protected String foo;
}'''

        when:
        def clazz = api.classes.A
        def stubbed = api.loadStub(clazz)

        then:
        hasField(clazz.clazz, 'foo', String).modifiers == Modifier.PROTECTED
        hasField(stubbed, 'foo', String)

        when:
        stubbed.newInstance()

        then:
        thrown(UnsupportedOperationException)

    }

    def "should remove private field"() {
        given:
        def api = toApi 'A': '''public class A {
    private String foo;
}'''

        when:
        def clazz = api.classes.A
        def stubbed = api.loadStub(clazz)

        then:
        hasField(clazz.clazz, 'foo', String).modifiers == Modifier.PRIVATE
        noSuchField(stubbed, 'foo', String)

    }

    def "should remove package private field"() {
        given:
        def api = toApi 'A': '''public class A {
    String foo;
}'''

        when:
        def clazz = api.classes.A
        def stubbed = api.loadStub(clazz)

        then:
        hasField(clazz.clazz, 'foo', String).modifiers == 0
        noSuchField(stubbed, 'foo', String)

    }

}
