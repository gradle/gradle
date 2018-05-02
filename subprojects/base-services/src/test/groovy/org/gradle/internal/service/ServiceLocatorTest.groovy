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
package org.gradle.internal.service

import spock.lang.Specification

class ServiceLocatorTest extends Specification {
    final ClassLoader classLoader = Mock()
    final DefaultServiceLocator serviceLocator = new DefaultServiceLocator(classLoader)

    def "locates service implementation class using resources of given ClassLoader"() {
        def serviceFile = stream('org.gradle.ImplClass')

        when:
        def result = serviceLocator.findFactory(String.class).create()

        then:
        result instanceof String
        1 * classLoader.getResources("META-INF/services/java.lang.String") >> Collections.enumeration([serviceFile])
        1 * classLoader.loadClass('org.gradle.ImplClass') >> String
    }

    def "uses union of resources found in all ClassLoaders"() {
        def ClassLoader classLoader2 = Mock()
        def serviceLocator = new DefaultServiceLocator(classLoader, classLoader2)

        def serviceFile1 = stream('org.gradle.ImplClass')
        def serviceFile2 = stream('org.gradle.ImplClass2')
        def serviceFile3 = stream('org.gradle.ImplClass')
        def serviceFile4 = stream('org.gradle.ImplClass3')

        when:
        def result = serviceLocator.getAll(CharSequence.class)

        then:
        result*.class == [String, StringBuilder, StringBuffer]
        1 * classLoader.getResources("META-INF/services/java.lang.CharSequence") >> Collections.enumeration([serviceFile1, serviceFile2])
        1 * classLoader.loadClass('org.gradle.ImplClass') >> String
        1 * classLoader.loadClass('org.gradle.ImplClass2') >> StringBuilder
        1 * classLoader2.getResources("META-INF/services/java.lang.CharSequence") >> Collections.enumeration([serviceFile3, serviceFile4])
        1 * classLoader2.loadClass('org.gradle.ImplClass3') >> StringBuffer
    }

    def "findFactory() returns null when no service meta data resource available"() {
        when:
        def result = serviceLocator.findFactory(String.class)

        then:
        result == null
        1 * classLoader.getResources("META-INF/services/java.lang.String") >> Collections.enumeration([])
    }

    def "wraps implementation class load failure"() {
        def serviceFile = stream('org.gradle.ImplClass')
        def failure = new ClassNotFoundException()

        when:
        serviceLocator.findFactory(String.class)

        then:
        RuntimeException e = thrown()
        e.message == "Could not load implementation class 'org.gradle.ImplClass' for service 'java.lang.String' specified in resource '" + serviceFile + "'."
        e.cause == failure
        1 * classLoader.getResources("META-INF/services/java.lang.String") >> Collections.enumeration([serviceFile])
        1 * classLoader.loadClass('org.gradle.ImplClass') >> { throw failure }
    }

    def "ignores comments and whitespace in service meta data resource"() {
        def serviceFile = stream('''#comment

    org.gradle.ImplClass
''')

        when:
        def result = serviceLocator.findFactory(String.class).create()

        then:
        result instanceof String
        1 * classLoader.getResources("META-INF/services/java.lang.String") >> Collections.enumeration([serviceFile])
        1 * classLoader.loadClass('org.gradle.ImplClass') >> String
    }

    def "can locate multiple service implementations from one resource file"() {
        def serviceFile = stream("""org.gradle.ImplClass1
org.gradle.ImplClass2""")

        when:
        def result = serviceLocator.getAll(String.class)

        then:
        result.size() == 2
        1 * classLoader.getResources("META-INF/services/java.lang.String") >> Collections.enumeration([serviceFile])
        1 * classLoader.loadClass('org.gradle.ImplClass1') >> String
        1 * classLoader.loadClass('org.gradle.ImplClass2') >> String
    }

    def "findFactory() fails when no implementation class specified in service meta data resource"() {
        def serviceFile = stream('#empty!')

        when:
        serviceLocator.findFactory(String.class)

        then:
        RuntimeException e = thrown()
        e.message == "Could not determine implementation class for service 'java.lang.String' specified in resource '" + serviceFile + "'."
        e.cause.message == "No implementation class for service 'java.lang.String' specified."
        1 * classLoader.getResources("META-INF/services/java.lang.String") >> Collections.enumeration([serviceFile])
    }

    def "findFactory() fails when implementation class specified in service meta data resource is not assignable to service type"() {
        given:
        implementationDeclared(String, Integer)

        when:
        serviceLocator.findFactory(String)

        then:
        RuntimeException e = thrown()
        e.message.startsWith("Could not load implementation class 'java.lang.Integer' for service 'java.lang.String' specified in resource '")
        e.cause.message == "Implementation class 'java.lang.Integer' is not assignable to service class 'java.lang.String'."
    }

    def "get() creates an instance of specified service implementation class"() {
        given:
        implementationDeclared(CharSequence, String)

        when:
        def result = serviceLocator.get(CharSequence)

        then:
        result instanceof String
    }

    def "get() fails when no meta-data file found for service type"() {
        when:
        serviceLocator.get(CharSequence)

        then:
        UnknownServiceException e = thrown()
        e.message == "Could not find meta-data resource 'META-INF/services/java.lang.CharSequence' for service 'java.lang.CharSequence'."
        1 * classLoader.getResources("META-INF/services/java.lang.CharSequence") >> Collections.enumeration([])
    }

    def "getFactory() returns a factory which creates instances of implementation class"() {
        given:
        implementationDeclared(CharSequence, String)

        when:
        def factory = serviceLocator.getFactory(CharSequence)
        def obj1 = factory.create()
        def obj2 = factory.create()

        then:
        obj1 instanceof String
        obj2 instanceof String
        !obj1.is(obj2)
    }

    def "getFactory() fails when no meta-data file found for service type"() {
        when:
        serviceLocator.getFactory(CharSequence)

        then:
        UnknownServiceException e = thrown()
        e.message == "Could not find meta-data resource 'META-INF/services/java.lang.CharSequence' for service 'java.lang.CharSequence'."
        1 * classLoader.getResources("META-INF/services/java.lang.CharSequence") >> Collections.enumeration([])
    }

    def "getAll() returns an instance of each declared implementation"() {
        def impl1 = stream("org.gradle.Impl1")
        def impl2 = stream("org.gradle.Impl2")

        when:
        def result = serviceLocator.getAll(CharSequence)

        then:
        result.size() == 2
        result[0] instanceof String
        result[1] instanceof StringBuilder
        1 * classLoader.getResources("META-INF/services/java.lang.CharSequence") >> Collections.enumeration([impl1, impl2])
        1 * classLoader.loadClass("org.gradle.Impl1") >> String
        1 * classLoader.loadClass("org.gradle.Impl2") >> StringBuilder
    }

    def "getAll() ignores duplicate implementation classes"() {
        def impl1 = stream("org.gradle.Impl1")
        def impl2 = stream("org.gradle.Impl2")
        def impl3 = stream("org.gradle.Impl1")

        when:
        def result = serviceLocator.getAll(CharSequence)

        then:
        result.size() == 2
        result[0] instanceof String
        result[1] instanceof StringBuilder
        1 * classLoader.getResources("META-INF/services/java.lang.CharSequence") >> Collections.enumeration([impl1, impl2, impl3])
        1 * classLoader.loadClass("org.gradle.Impl1") >> String
        1 * classLoader.loadClass("org.gradle.Impl2") >> StringBuilder
    }

    def "getAll() returns empty collection no meta-data file found for service type"() {
        when:
        def result = serviceLocator.getAll(CharSequence)

        then:
        result.empty
        1 * classLoader.getResources("META-INF/services/java.lang.CharSequence") >> Collections.enumeration([])
    }

    def "servicelocator uses utf-8 encoding for reading serviceFile"() {
        def serviceFile = stream(className)

        when:
        def result = serviceLocator.get(String.class)

        then:
        result instanceof String
        1 * classLoader.getResources("META-INF/services/java.lang.String") >> Collections.enumeration([serviceFile])
        1 * classLoader.loadClass(className) >> String

        where:
        className << ['org.gradle.κόσμε']
    }


    def stream(String contents) {
        URLStreamHandler handler = Mock()
        URLConnection connection = Mock()
        URL url = new URL("custom", "host", 12, "file", handler)
        _ * handler.openConnection(url) >> connection
        _ * connection.getInputStream() >> new ByteArrayInputStream(contents.getBytes("UTF-8"))
        return url
    }

    def implementationDeclared(Class<?> serviceType, Class<?> implementationType) {
        def serviceFile = stream(implementationType.name)
        _ * classLoader.getResources("META-INF/services/${serviceType.name}") >> Collections.enumeration([serviceFile])
        _ * classLoader.loadClass(implementationType.name) >> implementationType
    }
}
