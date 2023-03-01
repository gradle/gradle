/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.provider

import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import static org.gradle.api.internal.provider.ProviderTestUtil.withProducer
import static org.gradle.api.internal.provider.ProviderTestUtil.withValues

class DefaultProviderFactoryTest extends Specification implements ProviderAssertions {

    static final PROJECT = ProjectBuilder.builder().build()
    static final File TEST_FILE = PROJECT.file('someDir')

    def providerFactory = new DefaultProviderFactory()

    def "cannot create provider for null value"() {
        when:
        providerFactory.provider(null)

        then:
        def t = thrown(IllegalArgumentException)
        t.message == 'Value cannot be null'
    }

    def "can create provider for #type"() {
        when:
        def provider = providerFactory.provider({ value })

        then:
        provider
        provider.get() == value

        where:
        type      | value
        Boolean   | true
        Byte      | Byte.valueOf((byte) 0)
        Short     | Short.valueOf((short) 0)
        Integer   | Integer.valueOf(0)
        Long      | 4L
        Float     | 5.5f
        Double    | 6.6d
        Character | '\u1234'
        String    | 'hello'
        File      | TEST_FILE
    }

    def "can zip two providers"() {
        def big = withValues("big")
        def black = withValues("black")
        def cat = withValues("cat")

        when:
        def zipped = providerFactory.zip(
                providerFactory.zip(big, black) { s1, s2 -> "$s1 $s2" } ,
                cat) { s1, s2 -> "${s1.capitalize()} ${s2}"}

        then:
        zipped instanceof Provider
        zipped.get() == 'Big black cat'
    }

    def "can zip two providers and use null to remove the value"() {
        def not = providerFactory.provider { "not" }
        def important = providerFactory.provider { "important" }

        when:
        def zipped = providerFactory.zip(not, important) { s1, s2 -> null }

        then:
        zipped instanceof Provider
        !zipped.isPresent()
        zipped.getOrNull() == null
    }

    def "can zip two providers and use null to remove the value, and it is live"() {
        def provider = withValues("accepted", "accepted", "rejected", "rejected")
        def second = providerFactory.provider { "value" }

        when:
        def zipped = providerFactory.zip(provider, second) { s1, s2 ->
            s1 == "accepted" ? "$s1 $s2" : null
        }

        then:
        zipped instanceof Provider
        zipped.isPresent()
        zipped.get() == "accepted value"

        then:
        !zipped.isPresent()
        zipped.getOrNull() == null
    }

    def "can zip two providers of arbitrary types"() {
        def a = withValues("big")
        def b = withValues(123L)

        when:
        def zipped = providerFactory.zip(a, b) { x, y -> x.length() + y }

        then:
        zipped instanceof Provider
        zipped.get() == 126
    }

    def "zip tracks task dependencies"() {
        def task1 = Stub(Task)
        def a = withProducer(Integer, task1, 5)
        def task2 = Stub(Task)
        def b = withProducer(String, task2, "Hello")

        when:
        def zipped = providerFactory.zip(a, b) { i, s -> s.length() == i } as ProviderInternal<Boolean>

        then:
        assertHasProducer(zipped, task1, task2)
        zipped.get() == true
    }
}
