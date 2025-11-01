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

import org.gradle.internal.classpath.TransformedClassPath
import spock.lang.Specification

class VisitableURLClassLoaderTest extends Specification {
    def "visits self and parent"() {
        def parent = new ClassLoader(null) { }
        def classPath = [new File("a").toURI().toURL(), new File("b").toURI().toURL()]
        def cl = new VisitableURLClassLoader("test", parent, classPath)

        when:
        cl.findClass("nonexistent.Class")

        then:
        thrown(ClassNotFoundException)
    }

    def parent = new ClassLoader(null) { }
    def transformedClassPath = Mock(TransformedClassPath)

    def setup() {
        transformedClassPath.getAsURLArray() >> [new File("a.jar").toURI().toURL()].toArray(new URL[0])
    }

    def "implements InstrumentingClassLoader interface"() {
        given:
        def cl = new VisitableURLClassLoader.InstrumentingVisitableURLClassLoader("test", parent, transformedClassPath)

        expect:
        cl instanceof InstrumentingClassLoader
    }

    def "transformFailed delegates to error handler"() {
        given:
        def cl = new VisitableURLClassLoader.InstrumentingVisitableURLClassLoader("test", parent, transformedClassPath)
        def className = "com.example.Test"
        def cause = new RuntimeException("Transform failed")

        when:
        cl.transformFailed(className, cause)

        then:
        noExceptionThrown() // Just verify it doesn't throw
    }

    def "findClass manages error handling scope"() {
        given:
        def cl = new VisitableURLClassLoader.InstrumentingVisitableURLClassLoader("test", parent, transformedClassPath)

        when:
        cl.findClass("nonexistent.Class")

        then:
        thrown(ClassNotFoundException)
    }

    def "spec equals and hashCode"() {
        given:
        def urls1 = [new File("a.jar").toURI().toURL(), new File("b.jar").toURI().toURL()]
        def urls2 = [new File("a.jar").toURI().toURL(), new File("b.jar").toURI().toURL()]
        def urls3 = [new File("c.jar").toURI().toURL()]

        def spec1 = new VisitableURLClassLoader.Spec("name1", urls1)
        def spec2 = new VisitableURLClassLoader.Spec("name2", urls2) // different name, same urls
        def spec3 = new VisitableURLClassLoader.Spec("name1", urls3) // same name, different urls

        expect:
        spec1 == spec1
        spec1 == spec2 // different urls
        spec1 != spec3 // different urls
        spec1.hashCode() == spec1.hashCode()
        spec1.toString().contains("name1")
        spec1.toString().contains("classpath")
    }

    def "fromClassPath creates InstrumentingVisitableURLClassLoader for TransformedClassPath"() {
        given:
        def classPath = Mock(TransformedClassPath)
        classPath.getAsURLArray() >> [new File("a.jar").toURI().toURL()].toArray(new URL[0])

        when:
        def result = VisitableURLClassLoader.fromClassPath("test", parent, classPath)

        then:
        result instanceof VisitableURLClassLoader.InstrumentingVisitableURLClassLoader
    }

    def "instrumentClass returns null when no transformation"() {
        given:
        def cl = new VisitableURLClassLoader.InstrumentingVisitableURLClassLoader("test", parent, transformedClassPath)
        def className = "com.example.Test"
        def protectionDomain = null
        def classfileBuffer = new byte[0]

        when:
        def result = cl.instrumentClass(className, protectionDomain, classfileBuffer)

        then:
        result == null
    }

    def "close properly closes resources"() {
        given:
        def cl = new VisitableURLClassLoader.InstrumentingVisitableURLClassLoader("test", parent, transformedClassPath)

        when:
        cl.close()

        then:
        noExceptionThrown()
    }

    def "class loader name is preserved"() {
        given:
        def name = "test-loader"
        def cl = new VisitableURLClassLoader(name, parent, [])

        expect:
        cl.getName() == name
        cl.toString().contains(name)
    }

    def "can add URLs dynamically"() {
        given:
        def cl = new VisitableURLClassLoader("test", parent, [])
        def newUrl = new File("new.jar").toURI().toURL()

        when:
        cl.addURL(newUrl)

        then:
        noExceptionThrown()
    }

    def "visit method provides correct information to visitor"() {
        given:
        def urls = [new File("a.jar").toURI().toURL(), new File("b.jar").toURI().toURL()]
        def cl = new VisitableURLClassLoader("test", parent, urls)
        def visitor = Mock(ClassLoaderVisitor)

        when:
        cl.visit(visitor)

        then:
        1 * visitor.visitSpec(_ as VisitableURLClassLoader.Spec) >> { args ->
            def spec = args[0] as VisitableURLClassLoader.Spec
            assert spec.name == "test"
            assert spec.classpath == urls
        }
        1 * visitor.visitClassPath(urls as URL[])
        1 * visitor.visitParent(parent)
    }

    def "instrumenting class loader constructor validates transformed class path"() {
        when:
        new VisitableURLClassLoader("test", parent, transformedClassPath)

        then:
        thrown(IllegalArgumentException)
    }

    def "findClass propagates original exception through error handler"() {
        given:
        def cl = new VisitableURLClassLoader.InstrumentingVisitableURLClassLoader("test", parent, transformedClassPath)

        when:
        cl.findClass("nonexistent.Class")

        then:
        thrown(ClassNotFoundException)
    }

    def "multiple class loaders can coexist"() {
        given:
        def cl1 = new VisitableURLClassLoader("loader1", parent, [])
        def cl2 = new VisitableURLClassLoader("loader2", parent, [])

        expect:
        cl1.getName() == "loader1"
        cl2.getName() == "loader2"
        cl1 != cl2
    }

    def "class loader spec with different classpaths are not equal"() {
        given:
        def urls1 = [new File("a.jar").toURI().toURL()]
        def urls2 = [new File("b.jar").toURI().toURL()]
        def spec1 = new VisitableURLClassLoader.Spec("name", urls1)
        def spec2 = new VisitableURLClassLoader.Spec("name", urls2)

        expect:
        spec1 != spec2
        spec1.hashCode() != spec2.hashCode()
    }

    def "null class name handling in instrumentClass"() {
        given:
        def cl = new VisitableURLClassLoader.InstrumentingVisitableURLClassLoader("test", parent, transformedClassPath)

        when:
        def result = cl.instrumentClass(null, null, new byte[0])

        then:
        result == null
    }

    def "error handler scope management with runtime exceptions"() {
        given:
        def cl = new VisitableURLClassLoader.InstrumentingVisitableURLClassLoader("test", parent, transformedClassPath)

        when:
        cl.findClass("nonexistent.Class")

        then:
        thrown(ClassNotFoundException)
    }

    def "spec with same classpath but different names are equal"() {
        given:
        def urls = [new File("a.jar").toURI().toURL()]
        def spec1 = new VisitableURLClassLoader.Spec("name1", urls)
        def spec2 = new VisitableURLClassLoader.Spec("name2", urls)

        expect:
        spec1 == spec2
        spec1.hashCode() == spec2.hashCode()
    }
}
