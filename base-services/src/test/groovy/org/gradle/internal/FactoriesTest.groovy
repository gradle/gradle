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
package org.gradle.internal

import spock.lang.Specification

class FactoriesTest extends Specification {
    def "factory runs given runnable and returns null"() {
        Runnable r = Mock()
        Factory<String> factory = Factories.toFactory(r)

        when:
        def result = factory.create()

        then:
        result == null

        and:
        1 * r.run()
        0 * r._
    }

    def "factory gets cached"() {
        given:
        Factory factory = Mock()
        Factory cachedFactory = Factories.softReferenceCache(factory)

        when:
        def value = cachedFactory.create()

        then:
        1 * factory.create() >> 123
        value == 123

        when:
        value = cachedFactory.create()

        then:
        0 * _._
        value == 123
    }
}
