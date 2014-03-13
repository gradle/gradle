/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Transformer
import spock.lang.Specification

class SuppliersTest extends Specification {

    def "closeable is closed on success"() {
        given:
        def closeable = Mock(Closeable)
        def supplier = Suppliers.ofQuietlyClosed(Factories.constant(closeable))

        when:
        def result = supplier.supplyTo({ 1 } as Transformer)

        then:
        result == 1
        1 * closeable.close()
    }

    def "closeable is closed on failure"() {
        given:
        def closeable = Mock(Closeable)
        def supplier = Suppliers.ofQuietlyClosed(Factories.constant(closeable))

        when:
        supplier.supplyTo({ throw new IllegalStateException("!!") } as Transformer)

        then:
        def e = thrown IllegalStateException
        e.message == "!!"
        1 * closeable.close()
    }

    def "exceptions thrown during close are ignored"() {
        given:
        def closeable = Mock(Closeable)
        def supplier = Suppliers.ofQuietlyClosed(Factories.constant(closeable))
        1 * closeable.close() >> { throw new IllegalStateException("on close") }

        when:
        supplier.supplyTo({ throw new IllegalStateException("!!") } as Transformer)

        then:
        def e = thrown IllegalStateException
        e.message == "!!"
    }

    def "suppliers can transformed"() {
        when:
        def original = Suppliers.of(Factories.constant(1))
        def wrapped = Suppliers.wrap(original, new Transformer<Integer, Integer>() {
            Integer transform(Integer integer) {
                integer * 2
            }
        })

        then:
        4 == wrapped.supplyTo(new Transformer<Integer, Integer>() {
            Integer transform(Integer integer) {
                integer * 2
            }
        })
    }

}
