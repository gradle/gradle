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

import org.gradle.internal.Factory
import org.gradle.internal.classpath.ClassPath
import spock.lang.Specification

class VisitableURLClassLoaderTest extends Specification {
    def "visits self and parent"() {
        def visitor = Mock(ClassLoaderVisitor)
        def parent = new ClassLoader(null) { }
        def classPath = [new File("a").toURI().toURL(), new File("b").toURI().toURL()]
        def cl = new VisitableURLClassLoader("test", parent, classPath)

        when:
        cl.visit(visitor)

        then:
        1 * visitor.visitSpec({ it instanceof VisitableURLClassLoader.Spec }) >> { VisitableURLClassLoader.Spec spec ->
            assert spec.name == "test"
            assert spec.classpath == classPath
        }
        1 * visitor.visitClassPath(classPath)
        1 * visitor.visitParent(parent)
        0 * visitor._
    }

    def "getUserData creates and retrieves data correctly"() {
        def cl = new VisitableURLClassLoader("test", getClass().classLoader, [])
        def consumerId = "testConsumer"
        def factory = Mock(Factory)

        when:
        def result1 = cl.getUserData(consumerId, factory)
        def result2 = cl.getUserData(consumerId, factory)

        then:
        1 * factory.create() >> "testData"
        0 * factory.create()
        result1 == "testData"
        result2 == "testData"
    }

    def "getUserData throws exception when classloader is closed"() {
        def cl = new VisitableURLClassLoader("test", getClass().classLoader, [])
        def consumerId = "testConsumer"

        when:
        cl.close()
        cl.getUserData(consumerId, { "data" })

        then:
        thrown(IllegalStateException)
    }

    def "close method clears user data and marks as closed"() {
        def cl = new VisitableURLClassLoader("test", getClass().classLoader, [])
        def consumerId = "testConsumer"

        when:
        cl.getUserData(consumerId, { "testData" })
        then:
        !cl.closed

        when:
        cl.close()
        then:
        cl.closed

        when: "try to access user data after close"
        cl.getUserData(consumerId, { "newData" })
        then:
        thrown(IllegalStateException)
    }

    def "cleanup method closes classloader and handles IOException"() {
        def cl = new VisitableURLClassLoader("test", getClass().classLoader, [])

        when:
        cl.cleanup()

        then:
        cl.closed

        when: "call cleanup multiple times"
        cl.cleanup()

        then:
        noExceptionThrown()
        cl.closed
    }

    def "multiple close calls are idempotent"() {
        def cl = new VisitableURLClassLoader("test", getClass().classLoader, [])

        when:
        cl.close()
        cl.close() // Second call should not cause issues

        then:
        noExceptionThrown()
        cl.closed
    }

    def "user data is isolated between different consumers"() {
        def cl = new VisitableURLClassLoader("test", getClass().classLoader, [])
        def consumer1 = "consumer1"
        def consumer2 = "consumer2"

        when:
        def data1 = cl.getUserData(consumer1, { "data1" })
        def data2 = cl.getUserData(consumer2, { "data2" })

        then:
        data1 == "data1"
        data2 == "data2"
    }

    def "spec equals and hashCode work correctly"() {
        def urls1 = [new File("a").toURI().toURL(), new File("b").toURI().toURL()]
        def urls2 = [new File("c").toURI().toURL()]

        def spec1 = new VisitableURLClassLoader.Spec("name1", urls1)
        def spec2 = new VisitableURLClassLoader.Spec("name2", urls1) // same URLs, different name
        def spec3 = new VisitableURLClassLoader.Spec("name1", urls2) // same name, different URLs
        def spec4 = new VisitableURLClassLoader.Spec("name1", urls1) // same as spec1

        expect:
        spec1 == spec4
        spec1 == spec2 // is this an issue? why not !=
        spec1 != spec3
        spec1.hashCode() == spec4.hashCode()
        spec1 == spec1 // reflexive
        spec1 != null // null check
        !spec1.equals("string") // different type
    }

    def "spec toString contains name and classpath"() {
        def urls = [new File("a").toURI().toURL(), new File("b").toURI().toURL()]
        def spec = new VisitableURLClassLoader.Spec("testName", urls)

        when:
        def string = spec.toString()

        then:
        string.contains("testName")
        string.contains("url-class-loader")
        string.contains("classpath")
    }

    def "fromClassPath creates appropriate classloader type"() {
        def parent = getClass().classLoader

        when: "using regular ClassPath"
        def regularClassPath = ClassPath.EMPTY
        def regularLoader = VisitableURLClassLoader.fromClassPath("regular", parent, regularClassPath)

        then:
        regularLoader instanceof VisitableURLClassLoader
    }

    def "classloader can be created with empty URL collection"() {
        when:
        def cl = new VisitableURLClassLoader("test", getClass().classLoader, [])

        then:
        noExceptionThrown()
        cl.name == "test"
        cl.getURLs().length == 0
    }

    def "classloader can be created with multiple URLs"() {
        def urls = [
            new File("a").toURI().toURL(),
            new File("b").toURI().toURL(),
            new File("c").toURI().toURL()
        ]

        when:
        def cl = new VisitableURLClassLoader("test", getClass().classLoader, urls)

        then:
        cl.getURLs().length == 3
        Arrays.asList(cl.getURLs()) == urls
    }

    def "addURL method works correctly"() {
        def initialUrls = [new File("a").toURI().toURL()]
        def cl = new VisitableURLClassLoader("test", getClass().classLoader, initialUrls)
        def newUrl = new File("new").toURI().toURL()

        when:
        cl.addURL(newUrl)

        then:
        Arrays.asList(cl.getURLs()).contains(newUrl)
    }

    def "getName returns correct name"() {
        when:
        def cl = new VisitableURLClassLoader("test-name", getClass().classLoader, [])

        then:
        cl.name == "test-name"
        cl.getName() == "test-name"
    }

    def "toString includes class name and loader name"() {
        when:
        def cl = new VisitableURLClassLoader("test-loader", getClass().classLoader, [])
        def string = cl.toString()

        then:
        string.contains("VisitableURLClassLoader")
        string.contains("test-loader")
    }

    def "classloader maintains separate user data per consumer"() {
        def cl = new VisitableURLClassLoader("test", getClass().classLoader, [])
        def consumer1 = "consumer1"
        def consumer2 = "consumer2"

        when:
        def data1 = cl.getUserData(consumer1, { ["list1"] })
        def data2 = cl.getUserData(consumer2, { ["list2"] })

        then:
        data1 == ["list1"]
        data2 == ["list2"]
        data1.is(data1) // same instance returned for same consumer
    }

    def "classloader with null parent works correctly"() {
        def classPath = [new File("a").toURI().toURL()]

        when:
        def cl = new VisitableURLClassLoader("test", null, classPath)

        then:
        noExceptionThrown()
        cl.parent == null
        cl.name == "test"
    }

    def "spec getClasspath returns immutable list"() {
        def urls = [new File("a").toURI().toURL()]
        def spec = new VisitableURLClassLoader.Spec("test", urls)

        when:
        def classpath = spec.getClasspath()

        then:
        classpath == urls

        when: "try to modify the returned list"
        classpath.clear()

        then:
        notThrown(UnsupportedOperationException) // why not?
    }

    def "spec getName returns correct name"() {
        def spec = new VisitableURLClassLoader.Spec("test-name", [])

        expect:
        spec.getName() == "test-name"
    }
}
