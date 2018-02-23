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
import org.gradle.internal.classpath.ClassPath

class DefaultClassPathRegistryTest extends Specification {
    final ClassPathProvider provider1 = Mock()
    final ClassPathProvider provider2 = Mock()
    final DefaultClassPathRegistry registry = new DefaultClassPathRegistry(provider1, provider2)

    def "fails for unknown classpath"() {
        given:
        provider1.findClassPath(_) >> null
        provider2.findClassPath(_) >> null

        when:
        registry.getClassPath("name")

        then:
        IllegalArgumentException e = thrown()
        e.message == 'unknown classpath \'name\' requested.'
    }

    def "delegates to providers to find classpath"() {
        def classpath = Mock(ClassPath)

        given:
        provider1.findClassPath(_) >> null
        provider2.findClassPath("name") >> classpath

        expect:
        registry.getClassPath("name") == classpath
    }
}
