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

package org.gradle.cache.internal


import org.gradle.util.internal.DefaultGradleVersion
import spock.lang.Specification

import static org.gradle.util.GradleVersion.version

class CacheVersionMappingTest extends Specification {

    def "starts with version 1"() {
        when:
        def mapping = CacheVersionMapping.introducedIn("1.0").build()

        then:
        mapping.latestVersion == CacheVersion.of(1)
    }

    def "returns latest version"() {
        given:
        def parentVersion = CacheVersion.of(2)

        when:
        def mapping = CacheVersionMapping.introducedIn("1.0")
            .incrementedIn("1.1")
            .changedTo(5, "1.2")
            .build(parentVersion)

        then:
        mapping.latestVersion == parentVersion.append(5)
    }

    def "finds highest version with same base Gradle version for snapshot Gradle versions"() {
        when:
        def mapping = CacheVersionMapping.introducedIn("1.0")
            .incrementedIn("1.1-rc-1")
            .incrementedIn("1.1-rc-2")
            .build()

        then:
        !mapping.getVersionUsedBy(version("0.9-SNAPSHOT")).present
        mapping.getVersionUsedBy(version("1.0-SNAPSHOT")).get() == CacheVersion.of(1)
        mapping.getVersionUsedBy(version("1.1-SNAPSHOT")).get() == CacheVersion.of(3)
        mapping.getVersionUsedBy(version("1.2-SNAPSHOT")).get() == CacheVersion.of(3)
    }

    def "throws exception on invalid Gradle version"() {
        when:
        CacheVersionMapping.introducedIn("foo")

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("not a valid Gradle version")
    }

    def "throws exception if cache version is not greater than previous ones"() {
        when:
        CacheVersionMapping.introducedIn("1.0")
            .changedTo(3, "1.1")
            .changedTo(2, "1.2")

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("cache version (2) must be greater than all previous versions")
    }

    def "throws exception if Gradle version is not greater than previous one"() {
        when:
        CacheVersionMapping.introducedIn("1.0")
            .changedTo(2, "1.2")
            .changedTo(3, "1.1")

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Gradle version (1.1) must be greater than all previous versions")
    }

    def "throws exception if base version of Gradle version is greater than base version of current Gradle version"() {
        when:
        CacheVersionMapping.introducedIn("1.0")
            .changedTo(2, DefaultGradleVersion.current().nextMajorVersion.version)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("must not be greater than base version of current Gradle version")
    }

    def "determines cache version given a Gradle version"() {
        when:
        def mapping = CacheVersionMapping.introducedIn("1.0-rc-2")
            .incrementedIn("1.1-rc-1")
            .changedTo(5, "2.0.1")
            .build()

        then:
        !mapping.getVersionUsedBy(version("0.9.9")).present
        !mapping.getVersionUsedBy(version("1.0-rc-1")).present
        !mapping.getVersionUsedBy(version("1.0-milestone-2")).present
        mapping.getVersionUsedBy(version("1.0-rc-2")).get() == CacheVersion.of(1)
        mapping.getVersionUsedBy(version("1.0-rc-3")).get() == CacheVersion.of(1)
        mapping.getVersionUsedBy(version("1.0")).get() == CacheVersion.of(1)
        mapping.getVersionUsedBy(version("1.1-milestone-1")).get() == CacheVersion.of(1)
        mapping.getVersionUsedBy(version("1.1-rc-1")).get() == CacheVersion.of(2)
        mapping.getVersionUsedBy(version("2.0")).get() == CacheVersion.of(2)
        mapping.getVersionUsedBy(version("2.0.1-rc-1")).get() == CacheVersion.of(2)
        mapping.getVersionUsedBy(version("2.0.1")).get() == CacheVersion.of(5)
        mapping.getVersionUsedBy(version("2.0.2")).get() == CacheVersion.of(5)
        mapping.getVersionUsedBy(version("3.0")).get() == CacheVersion.of(5)
    }
}
