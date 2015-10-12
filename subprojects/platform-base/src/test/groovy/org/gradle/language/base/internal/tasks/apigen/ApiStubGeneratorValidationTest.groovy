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
import spock.lang.Unroll

@Requires(TestPrecondition.JDK6_OR_LATER)
class ApiStubGeneratorValidationTest extends ApiStubGeneratorTestSupport {
    @Unroll
    def "should not throw an error if exposing a JDK class #type in method return type"() {
        given:
        def api = toApi(['com.acme'], ['com.acme.A': """package com.acme;

public abstract class A {
    public abstract $type foo();
    public abstract $type[] bar();
}"""])

        when:
        api.loadStub(api.classes['com.acme.A'])

        then:
        noExceptionThrown()

        where:
        type << ['String', 'boolean', 'byte', 'short', 'char', 'int', 'long', 'float', 'double']
    }

    @Unroll
    def "should throw an error if an implementation class is exposed in the public API in a #descriptor"() {
        given:
        def api = toApi(['com.acme'], ['com.acme.A'             : """package com.acme;
import com.acme.internal.AImpl;
import java.util.List;

public class A {
    $method
}""",
                                       'com.acme.internal.AImpl': '''package com.acme.internal;
public class AImpl {}

'''])

        when:
        api.loadStub(api.classes['com.acme.A'])

        then:
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "In $methodDescriptor, type com.acme.internal.AImpl is exposed in the public API but doesn't belong to the allowed packages."

        where:
        descriptor                  | method                                           | methodDescriptor
        'return type'               | 'public AImpl getImpl() { return new AImpl(); }' | 'public com.acme.internal.AImpl getImpl()'
        'parameter'                 | 'public void getImpl(AImpl impl) { }'            | 'public void getImpl(com.acme.internal.AImpl)'
        'generic type'              | 'public List<AImpl> getImpl() { return null; }'  | 'public java.util.List getImpl()'
        'generic type in parameter' | 'public void getImpl(List<AImpl> impls) { }'     | 'public void getImpl(java.util.List)'
    }

    def "should throw an error listing all invalid exposed types for a single method"() {
        given:
        def api = toApi(['com.acme'], ['com.acme.A'             : """package com.acme;
import com.acme.internal.AImpl;
import com.acme.internal.AInternal;

public class A {
    public AImpl toImpl(AInternal internal) { return null; }
}""",
                                       'com.acme.internal.AImpl': '''package com.acme.internal;
public class AImpl {}

''',
                                       'com.acme.internal.AInternal': '''package com.acme.internal;
public class AInternal {}

'''])

        when:
        api.loadStub(api.classes['com.acme.A'])

        then:
        def ex = thrown(InvalidPublicAPIException)
        ex.message == """The following types are referenced in public com.acme.internal.AImpl toImpl(com.acme.internal.AInternal) but don't belong to the allowed packages:
   - com.acme.internal.AInternal
   - com.acme.internal.AImpl
"""
    }

    void "reports error if class is annotated with an annotation that doesn't belong to the public API"() {
        given:
        def api = toApi(['com.acme'], [
            'com.acme.A': '''package com.acme;
import com.acme.internal.Ann;

@Ann public class A {}
''',
            'com.acme.internal.Ann': '''package com.acme.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Ann {}
'''
        ])

        when:
        def clazz = api.classes['com.acme.A']
        def annotations = clazz.clazz.annotations
        api.loadStub(clazz)

        then:
        annotations.size() == 1
        annotations[0].annotationType().name == 'com.acme.internal.Ann'
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "'com.acme.A' is annotated with 'com.acme.internal.Ann' effectively exposing it in the public API but its package doesn't belong to the allowed packages."
    }

    void "reports error if a method is annotated with an annotation that doesn't belong to the public API"() {
        given:
        def api = toApi(['com.acme'], [
            'com.acme.A': '''package com.acme;
import com.acme.internal.Ann;

public class A {
    @Ann public void foo() {}
}
''',
            'com.acme.internal.Ann': '''package com.acme.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Ann {}
'''
        ])

        when:
        def clazz = api.classes['com.acme.A']
        def annotations = clazz.clazz.getDeclaredMethod('foo').annotations
        api.loadStub(clazz)

        then:
        annotations.size() == 1
        annotations[0].annotationType().name == 'com.acme.internal.Ann'
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "'public void foo()' is annotated with 'com.acme.internal.Ann' effectively exposing it in the public API but its package doesn't belong to the allowed packages."
    }

    void "reports error if a method parameter is annotated with an annotation that doesn't belong to the public API"() {
        given:
        def api = toApi(['com.acme'], [
            'com.acme.A': '''package com.acme;
import com.acme.internal.Ann;

public class A {
    public void foo(@Ann String bar) {}
}
''',
            'com.acme.internal.Ann': '''package com.acme.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Ann {}
'''
        ])

        when:
        def clazz = api.classes['com.acme.A']
        api.loadStub(clazz)

        then:
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "'public void foo(java.lang.String)' is annotated with 'com.acme.internal.Ann' effectively exposing it in the public API but its package doesn't belong to the allowed packages."
    }

    void "cannot have a superclass which is not in the public API"() {
        given:
        def api = toApi(['com.acme'], ['com.acme.A'             : """package com.acme;
import com.acme.internal.AImpl;

public class A extends AImpl {
}""",
                                       'com.acme.internal.AImpl': '''package com.acme.internal;
public class AImpl {}

'''])

        when:
        api.loadStub(api.classes['com.acme.A'])

        then:
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "'com.acme.A' extends 'com.acme.internal.AImpl' which package doesn't belong to the allowed packages."
    }

    void "cannot have an interface which is not in the public API"() {
        given:
        def api = toApi(['com.acme'], ['com.acme.A'             : """package com.acme;
import com.acme.internal.AInternal;

public class A implements AInternal {
}""",
                                       'com.acme.internal.AInternal': '''package com.acme.internal;
public interface AInternal {}

'''])

        when:
        api.loadStub(api.classes['com.acme.A'])

        then:
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "'com.acme.A' declares interface 'com.acme.internal.AInternal' which package doesn't belong to the allowed packages."
    }

}
