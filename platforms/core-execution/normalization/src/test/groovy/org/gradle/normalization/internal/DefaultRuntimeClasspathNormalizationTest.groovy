/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.normalization.internal

import org.gradle.api.GradleException
import org.gradle.api.internal.changedetection.state.PropertiesFileFilter
import spock.lang.Specification

import java.util.function.Supplier

class DefaultRuntimeClasspathNormalizationTest extends Specification {
    def "default normalization has no cached state"() {
        when:
        def normalization = new DefaultRuntimeClasspathNormalization()
        then:
        normalization.computeCachedState() == null
    }

    def "file ignores can be restored from the cached state"() {
        given:
        def originalNormalization = new DefaultRuntimeClasspathNormalization()
        originalNormalization.ignore(ignoredPattern)

        when:
        def restoredNormalization = new DefaultRuntimeClasspathNormalization()
        restoredNormalization.configureFromCachedState(originalNormalization.computeCachedState())

        then:
        restoredNormalization.classpathResourceFilter.shouldBeIgnored(ignoredFilePathSegments)
        !restoredNormalization.classpathResourceFilter.shouldBeIgnored(asPathFactory("notIgnored.file"))

        where:
        ignoredPattern | ignoredFilePathSegments
        "some.file"    | asPathFactory("some.file")
        "**/some.*"    | asPathFactory("dir", "some.file")
    }

    def "property ignores can be restored from the cached state"() {
        given:
        def originalNormalization = new DefaultRuntimeClasspathNormalization()
        originalNormalization.properties {
            it.ignoreProperty("ignored.in.all")
        }
        originalNormalization.properties("some.properties") {
            it.ignoreProperty("ignored.in.some")
        }

        when:
        def restoredNormalization = new DefaultRuntimeClasspathNormalization()
        restoredNormalization.configureFromCachedState(originalNormalization.computeCachedState())

        then:
        restoredNormalization.getPropertiesFileFilters()[PropertiesFileFilter.ALL_PROPERTIES].shouldBeIgnored("ignored.in.all")
        !restoredNormalization.getPropertiesFileFilters()[PropertiesFileFilter.ALL_PROPERTIES].shouldBeIgnored("not.ignored")
        restoredNormalization.getPropertiesFileFilters()["some.properties"].shouldBeIgnored("ignored.in.some")
        !restoredNormalization.getPropertiesFileFilters()["some.properties"].shouldBeIgnored("not.ignored")
    }

    def "metainf complete ignores can be restored from the cached state"() {
        given:
        def originalNormalization = new DefaultRuntimeClasspathNormalization()
        originalNormalization.metaInf {
            it.ignoreCompletely()
        }


        when:
        def restoredNormalization = new DefaultRuntimeClasspathNormalization()
        restoredNormalization.configureFromCachedState(originalNormalization.computeCachedState())

        then:
        restoredNormalization.classpathResourceFilter.shouldBeIgnored(asPathFactory("META-INF", "some.resource"))
    }

    def "metainf manifest ignores can be restored from the cached state"() {
        given:
        def originalNormalization = new DefaultRuntimeClasspathNormalization()
        originalNormalization.metaInf {
            it.ignoreManifest()
        }


        when:
        def restoredNormalization = new DefaultRuntimeClasspathNormalization()
        restoredNormalization.configureFromCachedState(originalNormalization.computeCachedState())

        then:
        !restoredNormalization.classpathResourceFilter.shouldBeIgnored(asPathFactory("META-INF", "some.resource"))
        restoredNormalization.classpathResourceFilter.shouldBeIgnored(asPathFactory("META-INF", "MANIFEST.MF"))
    }

    def "metainf manifest attributes ignores can be restored from the cached state"() {
        given:
        def originalNormalization = new DefaultRuntimeClasspathNormalization()
        originalNormalization.metaInf {
            it.ignoreAttribute("Ignored-Attribute")
        }


        when:
        def restoredNormalization = new DefaultRuntimeClasspathNormalization()
        restoredNormalization.configureFromCachedState(originalNormalization.computeCachedState())

        then:
        // All clients of ResourceEntryFilter are expected to convert an attribute name to lowercase
        !restoredNormalization.manifestAttributeResourceEntryFilter.shouldBeIgnored("non-ignored-attribute")
        restoredNormalization.manifestAttributeResourceEntryFilter.shouldBeIgnored("ignored-attribute")
    }

    def "metainf ignores can be restored from the cached state"() {
        given:
        def originalNormalization = new DefaultRuntimeClasspathNormalization()
        originalNormalization.metaInf {
            it.ignoreProperty("ignored.property")
        }


        when:
        def restoredNormalization = new DefaultRuntimeClasspathNormalization()
        restoredNormalization.configureFromCachedState(originalNormalization.computeCachedState())

        then:
        restoredNormalization.propertiesFileFilters["META-INF/**/*.properties"].shouldBeIgnored("ignored.property")
        !restoredNormalization.propertiesFileFilters["META-INF/**/*.properties"].shouldBeIgnored("non.ignored.property")
        !restoredNormalization.getPropertiesFileFilters()[PropertiesFileFilter.ALL_PROPERTIES].shouldBeIgnored("ignored.property")
    }

    def "exception is thrown if ignore added after caching"() {
        given:
        def normalization = new DefaultRuntimeClasspathNormalization()

        when:
        normalization.computeCachedState()
        normalization.ignore("some.file")

        then:
        thrown(GradleException.class)
    }

    def "exception is thrown if property ignore added after caching"() {
        given:
        def normalization = new DefaultRuntimeClasspathNormalization()

        when:
        normalization.computeCachedState()
        normalization.properties {
            it.ignoreProperty("some.property")
        }

        then:
        thrown(IllegalStateException.class)
    }

    def "exception is thrown if manifest attribute ignore added after caching"() {
        given:
        def normalization = new DefaultRuntimeClasspathNormalization()

        when:
        normalization.computeCachedState()
        normalization.metaInf {
            it.ignoreAttribute("Some-Attribute")
        }

        then:
        thrown(GradleException.class)
    }

    private static Supplier<String[]> asPathFactory(String... segments) {
        return () -> segments
    }
}
