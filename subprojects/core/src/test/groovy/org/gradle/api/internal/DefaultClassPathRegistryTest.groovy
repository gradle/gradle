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

package org.gradle.api.internal

import spock.lang.Specification

class DefaultClassPathRegistryTest extends Specification {
    final ClassPathProvider provider = Mock()
    final DefaultClassPathRegistry registry = new DefaultClassPathRegistry(provider)

    def "fails for unknown classpath"() {
        given:
        provider.findClassPath("name") >> null

        when:
        registry.getClassPath("name")

        then:
        IllegalArgumentException e = thrown()
        e.message == 'unknown classpath \'name\' requested.'
    }

    def "converts classpath to collection of file and uri and url"() {
        def files = [new File("a.jar"), new File("with a space.jar")]

        given:
        provider.findClassPath("name") >> (files as LinkedHashSet)

        when:
        def classpath = registry.getClassPath("name")

        then:
        classpath.asFiles == files
        classpath.asURIs == files.collect { it.toURI() }
        classpath.asURLs == files.collect { it.toURI().toURL() }
        classpath.asURLArray == files.collect { it.toURI().toURL() } as URL[]
    }
}
