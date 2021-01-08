/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.compile

import spock.lang.Specification
import org.codehaus.groovy.transform.GroovyASTTransformationClass
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.DefaultClassPath

class GroovyCompileTransformingClassLoaderTest extends Specification {
    GroovyCompileTransformingClassLoader loader
    Class<?> classAnnotation

    def setup() {
        def classPath = DefaultClassPath.of(ClasspathUtil.getClasspathForClass(getClass()), ClasspathUtil.getClasspathForClass(GroovyASTTransformationClass))
        loader = new GroovyCompileTransformingClassLoader(null, classPath)
        classAnnotation = loader.loadClass(GroovyASTTransformationClass.name)
    }

    def "loads class annotated with transformer name"() {
        expect:
        def cl = loader.loadClass(WithNameSpecified.name)
        def annotation = cl.getAnnotation(classAnnotation)
        annotation.value() == ['some-type'] as String[]
        annotation.classes() == [] as Class[]
    }

    def "loads class annotated with transformer names"() {
        expect:
        def cl = loader.loadClass(WithNamesSpecified.name)
        def annotation = cl.getAnnotation(classAnnotation)
        annotation.value() == ['some-type', 'some-other-type'] as String[]
        annotation.classes() == [] as Class[]
    }

    def "loads class annotated with transformer class"() {
        expect:
        def cl = loader.loadClass(WithClassSpecified.name)
        def annotation = cl.getAnnotation(classAnnotation)
        annotation.value() == [Transformer.name] as String[]
        annotation.classes() == [] as Class[]
    }

    def "loads class annotated with transformer classes"() {
        expect:
        def cl = loader.loadClass(WithClassesSpecified.name)
        def annotation = cl.getAnnotation(classAnnotation)
        annotation.value() == [Transformer.name, Runnable.name] as String[]
        annotation.classes() == [] as Class[]
    }

    def "loads class annotated with transformer names and classes"() {
        expect:
        def cl = loader.loadClass(WithBothSpecified.name)
        def annotation = cl.getAnnotation(classAnnotation)
        annotation.value() as Set == ["some-type", Transformer.name, Runnable.name] as Set
        annotation.classes() == [] as Class[]
    }

    static class Transformer {
    }
}

@GroovyASTTransformationClass("some-type")
@interface WithNameSpecified {
}

@GroovyASTTransformationClass(["some-type", "some-other-type"])
@interface WithNamesSpecified {
}

@GroovyASTTransformationClass(classes = [GroovyCompileTransformingClassLoaderTest.Transformer])
@interface WithClassSpecified {
}

@GroovyASTTransformationClass(classes = [GroovyCompileTransformingClassLoaderTest.Transformer, Runnable])
@interface WithClassesSpecified {
}

@GroovyASTTransformationClass(value = "some-type", classes = [GroovyCompileTransformingClassLoaderTest.Transformer, Runnable])
@interface WithBothSpecified {
}
