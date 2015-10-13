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
import spock.lang.Unroll

import java.lang.reflect.Modifier

@Requires(TestPrecondition.JDK6_OR_LATER)
class ApiStubGeneratorInnerClassTest extends ApiStubGeneratorTestSupport {

    private final static int ACC_PUBLICSTATIC = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
    private final static int ACC_PROTECTEDSTATIC = Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC
    private final static int ACC_PRIVATESTATIC = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC

    @Unroll
    def "should not remove #modifier inner class"() {
        given:
        def api = toApi 'A': """
public class A {
   $modifier class Inner {
      public void foo() {}
   }
}"""

        when:
        def outer = api.classes.A
        def inner = api.classes['A$Inner']
        def stubbedOuter = api.loadStub(outer)
        def stubbedInner = api.loadStub(inner)

        then:
        inner.clazz.getDeclaredMethod('foo').modifiers == Modifier.PUBLIC
        stubbedInner.modifiers == access
        hasMethod(stubbedInner, 'foo').modifiers == Modifier.PUBLIC

        when:
        def o = !(modifier =~ /static/) ? stubbedInner.newInstance(null) : stubbedInner.newInstance()
        o.foo()

        then:
        thrown(UnsupportedOperationException)

        where:
        modifier           | access
        'public'           | Opcodes.ACC_PUBLIC
        'protected'        | Opcodes.ACC_PROTECTED
        'private'          | Opcodes.ACC_PRIVATE
        ''                 | 0
        'public static'    | ACC_PUBLICSTATIC
        'protected static' | ACC_PROTECTEDSTATIC
        'private static'   | ACC_PRIVATESTATIC
        'static'           | Opcodes.ACC_STATIC

    }

}
