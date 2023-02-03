/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.initialization

import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification

import static java.util.Collections.enumeration

class GradleApiSpecAggregatorTest extends Specification {

    def "will aggregate specs from all reachable GradleApiSpecProviders"() {
        given:
        def classLoader = Mock(ClassLoader)
        1 * classLoader.getResources("META-INF/services/${GradleApiSpecProvider.class.name}") >>
            resources(SpecProvider1.class.name.bytes, SpecProvider2.class.name.bytes)
        1 * classLoader.loadClass(SpecProvider1.class.name) >> SpecProvider1
        1 * classLoader.loadClass(SpecProvider2.class.name) >> SpecProvider2

        def subject = new GradleApiSpecAggregator(classLoader, DirectInstantiator.INSTANCE)

        when:
        def aggregate = subject.aggregate()

        then:
        aggregate.exportedResourcePrefixes == setOf("META-INF/gradle-plugins")
        aggregate.exportedPackages == setOf("kotlin", "org.gradle", "groovy")
    }

    static class SpecProvider1 implements GradleApiSpecProvider {
        @Override
        GradleApiSpecProvider.Spec get() {
            new GradleApiSpecProvider.SpecAdapter() {
                @Override
                Set<String> getExportedPackages() {
                    setOf("kotlin")
                }
            }
        }
    }

    static class SpecProvider2 implements GradleApiSpecProvider {
        @Override
        GradleApiSpecProvider.Spec get() {
            new GradleApiSpecProvider.SpecAdapter() {
                @Override
                Set<String> getExportedPackages() {
                    setOf("org.gradle", "groovy")
                }

                @Override
                Set<String> getExportedResourcePrefixes() {
                    setOf("META-INF/gradle-plugins")
                }
            }
        }
    }

    static Set<String> setOf(String... xs) {
        xs as Set<String>
    }

    def resources(byte[] ... content) {
        enumeration(content.collect { mockResourceUrlFor(it) })
    }

    private URL mockResourceUrlFor(byte[] contents) {
        URLStreamHandler handler = Mock()
        URLConnection connection = Mock()
        URL url = new URL("custom", "host", 12, "file", handler)
        _ * handler.openConnection(url) >> connection
        _ * connection.getInputStream() >> new ByteArrayInputStream(contents)
        url
    }
}
