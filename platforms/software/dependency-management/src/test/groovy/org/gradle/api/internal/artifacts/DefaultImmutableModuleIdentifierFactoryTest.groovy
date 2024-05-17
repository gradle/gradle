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

package org.gradle.api.internal.artifacts

import spock.lang.Specification

class DefaultImmutableModuleIdentifierFactoryTest extends Specification {
    def factory = new DefaultImmutableModuleIdentifierFactory()

    def "creates module"() {
        when:
        def m = factory.module('foo', 'bar')

        then:
        m.group == 'foo'
        m.name == 'bar'
    }

    def "caches module ids"() {
        when:
        def m1 = factory.module('foo', 'bar')
        def m2 = factory.module('foo', 'bar')

        then:
        m1.is(m2)
    }

    def "caches modules with versions"() {
        when:
        def m1 = factory.moduleWithVersion('foo', 'bar', '1.0')
        def m2 = factory.moduleWithVersion('foo', 'bar', '1.0')

        then:
        m1.is(m2)
    }

    def "reuses the cached module when building a module with version"() {
        when:
        def m1 = factory.module('foo', 'bar')
        def m2 = factory.moduleWithVersion('foo', 'bar', '1.0')

        then:
        m2.module.is(m1)
    }
}
