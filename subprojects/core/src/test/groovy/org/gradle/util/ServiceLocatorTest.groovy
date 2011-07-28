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
package org.gradle.util

import spock.lang.Specification
import org.gradle.api.internal.project.UnknownServiceException

class ServiceLocatorTest extends Specification {
    final ClassLoader classLoader = Mock()
    final ServiceLocator serviceLocator = new ServiceLocator(classLoader)

    def "locates service implementation class using resources of given ClassLoader"() {
        def serviceFile = stream('org.gradle.ImplClass')

        when:
        def result = serviceLocator.findServiceImplementationClass(String.class)

        then:
        result == String
        1 * classLoader.getResource("META-INF/services/java.lang.String") >> serviceFile
        1 * classLoader.loadClass('org.gradle.ImplClass') >> String
    }

    def "findServiceImplementationClass() returns null when no service meta data resource available"() {
        when:
        def result = serviceLocator.findServiceImplementationClass(String.class)

        then:
        result == null
        1 * classLoader.getResource("META-INF/services/java.lang.String") >> null
    }

    def "wraps implementation class load failure"() {
        def serviceFile = stream('org.gradle.ImplClass')
        def failure = new ClassNotFoundException()

        when:
        serviceLocator.findServiceImplementationClass(String.class)

        then:
        RuntimeException e = thrown()
        e.message == "Could not load implementation class 'org.gradle.ImplClass' for service 'java.lang.String'."
        e.cause.cause == failure
        1 * classLoader.getResource("META-INF/services/java.lang.String") >> serviceFile
        1 * classLoader.loadClass('org.gradle.ImplClass') >> { throw failure }
    }

    def "ignores comments and whitespace in service meta data resource"() {
        def serviceFile = stream('''#comment

    org.gradle.ImplClass  
''')

        when:
        def result = serviceLocator.findServiceImplementationClass(String.class)

        then:
        result == String
        1 * classLoader.getResource("META-INF/services/java.lang.String") >> serviceFile
        1 * classLoader.loadClass('org.gradle.ImplClass') >> String
    }

    def "findServiceImplementationClass() fails when no implementation class specified in service meta data resource"() {
        def serviceFile = stream('#empty!')

        when:
        serviceLocator.findServiceImplementationClass(String.class)

        then:
        RuntimeException e = thrown()
        e.message == "Could not determine implementation class for service 'java.lang.String'."
        e.cause.message == "No implementation class for service 'java.lang.String' specified in resource '${serviceFile}'."
        1 * classLoader.getResource("META-INF/services/java.lang.String") >> serviceFile
    }

    def "findServiceImplementationClass() fails when implementation class specified in service meta data resource is not assignable to service type"() {
        given:
        implementationDeclared(String, Integer)

        when:
        serviceLocator.findServiceImplementationClass(String)

        then:
        RuntimeException e = thrown()
        e.message == "Could not load implementation class 'java.lang.Integer' for service 'java.lang.String'."
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

    def "get() caches service implementation instances"() {
        given:
        implementationDeclared(CharSequence, String)

        when:
        def obj1 = serviceLocator.get(CharSequence)
        def obj2 = serviceLocator.get(CharSequence)

        then:
        obj1.is(obj2)
    }

    def "get() fails when no implementation class is specified"() {
        when:
        serviceLocator.get(CharSequence)

        then:
        UnknownServiceException e = thrown()
        e.message == "No implementation class specified for service 'java.lang.CharSequence'."
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

    def "getFactory() fails when no implementation class is specified"() {
        when:
        serviceLocator.getFactory(CharSequence)

        then:
        UnknownServiceException e = thrown()
        e.message == "No implementation class specified for service 'java.lang.CharSequence'."
    }

    def stream(String contents) {
        URLStreamHandler handler = Mock()
        URLConnection connection = Mock()
        URL url = new URL("custom", "host", 12, "file", handler)
        _ * handler.openConnection(url) >> connection
        _ * connection.getInputStream() >> new ByteArrayInputStream(contents.bytes)
        return url
    }

    def "newInstance() creates instances of implementation class"() {
        given:
        implementationDeclared(CharSequence, String)

        when:
        def result = serviceLocator.newInstance(CharSequence)

        then:
        result instanceof String
    }
    
    def implementationDeclared(Class<?> serviceType, Class<?> implementationType) {
        def serviceFile = stream(implementationType.name)
        _ * classLoader.getResource("META-INF/services/${serviceType.name}") >> serviceFile
        _ * classLoader.loadClass(implementationType.name) >> implementationType
    }
}
