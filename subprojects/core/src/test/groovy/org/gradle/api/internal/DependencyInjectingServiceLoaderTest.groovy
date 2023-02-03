/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.service.ServiceRegistry
import org.gradle.util.TestUtil
import spock.lang.Specification

import javax.inject.Inject

import static java.util.Collections.enumeration

class DependencyInjectingServiceLoaderTest extends Specification {

    def "will load service implementation and inject dependencies"() {
        given:
        def implClassName = ServiceTypeImpl.class.name

        def classLoader = Mock(ClassLoader)
        1 * classLoader.getResources("META-INF/services/${ServiceType.class.name}") >> resources(implClassName.bytes)
        1 * classLoader.loadClass(implClassName) >> ServiceTypeImpl

        def dependency = Mock(ServiceDependency)
        def serviceRegistry = Mock(ServiceRegistry)
        1 * serviceRegistry.find(ServiceDependency) >> dependency
        1 * serviceRegistry.get(InstantiatorFactory) >> TestUtil.instantiatorFactory()

        def subject = new DependencyInjectingServiceLoader(serviceRegistry)

        when:
        def service = subject.load(ServiceType, classLoader).iterator().next()

        then:
        dependency == service.dependency
    }

    def resources(byte[] content) {
        enumeration([mockResourceUrlFor(content)])
    }

    private URL mockResourceUrlFor(byte[] contents) {
        URLStreamHandler handler = Mock()
        URLConnection connection = Mock()
        URL url = new URL("custom", "host", 12, "file", handler)
        _ * handler.openConnection(url) >> connection
        _ * connection.getInputStream() >> new ByteArrayInputStream(contents)
        url
    }

    interface ServiceType {
        ServiceDependency getDependency()
    }

    interface ServiceDependency {
    }

    static class ServiceTypeImpl implements ServiceType {
        ServiceDependency dependency

        @Inject ServiceTypeImpl(ServiceDependency dependency) {
            this.dependency = dependency
        }
    }
}
