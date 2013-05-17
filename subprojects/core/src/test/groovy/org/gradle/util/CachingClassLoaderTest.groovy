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

package org.gradle.util

import spock.lang.Specification

class CachingClassLoaderTest extends Specification {
    final parent = Mock(ClassLoader)
    final classLoader = new CachingClassLoader(parent)

    def "loads class once and caches result"() {
        when:
        def cl = classLoader.loadClass("someClass")

        then:
        cl == String.class

        and:
        1 * parent.loadClass("someClass", false) >> String.class
        0 * parent._

        when:
        cl = classLoader.loadClass("someClass")

        then:
        cl == String.class

        and:
        0 * parent._
    }

    def "caches missing classes"() {
        when:
        classLoader.loadClass("someClass")

        then:
        thrown(ClassNotFoundException)

        and:
        1 * parent.loadClass("someClass", false) >> { throw new ClassNotFoundException("broken") }
        0 * parent._

        when:
        classLoader.loadClass("someClass")

        then:
        thrown(ClassNotFoundException)

        and:
        0 * parent._
    }
}
