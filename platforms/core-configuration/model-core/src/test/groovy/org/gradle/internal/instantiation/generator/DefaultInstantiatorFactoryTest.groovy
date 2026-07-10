/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.instantiation.generator

import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.instantiation.InjectAnnotationHandler
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler
import spock.lang.Specification

import javax.inject.Inject
import java.lang.annotation.Annotation
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy


class DefaultInstantiatorFactoryTest extends Specification {
    def instantiatorFactory = new DefaultInstantiatorFactory(new TestCrossBuildInMemoryCacheFactory(), [handler(Annotation1), handler(Annotation2)], Stub(PropertyRoleAnnotationHandler))

    def "creates scheme with requested annotations"() {
        expect:
        instantiatorFactory.injectScheme([Annotation1]).injectionAnnotations == [Annotation1, Inject] as Set
    }

    def "uses Instantiators from inject schemes"() {
        expect:
        instantiatorFactory.injectScheme().instantiator().is(instantiatorFactory.inject())
        instantiatorFactory.injectScheme([]).instantiator().is(instantiatorFactory.inject())
        !instantiatorFactory.decorateScheme().instantiator().is(instantiatorFactory.inject())
    }

    def "fails for unknown annotation"() {
        when:
        instantiatorFactory.injectScheme([Annotation3])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Annotation @Annotation3 is not a registered injection annotation.'
    }

    def "detects properties injected by annotation"() {
        def scheme = instantiatorFactory.injectScheme([Annotation1, Annotation2])
        when:
        def instanceFactory = scheme.forType(UsesAnnotation1ForInjection)
        then:
        instanceFactory.serviceInjectionTriggeredByAnnotation(Annotation1)
        !instanceFactory.serviceInjectionTriggeredByAnnotation(Annotation2)

        when:
        instanceFactory = scheme.forType(UsesAnnotationsForInjection)
        then:
        instanceFactory.serviceInjectionTriggeredByAnnotation(Annotation1)
        instanceFactory.serviceInjectionTriggeredByAnnotation(Annotation2)
    }

    def handler(Class<? extends Annotation> annotation) {
        InjectAnnotationHandler handler = Stub(InjectAnnotationHandler)
        handler.annotationType >> annotation
        return handler
    }
}

@Retention(RetentionPolicy.RUNTIME)
@interface Annotation1 {
}

@Retention(RetentionPolicy.RUNTIME)
@interface Annotation2 {
}

@Retention(RetentionPolicy.RUNTIME)
@interface Annotation3 {
}

interface UsesAnnotation1ForInjection {
    @Annotation1
    String getPropertyOne()
    @Annotation1
    Integer getPropertyTwo()
}

interface UsesAnnotationsForInjection {
    @Annotation1
    String getPropertyOne()
    @Annotation2
    Integer getPropertyTwo()
}
