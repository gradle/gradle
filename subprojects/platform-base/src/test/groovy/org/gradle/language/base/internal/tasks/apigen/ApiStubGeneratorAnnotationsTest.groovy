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

@Requires(TestPrecondition.JDK6_OR_LATER)
class ApiStubGeneratorAnnotationsTest extends ApiStubGeneratorTestSupport {

    void "annotations on class are retained"() {
        given:
        def api = toApi([
            A  : '@Ann public class A {}',
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
        def clazz = api.classes.A
        def annotations = clazz.clazz.annotations
        def stubbed = api.loadStub(clazz)
        def annClazz = api.classes.Ann
        def stubbedAnn = api.loadStub(annClazz)
        def stubbedAnnotations = stubbed.annotations

        then:
        api.belongsToAPI(clazz)
        api.belongsToAPI(annClazz)
        annotations.size() == 1
        annotations[0].annotationType().name == 'Ann'
        stubbedAnnotations.size() == 1
        stubbedAnnotations[0].annotationType() == stubbedAnn
    }

    void "annotations on method are retained"() {
        given:
        def api = toApi([
            A  : '''public class A {
    @Ann
    public void foo() {}
}''',
            Ann: '''import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Ann {}
'''
        ])

        when:
        def clazz = api.classes.A
        def annotations = clazz.clazz.getDeclaredMethod('foo').annotations
        def stubbed = api.loadStub(clazz)
        def annClazz = api.classes.Ann
        def stubbedAnn = api.loadStub(annClazz)
        def stubbedAnnotations = stubbed.getDeclaredMethod('foo').annotations

        then:
        api.belongsToAPI(clazz)
        api.belongsToAPI(annClazz)
        annotations.size() == 1
        annotations[0].annotationType().name == 'Ann'
        stubbedAnnotations.size() == 1
        stubbedAnnotations[0].annotationType() == stubbedAnn
    }

    void "annotation value is retained"() {
        given:
        def api = toApi([
            A     : '''
            @Ann(@SubAnn("foo"))
            public class A {}''',
            Ann   : '''import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Ann {
    SubAnn value();
}
''',
            SubAnn: '''import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface SubAnn {
    String value();
}
'''
        ])

        when:
        def clazz = api.classes.A
        def annotations = clazz.clazz.annotations
        def annClazz = api.classes.Ann
        def subAnnClazz = api.classes.SubAnn

        def stubbedSubAnn = api.loadStub(subAnnClazz)
        def stubbedAnn = api.loadStub(annClazz)
        def stubbed = api.loadStub(clazz)
        def stubbedAnnotations = stubbed.annotations

        then:
        api.belongsToAPI(clazz)
        api.belongsToAPI(annClazz)
        annotations.size() == 1
        annotations[0].annotationType().name == 'Ann'
        stubbedAnnotations.size() == 1
        def annotation = stubbedAnnotations[0]
        annotation.annotationType() == stubbedAnn
        def subAnnotation = annotation.value()
        subAnnotation.annotationType().name == 'SubAnn'
        subAnnotation.value() == 'foo'

    }

    void "annotation arrays on class are retained"() {
        given:
        def api = toApi([
            A     : '''
            @Ann({@SubAnn("foo"), @SubAnn("bar")})
            public class A {}''',
            Ann   : '''import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Ann {
    SubAnn[] value();
}
''',
            SubAnn: '''import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface SubAnn {
    String value();
}
'''
        ])

        when:
        def clazz = api.classes.A
        def annotations = clazz.clazz.annotations
        def annClazz = api.classes.Ann
        def subAnnClazz = api.classes.SubAnn

        def stubbedSubAnn = api.loadStub(subAnnClazz)
        def stubbedAnn = api.loadStub(annClazz)
        def stubbed = api.loadStub(clazz)
        def stubbedAnnotations = stubbed.annotations

        then:
        api.belongsToAPI(clazz)
        api.belongsToAPI(annClazz)
        annotations.size() == 1
        annotations[0].annotationType().name == 'Ann'
        stubbedAnnotations.size() == 1
        def annotation = stubbedAnnotations[0]
        annotation.annotationType() == stubbedAnn
        def subAnnotations = annotation.value()
        subAnnotations.length == 2
        subAnnotations.collect { it.value() } == ['foo', 'bar']

    }

}
