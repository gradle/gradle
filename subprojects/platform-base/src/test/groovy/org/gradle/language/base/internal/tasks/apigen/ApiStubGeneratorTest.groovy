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
import org.objectweb.asm.Opcodes

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


}
