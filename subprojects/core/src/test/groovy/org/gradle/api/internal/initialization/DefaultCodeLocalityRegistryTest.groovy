/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.initialization

import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.code.Locality
import spock.lang.Specification

class DefaultCodeLocalityRegistryTest extends Specification {

    def "a class loaded by Gradle's own class loaders or their parents is core"() {
        given:
        def gradleLoader = new ClassLoader(getClass().classLoader) {}
        def registry = new DefaultCodeLocalityRegistry(gradleLoader)

        expect:
        registry.localityOf(getClass()) == Locality.CORE
    }

    def "a class is looked up through the jar it was loaded from"() {
        given:
        def registry = new DefaultCodeLocalityRegistry(new ClassLoader(null) {})
        registry.recordClasspathEntry(ClasspathUtil.getClasspathForClass(getClass()), locality)

        expect:
        registry.localityOf(getClass()) == locality

        where:
        locality << [Locality.LOCAL, Locality.EXTERNAL]
    }

    def "a class with no record is unknown"() {
        given:
        def registry = new DefaultCodeLocalityRegistry(new ClassLoader(null) {})

        expect:
        registry.localityOf(getClass()) == Locality.UNKNOWN
    }
}
