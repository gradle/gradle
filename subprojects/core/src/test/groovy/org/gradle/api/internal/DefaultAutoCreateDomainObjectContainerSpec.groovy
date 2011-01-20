/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal

import spock.lang.Specification
import org.gradle.api.NamedDomainObjectFactory

class DefaultAutoCreateDomainObjectContainerSpec extends Specification {
    final NamedDomainObjectFactory<String> factory = Mock()
    final ClassGenerator classGenerator = Mock()

    def usesFactoryToCreateContainerElements() {
        def container = new DefaultAutoCreateDomainObjectContainer<String>(String.class, classGenerator, factory)

        when:
        def result = container.add('a')

        then:
        result == 'element a'
        1 * factory.create('a') >> 'element a'
        0 * _._
    }

    def usesPublicConstructorWhenNoFactorySupplied() {
        def container = new DefaultAutoCreateDomainObjectContainer<String>(String.class, classGenerator)

        when:
        def result = container.add('a')

        then:
        result == 'a'
        0 * _._
    }

    def usesClosureToCreateContainerElements() {
        def cl = { name -> "element $name" as String }
        def container = new DefaultAutoCreateDomainObjectContainer<String>(String.class, classGenerator, cl)

        when:
        def result = container.add('a')

        then:
        result == 'element a'
        0 * _._
    }
}
