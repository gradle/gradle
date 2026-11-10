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
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.classpath.TransformedClassPath
import org.gradle.internal.code.Locality
import spock.lang.Specification

import static org.gradle.internal.classpath.TransformedClassPath.InstrumentationKind.EXTERNAL_DEPENDENCY
import static org.gradle.internal.classpath.TransformedClassPath.InstrumentationKind.PROJECT_DEPENDENCY
import static org.gradle.internal.classpath.TransformedClassPath.InstrumentationKind.UNKNOWN

class DefaultCodeLocalityResolverTest extends Specification {

    def "a class loaded by Gradle's own class loaders or their parents is core"() {
        given:
        def gradleLoader = new ClassLoader(getClass().classLoader) {}
        def resolver = new DefaultCodeLocalityResolver(gradleLoader)

        expect:
        resolver.localityOf(getClass()) == Locality.CORE
    }

    def "a class is placed by the class loader that loaded it, through the classpath the loader was built from"() {
        given:
        def resolver = new DefaultCodeLocalityResolver(new ClassLoader(null) {})
        // A class with no dependencies beyond the JDK, so that a loader with no parent can define it from its jar
        def classPath = TransformedClassPath.builderWithExactSize(1)
            .addUntransformed(ClasspathUtil.getClasspathForClass(Locality), kind)
            .build()
        def loader = VisitableURLClassLoader.fromClassPath("test", null, classPath)

        expect:
        resolver.localityOf(loader.loadClass(Locality.name)) == locality

        where:
        kind                | locality
        PROJECT_DEPENDENCY  | Locality.LOCAL
        EXTERNAL_DEPENDENCY | Locality.EXTERNAL
        UNKNOWN             | Locality.UNKNOWN
    }

    def "a class from a loader that keeps no record is unknown"() {
        given:
        def resolver = new DefaultCodeLocalityResolver(new ClassLoader(null) {})

        expect:
        resolver.localityOf(getClass()) == Locality.UNKNOWN
    }
}
