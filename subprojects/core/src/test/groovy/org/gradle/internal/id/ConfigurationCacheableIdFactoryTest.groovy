/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.id

import spock.lang.Specification

class ConfigurationCacheableIdFactoryTest extends Specification {

    def "creating ids after loading is not allowed"() {
        def factory = new ConfigurationCacheableIdFactory()

        when:
        factory.idRecreated()
        factory.idRecreated()
        then:
        noExceptionThrown()

        when:
        factory.createId()
        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot create a new id after one has been loaded"

        // repeating creation still throws an exception
        when:
        factory.createId()
        then:
        def e2 = thrown(IllegalStateException)
        e2.message == "Cannot create a new id after one has been loaded"
    }

    def "creating ids again after loading is not allowed"() {
        def factory = new ConfigurationCacheableIdFactory()

        when:
        def id1 = factory.createId()
        def id2 = factory.createId()
        then:
        id1 != id2

        when:
        factory.idRecreated()
        factory.idRecreated()
        then:
        noExceptionThrown()

        when:
        factory.createId()
        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot create a new id after one has been loaded"
    }

}
