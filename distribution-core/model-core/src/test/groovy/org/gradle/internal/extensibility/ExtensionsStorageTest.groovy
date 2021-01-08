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

package org.gradle.internal.extensibility

import org.gradle.api.Action
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.reflect.TypeOf
import spock.lang.Specification

import static org.gradle.api.reflect.TypeOf.typeOf

class ExtensionsStorageTest extends Specification {
    def storage = new ExtensionsStorage()
    def listExtension = Mock(List)
    def setExtension = Mock(Set)

    def "setup"() {
        storage.add(typeOf(List), "list", listExtension)
        storage.add(typeOf(Set), "set", setExtension)
    }

    def "has extension"() {
        expect:
        storage.hasExtension("list")
        !storage.hasExtension("not")
    }

    def "get extension"() {
        when:
        def list = storage.getByName("list")
        def set = storage.getByType(typeOf(Set))

        then:
        list == listExtension
        set == setExtension

        when:
        storage.getByName("foo")

        then:
        thrown UnknownDomainObjectException

        when:
        storage.getByType(typeOf(String))

        then:
        thrown UnknownDomainObjectException
    }

    def "configure extension"() {
        when:
        def shouldNotExist = "shouldNotExist"
        storage.configureExtension(shouldNotExist, {})
        then:
        def t = thrown UnknownDomainObjectException
        t.message.startsWith("Extension with name '$shouldNotExist' does not exist.")
    }

    def "find extension"() {
        when:
        def list = storage.findByName("list")
        def set = storage.findByType(typeOf(Set))
        def foo = storage.findByName("foo")
        def string = storage.findByType(typeOf(String))

        then:
        list == listExtension
        set == setExtension
        foo == null
        string == null
    }

    def "get as map"() {
        expect:
        storage.getAsMap() == ["list": listExtension, "set": setExtension]
    }

    def "configures regular extension"() {
        when:
        def extension = Mock(TestExtension)
        storage.add(typeOf(TestExtension), "ext", extension)

        and:
        storage.configureExtension("ext", {
            it.call(1)
        })
        storage.configureExtension(typeOf(TestExtension), new Action<TestExtension>() {
            void execute(TestExtension t) {
                t.call(2)
            }
        })

        then:
        extension.call(1)
        extension.call(2)

        when:
        def val = storage.getByName("ext")

        then:
        val == extension
    }

    static interface TestExtension {
        void call(value);
    }

    def "favor exact same type over assignable"() {
        given:
        storage.add typeOf(Integer), 'int', 23
        storage.add typeOf(Number), 'num', 42
        storage.add new TypeOf<List<String>>() {}, 'stringList', ['string']

        expect:
        storage.findByType(typeOf(Number)) == 42
        storage.findByType(new TypeOf<List<String>>() {}) == ['string']
    }

    def "get schema"() {
        given:
        storage.add new TypeOf<List<String>>() {}, 'stringList', ['string']
        storage.add new TypeOf<TestExtension>() {}, 'testExtension', Stub(TestExtension)

        when:
        def schema = storage.schema

        then:
        schema.collect { [name: it.name, type: it.publicType] } == [
            [name: 'list', type: typeOf(List)],
            [name: 'set', type: typeOf(Set)],
            [name: 'stringList', type: new TypeOf<List<String>>() {}],
            [name: 'testExtension', type: new TypeOf<TestExtension>() {}]
        ]
    }

    def "only considers public type when addressing extensions by type"() {
        given:
        Integer number = 23
        storage.add typeOf(Number), 'number', number

        expect:
        storage.findByType(typeOf(Integer)) == null
        storage.findByType(typeOf(Number)) == number
        storage.getByType(typeOf(Number)) == number
    }
}
