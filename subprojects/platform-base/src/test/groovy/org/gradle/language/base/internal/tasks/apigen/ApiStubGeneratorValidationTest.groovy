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
        def clazz = api.classes['com.acme.A']
        api.loadStub(clazz)

        then:
        api.belongsToAPI(clazz)
        noExceptionThrown()

        where:
        type << ['String', 'boolean', 'byte', 'short', 'char', 'int', 'long', 'float', 'double']
    }

    @Unroll
    def "should throw an error if an implementation class is exposed in the public API in a #descriptor"() {
        given:
        validationEnabled()

        and:
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
        def clazz = api.classes['com.acme.A']
        def internal = api.classes['com.acme.internal.AImpl']
        api.loadStub(clazz)

        then:
        api.belongsToAPI(clazz)
        !api.belongsToAPI(internal)
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "In $methodDescriptor, type com.acme.internal.AImpl is exposed in the public API but its package is not one of the allowed packages."

        where:
        descriptor                  | method                                           | methodDescriptor
        'return type'               | 'public AImpl getImpl() { return new AImpl(); }' | 'public com.acme.internal.AImpl getImpl()'
        'parameter'                 | 'public void getImpl(AImpl impl) { }'            | 'public void getImpl(com.acme.internal.AImpl)'
        'generic type'              | 'public List<AImpl> getImpl() { return null; }'  | 'public java.util.List getImpl()'
        'generic type in parameter' | 'public void getImpl(List<AImpl> impls) { }'     | 'public void getImpl(java.util.List)'
    }

    @Unroll
    def "should not throw an error if an implementation class is exposed in the public API in a #descriptor but validation is disabled"() {
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
        def clazz = api.classes['com.acme.A']
        def internal = api.classes['com.acme.internal.AImpl']
        api.loadStub(clazz)

        then:
        api.belongsToAPI(clazz)
        !api.belongsToAPI(internal)
        noExceptionThrown()

        where:
        descriptor                  | method                                           | methodDescriptor
        'return type'               | 'public AImpl getImpl() { return new AImpl(); }' | 'public com.acme.internal.AImpl getImpl()'
        'parameter'                 | 'public void getImpl(AImpl impl) { }'            | 'public void getImpl(com.acme.internal.AImpl)'
        'generic type'              | 'public List<AImpl> getImpl() { return null; }'  | 'public java.util.List getImpl()'
        'generic type in parameter' | 'public void getImpl(List<AImpl> impls) { }'     | 'public void getImpl(java.util.List)'
    }

    def "should throw an error listing all invalid exposed types for a single method"() {
        given:
        validationEnabled()

        and:
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
        def clazz = api.classes['com.acme.A']
        def internal = api.classes['com.acme.internal.AInternal']
        api.loadStub(clazz)

        then:
        api.belongsToAPI(clazz)
        !api.belongsToAPI(internal)
        def ex = thrown(InvalidPublicAPIException)
        ex.message == """The following types are referenced in public com.acme.internal.AImpl toImpl(com.acme.internal.AInternal) but their package is not one of the allowed packages:
   - com.acme.internal.AInternal
   - com.acme.internal.AImpl
"""
    }

    void "reports error if class is annotated with an annotation that doesn't belong to the public API"() {
        given:
        validationEnabled()

        and:
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
        def ann = api.classes['com.acme.internal.Ann']
        def annotations = clazz.clazz.annotations
        api.loadStub(clazz)

        then:
        api.belongsToAPI(clazz)
        !api.belongsToAPI(ann)
        annotations.size() == 1
        annotations[0].annotationType().name == 'com.acme.internal.Ann'
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "'com.acme.A' is annotated with 'com.acme.internal.Ann' effectively exposing it in the public API but its package is not one of the allowed packages."
    }

    void "reports error if a method is annotated with an annotation that doesn't belong to the public API"() {
        given:
        validationEnabled()

        and:
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
        def ann = api.classes['com.acme.internal.Ann']
        def annotations = clazz.clazz.getDeclaredMethod('foo').annotations
        api.loadStub(clazz)

        then:
        api.belongsToAPI(clazz)
        !api.belongsToAPI(ann)
        annotations.size() == 1
        annotations[0].annotationType().name == 'com.acme.internal.Ann'
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "'public void foo()' is annotated with 'com.acme.internal.Ann' effectively exposing it in the public API but its package is not one of the allowed packages."
    }

    void "reports error if a field is annotated with an annotation that doesn't belong to the public API"() {
        given:
        validationEnabled()

        and:
        def api = toApi(['com.acme'], [
            'com.acme.A': '''package com.acme;
import com.acme.internal.Ann;

public class A {
    @Ann public String foo;
}
''',
            'com.acme.internal.Ann': '''package com.acme.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Ann {}
'''
        ])

        when:
        def clazz = api.classes['com.acme.A']
        def ann = api.classes['com.acme.internal.Ann']
        def annotations = clazz.clazz.getDeclaredField('foo').annotations
        api.loadStub(clazz)

        then:
        api.belongsToAPI(clazz)
        !api.belongsToAPI(ann)
        annotations.size() == 1
        annotations[0].annotationType().name == 'com.acme.internal.Ann'
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "'public java.lang.String foo' is annotated with 'com.acme.internal.Ann' effectively exposing it in the public API but its package is not one of the allowed packages."
    }

    void "reports error if a method parameter is annotated with an annotation that doesn't belong to the public API"() {
        given:
        validationEnabled()

        and:
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
        def ann = api.classes['com.acme.internal.Ann']
        api.loadStub(clazz)

        then:
        api.belongsToAPI(clazz)
        !api.belongsToAPI(ann)
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "'public void foo(java.lang.String)' is annotated with 'com.acme.internal.Ann' effectively exposing it in the public API but its package is not one of the allowed packages."
    }

    void "cannot have a superclass which is not in the public API"() {
        given:
        validationEnabled()

        and:
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
        ex.message == "'com.acme.A' extends 'com.acme.internal.AImpl' and its package is not one of the allowed packages."
    }

    void "cannot have an interface which is not in the public API"() {
        given:
        validationEnabled()

        and:
        def api = toApi(['com.acme'], ['com.acme.A'             : """package com.acme;
import com.acme.internal.AInternal;

public class A implements AInternal {
}""",
                                       'com.acme.internal.AInternal': '''package com.acme.internal;
public interface AInternal {}

'''])

        when:
        def clazz = api.classes['com.acme.A']
        def internal = api.classes['com.acme.internal.AInternal']
        api.loadStub(clazz)

        then:
        api.belongsToAPI(clazz)
        !api.belongsToAPI(internal)
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "'com.acme.A' declares interface 'com.acme.internal.AInternal' and its package is not one of the allowed packages."
    }

    void "cannot have a superclass generic argument type which is not in the public API"() {
        given:
        validationEnabled()

        and:
        def api = toApi(['com.acme'], ['com.acme.A'             : """package com.acme;
import com.acme.internal.AImpl;
import java.util.ArrayList;

public class A extends ArrayList<AImpl> {
}""",
                                       'com.acme.internal.AImpl': '''package com.acme.internal;
public class AImpl {}

'''])

        when:
        def clazz = api.classes['com.acme.A']
        def internal = api.classes['com.acme.internal.AImpl']
        api.loadStub(clazz)

        then:
        api.belongsToAPI(clazz)
        !api.belongsToAPI(internal)
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "'com.acme.A' references disallowed API type 'com.acme.internal.AImpl' in superclass or interfaces."
    }

    void "cannot have an interface generic argument type which is not in the public API"() {
        given:
        validationEnabled()

        and:
        def api = toApi(['com.acme'], ['com.acme.A'             : """package com.acme;
import com.acme.internal.AImpl;
import java.util.List;

public interface A extends List<AImpl> {
}""",
                                       'com.acme.internal.AImpl': '''package com.acme.internal;
public class AImpl {}

'''])

        when:
        def clazz = api.classes['com.acme.A']
        def internal = api.classes['com.acme.internal.AImpl']
        api.loadStub(clazz)

        then:
        api.belongsToAPI(clazz)
        !api.belongsToAPI(internal)
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "'com.acme.A' references disallowed API type 'com.acme.internal.AImpl' in superclass or interfaces."
    }

    void "cannot have type in generic class signature which is not in the public API"() {
        given:
        validationEnabled()

        and:
        def api = toApi(['com.acme'], ['com.acme.A'             : """package com.acme;
import com.acme.internal.AImpl;
import java.util.List;

public interface A<T extends AImpl> extends List<T> {
}""",
                                       'com.acme.internal.AImpl': '''package com.acme.internal;
public class AImpl {}

'''])

        when:
        def clazz = api.classes['com.acme.A']
        def internal = api.classes['com.acme.internal.AImpl']
        api.loadStub(clazz)

        then:
        api.belongsToAPI(clazz)
        !api.belongsToAPI(internal)
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "'com.acme.A' references disallowed API type 'com.acme.internal.AImpl' in superclass or interfaces."
    }

    void "cannot have type in generic method return type signature which is not in the public API"() {
        given:
        validationEnabled()

        and:
        def api = toApi(['com.acme'], ['com.acme.A'             : """package com.acme;
import com.acme.internal.AImpl;
import java.util.List;

public interface A {
    List<? super AImpl> getImpls();
}""",
                                       'com.acme.internal.AImpl': '''package com.acme.internal;
public class AImpl {}

'''])

        when:
        def clazz = api.classes['com.acme.A']
        def internal = api.classes['com.acme.internal.AImpl']
        api.loadStub(clazz)

        then:
        api.belongsToAPI(clazz)
        !api.belongsToAPI(internal)
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "In public abstract java.util.List getImpls(), type com.acme.internal.AImpl is exposed in the public API but its package is not one of the allowed packages."
    }

    void "cannot have type in generic field type signature which is not in the public API"() {
        given:
        validationEnabled()

        and:
        def api = toApi(['com.acme'], ['com.acme.A'             : """package com.acme;
import com.acme.internal.AImpl;
import java.util.List;

public class A {
    public List<? super AImpl> impls;
}""",
                                       'com.acme.internal.AImpl': '''package com.acme.internal;
public class AImpl {}

'''])

        when:
        def clazz = api.classes['com.acme.A']
        def internal = api.classes['com.acme.internal.AImpl']
        api.loadStub(clazz)

        then:
        api.belongsToAPI(clazz)
        !api.belongsToAPI(internal)
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "Field 'public java.util.List impls' references disallowed API type 'com.acme.internal.AImpl'"
    }

    void "cannot have multiple types in generic field type signature which is not in the public API"() {
        given:
        validationEnabled()

        and:
        def api = toApi(['com.acme'], ['com.acme.A'             : """package com.acme;
import com.acme.internal.AImpl;
import com.acme.internal.AImpl2;
import java.util.Map;

public class A {
    public Map<? extends AImpl, ? super AImpl2> map;
}""",
                                       'com.acme.internal.AImpl': '''package com.acme.internal;
public class AImpl {}

''',
                                       'com.acme.internal.AImpl2': '''package com.acme.internal;
public class AImpl2 {}

'''])

        when:
        def clazz = api.classes['com.acme.A']
        def internal = api.classes['com.acme.internal.AImpl']
        def internal2 = api.classes['com.acme.internal.AImpl2']
        api.loadStub(clazz)

        then:
        api.belongsToAPI(clazz)
        !api.belongsToAPI(internal)
        !api.belongsToAPI(internal2)
        def ex = thrown(InvalidPublicAPIException)
        ex.message == """The following types are referenced in public java.util.Map map but their package is not one of the allowed packages:
   - com.acme.internal.AImpl
   - com.acme.internal.AImpl2
"""
    }
}
