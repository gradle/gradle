/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.instantiation

import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import spock.lang.Specification

import java.lang.annotation.Annotation


class DefaultInstantiatorFactoryTest extends Specification {
    def instantiatorFactory = new DefaultInstantiatorFactory(new TestCrossBuildInMemoryCacheFactory(), [handler(Annotation1), handler(Annotation2)])

    def "caches InstantiationScheme instances"() {
        def classes = [Annotation1]

        expect:
        instantiatorFactory.injectScheme(classes).is(instantiatorFactory.injectScheme(classes))
        instantiatorFactory.injectScheme(classes as Set).is(instantiatorFactory.injectScheme(classes))
        instantiatorFactory.injectScheme([Annotation1, Annotation1]).is(instantiatorFactory.injectScheme(classes))

        !instantiatorFactory.injectScheme(classes).is(instantiatorFactory.injectScheme([]))
        !instantiatorFactory.injectScheme(classes).is(instantiatorFactory.injectScheme([Annotation1, Annotation2]))

        instantiatorFactory.injectScheme([Annotation2, Annotation1]).is(instantiatorFactory.injectScheme([Annotation1, Annotation2]))
    }

    def "uses Instantiator from inject scheme"() {
        expect:
        instantiatorFactory.injectScheme([]).instantiator().is(instantiatorFactory.inject())
    }

    def "fails for unknown annotation"() {
        when:
        instantiatorFactory.injectScheme([Annotation3])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Annotation @Annotation3 is not a registered injection annotation.'
    }

    def handler(Class<? extends Annotation> annotation) {
        InjectAnnotationHandler handler = Stub(InjectAnnotationHandler)
        handler.annotation >> annotation
        return handler
    }
}

@interface Annotation1 {
}

@interface Annotation2 {
}

@interface Annotation3 {
}
