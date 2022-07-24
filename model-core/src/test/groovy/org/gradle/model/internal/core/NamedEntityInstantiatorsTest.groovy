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

package org.gradle.model.internal.core

import spock.lang.Specification

class NamedEntityInstantiatorsTest extends Specification {

    static class Base {}
    static class NonSubtype {}
    static class NonSubtypeChild extends NonSubtype {}


    def "non subtype instantiator always throws"() {
        given:
        def instantiator = NamedEntityInstantiators.nonSubtype(NonSubtype, Base)

        when:
        instantiator.create("foo", NonSubtypeChild)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot create an item of type ${NonSubtypeChild.name} as this is not a subtype of ${Base.name}."
    }
}
