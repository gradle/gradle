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

package org.gradle.internal.classloader

import spock.lang.Specification

class CachingClassLoaderTest extends Specification {
    final parent = Mock(ClassLoader, useObjenesis: false)
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

    def "loads resources once and caches result"() {
        when:
        def res = classLoader.getResource("foo.txt")

        then:
        res == new URL("file:foo.txt")

        and:
        1 * parent.getResource("foo.txt") >> new URL("file:foo.txt")
        0 * parent._

        when:
        res = classLoader.getResource("foo.txt")

        then:
        res == new URL("file:foo.txt")

        and:
        0 * parent._
    }

    def "caches missing resources"() {
        when:
        def res = classLoader.getResource("foo.txt")

        then:
        res == null

        and:
        1 * parent.getResource("foo.txt") >> null
        0 * parent._

        when:
        res = classLoader.getResource("foo.txt")

        then:
        res == null

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

    def "visits self and parent"() {
        def visitor = Mock(ClassLoaderVisitor)

        when:
        classLoader.visit(visitor)

        then:
        1 * visitor.visitSpec({it instanceof CachingClassLoader.Spec})
        1 * visitor.visitParent(parent)
        0 * visitor._
    }

    def "equals and hashcode"() {
        def c1 = new URLClassLoader()
        def c2 = new URLClassLoader()

        expect:
        new CachingClassLoader(c1) == new CachingClassLoader(c1)
        new CachingClassLoader(c1).hashCode() == new CachingClassLoader(c1).hashCode()

        new CachingClassLoader(c1) != new CachingClassLoader(c2)
        new CachingClassLoader(c1).hashCode() != new CachingClassLoader(c2).hashCode()
    }
}
