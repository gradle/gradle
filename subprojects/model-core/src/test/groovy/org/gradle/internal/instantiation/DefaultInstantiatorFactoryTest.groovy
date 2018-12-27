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


class DefaultInstantiatorFactoryTest extends Specification {
    def instantiatorFactory = new DefaultInstantiatorFactory(new TestCrossBuildInMemoryCacheFactory())

    def "caches InstantiationScheme instances"() {
        def classes = [Annotation1] as Set

        expect:
        instantiatorFactory.injectScheme(classes).is(instantiatorFactory.injectScheme(classes))
        !instantiatorFactory.injectScheme(classes).is(instantiatorFactory.injectScheme([] as Set))
        !instantiatorFactory.injectScheme(classes).is(instantiatorFactory.injectScheme([Annotation1, Annotation2] as Set))
    }

    def "uses Instantiator from inject scheme"() {
        expect:
        instantiatorFactory.injectScheme([] as Set).instantiator().is(instantiatorFactory.inject())
    }

}

@interface Annotation1 {
}

@interface Annotation2 {
}
