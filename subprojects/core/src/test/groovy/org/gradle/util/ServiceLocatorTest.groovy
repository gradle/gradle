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

class ServiceLocatorTest extends Specification {
    final ServiceLocator serviceLocator = new ServiceLocator()
    final ClassLoader classLoader = Mock()

    def "locates service implementation class using resources of given ClassLoader"() {
        def serviceFile = stream('org.gradle.ImplClass')

        when:
        def result = serviceLocator.findServiceImplementationClass(String.class, classLoader)

        then:
        result == String
        1 * classLoader.getResource("META-INF/services/java.lang.String") >> serviceFile
        1 * classLoader.loadClass('org.gradle.ImplClass') >> String
    }

    def "returns null when no service meta data resource available"() {
        when:
        def result = serviceLocator.findServiceImplementationClass(String.class, classLoader)

        then:
        result == null
        1 * classLoader.getResource("META-INF/services/java.lang.String") >> null
    }

    def "wraps implementation class load failure"() {
        def serviceFile = stream('org.gradle.ImplClass')
        def failure = new ClassNotFoundException()

        when:
        serviceLocator.findServiceImplementationClass(String.class, classLoader)

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
        def result = serviceLocator.findServiceImplementationClass(String.class, classLoader)

        then:
        result == String
        1 * classLoader.getResource("META-INF/services/java.lang.String") >> serviceFile
        1 * classLoader.loadClass('org.gradle.ImplClass') >> String
    }

    def "fails when no implementation class specified in service meta data resource"() {
        def serviceFile = stream('#empty!')

        when:
        serviceLocator.findServiceImplementationClass(String.class, classLoader)

        then:
        RuntimeException e = thrown()
        e.message == "Could not determine implementation class for service 'java.lang.String'."
        e.cause.message == "No implementation class for service 'java.lang.String' specified in resource '${serviceFile}'."
        1 * classLoader.getResource("META-INF/services/java.lang.String") >> serviceFile
    }

    def "fails when implementation class specified in service meta data resource is not assignable to service type"() {
        def serviceFile = stream('org.gradle.ImplClass')

        when:
        serviceLocator.findServiceImplementationClass(String.class, classLoader)

        then:
        RuntimeException e = thrown()
        e.message == "Could not load implementation class 'org.gradle.ImplClass' for service 'java.lang.String'."
        e.cause.message == "Implementation class 'org.gradle.ImplClass' is not assignable to service class 'java.lang.String'."
        1 * classLoader.getResource("META-INF/services/java.lang.String") >> serviceFile
        1 * classLoader.loadClass('org.gradle.ImplClass') >> Integer
    }

    def stream(String contents) {
        URLStreamHandler handler = Mock()
        URLConnection connection = Mock()
        URL url = new URL("custom", "host", 12, "file", handler)
        _ * handler.openConnection(url) >> connection
        _ * connection.getInputStream() >> new ByteArrayInputStream(contents.bytes)
        return url
    }
}
