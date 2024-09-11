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

import java.lang.annotation.ElementType
import java.lang.reflect.Method

class ApiClassExtractorAnnotationsTest extends ApiClassExtractorTestSupport {

    void "annotations on class are retained"() {
        given:
        def api = toApi([
            A  : '@Ann public class A {}',
            Ann: '''
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
        def clazz = api.classes.A
        def annotations = clazz.clazz.annotations
        def extractedClass = api.extractAndLoadApiClassFrom(clazz)
        def annClazz = api.classes.Ann
        def extractedAnn = api.extractAndLoadApiClassFrom(annClazz)
        def extractedAnnotations = extractedClass.annotations

        then:
        annotations.size() == 1
        annotations[0].annotationType().name == 'Ann'
        extractedAnnotations.size() == 1
        extractedAnnotations[0].annotationType() == extractedAnn
    }

    void "annotations on method are retained"() {
        given:
        def api = toApi([
            A  : '''
                public class A {
                    @Ann
                    public void foo() {}
                }
            ''',
            Ann: '''
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
        def clazz = api.classes.A
        def annotations = clazz.clazz.getDeclaredMethod('foo').annotations
        def extractedClass = api.extractAndLoadApiClassFrom(clazz)
        def annClazz = api.classes.Ann
        def extractedAnn = api.extractAndLoadApiClassFrom(annClazz)
        def extractedAnnotations = extractedClass.getDeclaredMethod('foo').annotations

        then:
        annotations.size() == 1
        annotations[0].annotationType().name == 'Ann'
        extractedAnnotations.size() == 1
        extractedAnnotations[0].annotationType() == extractedAnn
    }

    void "annotations on method params are retained"() {
        given:
        def api = toApi([
            A  : '''
                public class A {
                    public void foo(@Ann(path="somePath") String foo) {}
                }
            ''',
            Ann: '''
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.PARAMETER})
                public @interface Ann {
                    String path();
                }
            '''
        ])

        when:
        def clazz = api.classes.A
        def annotations = clazz.clazz.getDeclaredMethod('foo', String).parameterAnnotations[0]
        def extractedClass = api.extractAndLoadApiClassFrom(clazz)
        def annClazz = api.classes.Ann
        def extractedAnn = api.extractAndLoadApiClassFrom(annClazz)
        def extractedAnnotations = extractedClass.getDeclaredMethod('foo', String).parameterAnnotations[0]

        then:
        annotations.size() == 1
        annotations[0].annotationType().name == 'Ann'
        extractedAnnotations.size() == 1
        extractedAnnotations[0].annotationType() == extractedAnn
        extractedAnnotations[0].path() == 'somePath'
    }

    void "annotations on field are retained"() {
        given:
        def api = toApi([
            A  : '''
                public class A {
                    @Ann(a="b")
                    public String foo;
                }
            ''',
            Ann: '''
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.FIELD})
                public @interface Ann {
                    String a();
                }
            '''
        ])

        when:
        def clazz = api.classes.A
        def annotations = clazz.clazz.getDeclaredField('foo').annotations
        def extractedClass = api.extractAndLoadApiClassFrom(clazz)
        def annClazz = api.classes.Ann
        def extractedAnn = api.extractAndLoadApiClassFrom(annClazz)
        def extractedAnnotations = extractedClass.getDeclaredField('foo').annotations

        then:
        annotations.size() == 1
        annotations[0].annotationType().name == 'Ann'
        extractedAnnotations.size() == 1
        extractedAnnotations[0].annotationType() == extractedAnn
        extractedAnnotations[0].a() == 'b'
    }

    void "annotation value is retained"() {
        given:
        def api = toApi([
            A     : '''
                @Ann(@SubAnn("foo"))
                public class A {}
            ''',
            Ann   : '''
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE})
                public @interface Ann {
                    SubAnn value();
                }
            ''',
            SubAnn: '''
                import java.lang.annotation.ElementType;
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

        api.extractAndLoadApiClassFrom(subAnnClazz)
        def extractedAnn = api.extractAndLoadApiClassFrom(annClazz)
        def extractedClass = api.extractAndLoadApiClassFrom(clazz)
        def extractedAnnotations = extractedClass.annotations

        then:
        annotations.size() == 1
        annotations[0].annotationType().name == 'Ann'
        extractedAnnotations.size() == 1
        def annotation = extractedAnnotations[0]
        annotation.annotationType() == extractedAnn
        def subAnnotation = annotation.value()
        subAnnotation.annotationType().name == 'SubAnn'
        subAnnotation.value() == 'foo'
    }

    void "annotation arrays on class are retained"() {
        given:
        def api = toApi([
            A     : '''
                @Ann({@SubAnn("foo"), @SubAnn("bar")})
                public class A {}
            ''',
            Ann   : '''
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE})
                public @interface Ann {
                    SubAnn[] value();
                }
            ''',
            SubAnn: '''
                import java.lang.annotation.ElementType;
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

        api.extractAndLoadApiClassFrom(subAnnClazz)
        def extractedAnn = api.extractAndLoadApiClassFrom(annClazz)
        def extractedClass = api.extractAndLoadApiClassFrom(clazz)
        def extractedAnnotations = extractedClass.annotations

        then:
        annotations.size() == 1
        annotations[0].annotationType().name == 'Ann'
        extractedAnnotations.size() == 1
        def annotation = extractedAnnotations[0]
        annotation.annotationType() == extractedAnn
        def subAnnotations = annotation.value()
        subAnnotations.length == 2
        subAnnotations.collect { it.value() } == ['foo', 'bar']
    }

    void "annotation arrays of String on class are retained"() {
        given:
        def api = toApi([
            A     : '''
                @Ann(names={"foo", "bar"})
                public class A {}
            ''',
            Ann   : '''
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE})
                public @interface Ann {
                    String[] names();
                }
            '''
        ])

        when:
        def clazz = api.classes.A
        def annotations = clazz.clazz.annotations
        def annClazz = api.classes.Ann

        def extractedAnn = api.extractAndLoadApiClassFrom(annClazz)
        def extractedClass = api.extractAndLoadApiClassFrom(clazz)
        def extractedAnnotations = extractedClass.annotations

        then:
        annotations.size() == 1
        annotations[0].annotationType().name == 'Ann'
        extractedAnnotations.size() == 1
        def annotation = extractedAnnotations[0]
        annotation.annotationType() == extractedAnn
        def stringValues = annotation.names()
        stringValues == ['foo', 'bar']
    }

    void "default values for annotations are retained"() {
        given:
        def api = toApi([
            A  : '@Ann public class A {}',
            Ann: '''
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE})
                @interface Ann {
                    int[] arr() default {};
                    Deprecated child() default @Deprecated(since = "1.5");
                    ElementType enum0() default ElementType.TYPE;
                    String name() default "name";
                    Class<?> type() default Integer.class;
                    int value() default 42;
                }
            '''
        ])

        when:
        def clazz = api.classes.A
        def annotations = clazz.clazz.annotations
        def extractedClass = api.extractAndLoadApiClassFrom(clazz)
        def annClazz = api.classes.Ann
        def extractedAnn = api.extractAndLoadApiClassFrom(annClazz)
        def extractedAnnotations = extractedClass.annotations

        then:
        annotations.size() == 1
        def annotation = annotations[0]
        annotation.annotationType().name == 'Ann'
        def methods = mapMethods(annotation.annotationType().methods)
        methods["arr"].defaultValue == []
        annotation.arr() == []
        methods["child"].defaultValue.annotationType() == Deprecated
        methods["child"].defaultValue.since() == "1.5"
        annotation.child().annotationType() == Deprecated
        annotation.child().since() == "1.5"
        methods["child"].defaultValue
        methods["enum0"].defaultValue == ElementType.TYPE
        annotation.enum0() == ElementType.TYPE
        methods["type"].defaultValue == Integer
        annotation.type() == Integer
        methods["value"].defaultValue == 42
        annotation.value() == 42

        extractedAnnotations.size() == 1
        def extractedAnnotation = extractedAnnotations[0]
        extractedAnnotation.annotationType() == extractedAnn
        def extractedMethods = mapMethods(extractedAnnotation.annotationType().methods)
        extractedMethods["arr"].defaultValue == []
        extractedAnnotation.arr() == []
        extractedMethods["child"].defaultValue.annotationType() == Deprecated
        extractedMethods["child"].defaultValue.since() == "1.5"
        extractedMethods["enum0"].defaultValue == ElementType.TYPE
        extractedAnnotation.enum0() == ElementType.TYPE
        extractedMethods["type"].defaultValue == Integer
        extractedAnnotation.type() == Integer
        extractedMethods["value"].defaultValue == 42
        extractedAnnotation.value() == 42
    }

    private static Map<String, Method> mapMethods(Method[] methods) {
        methods.collectEntries { [it.name, it] }
    }
}
