/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.cache.internal.CacheVersion
import org.gradle.util.GradleVersion
import spock.lang.Specification

class CacheLayoutTest extends Specification {

    def "use root layout"() {
        when:
        CacheLayout cacheLayout = CacheLayout.ROOT

        then:
        cacheLayout.name == 'modules'
        cacheLayout.key == 'modules-2'
        cacheLayout.version == CacheVersion.of(2)
        cacheLayout.version.toString() == '2'
        cacheLayout.getPath(new File('some/dir')) == new File('some/dir/modules-2')
        !cacheLayout.versionMapping.getVersionUsedBy(GradleVersion.version("1.8")).present
        cacheLayout.versionMapping.getVersionUsedBy(GradleVersion.version("1.9-rc-1")).get() == CacheVersion.of(1)
        cacheLayout.versionMapping.getVersionUsedBy(GradleVersion.version("1.9-rc-2")).get() == CacheVersion.of(2)
    }

    def "use file store layout"() {
        when:
        CacheLayout cacheLayout = CacheLayout.FILE_STORE

        then:
        cacheLayout.name == 'files'
        cacheLayout.key == 'files-2.1'
        cacheLayout.version == CacheVersion.parse("2.1")
        cacheLayout.version.toString() == '2.1'
        cacheLayout.getPath(new File('some/dir')) == new File('some/dir/files-2.1')
    }

    def "use metadata store layout"() {
        when:
        CacheLayout cacheLayout = CacheLayout.META_DATA

        then:
        // If you change the value here, update the docs in dependency_resolution.adoc#sub:cache_copy
        def expectedVersion = 106
        cacheLayout.name == 'metadata'
        cacheLayout.key == "metadata-2.${expectedVersion}"
        cacheLayout.version == CacheVersion.parse("2.${expectedVersion}")
        cacheLayout.version.toString() == "2.${expectedVersion}"
        cacheLayout.getPath(new File('some/dir')) == new File("some/dir/metadata-2.${expectedVersion}")
        !cacheLayout.versionMapping.getVersionUsedBy(GradleVersion.version("1.9-rc-1")).present
        cacheLayout.versionMapping.getVersionUsedBy(GradleVersion.version("1.9-rc-2")).get() == CacheVersion.of(2, 1)
    }

    def "metadata store layout for 7.6.2 and before and after versions can be retrieved"() {
        when:
        CacheLayout cacheLayout = CacheLayout.META_DATA

        then:
        cacheLayout.versionMapping.getVersionUsedBy(GradleVersion.version("7.6.1")).get() == CacheVersion.parse("2.99")
        cacheLayout.versionMapping.getVersionUsedBy(GradleVersion.version("7.6.2")).get() == CacheVersion.parse("2.101")
        cacheLayout.versionMapping.getVersionUsedBy(GradleVersion.version("7.7")).get() == CacheVersion.parse("2.101")
        cacheLayout.versionMapping.getVersionUsedBy(GradleVersion.version("8.0")).get() == CacheVersion.parse("2.100")
    }

    def "use transforms layout"() {
        when:
        CacheLayout cacheLayout = CacheLayout.TRANSFORMS

        then:
        cacheLayout.name == 'transforms'
        cacheLayout.key == 'transforms-4'
        cacheLayout.version == CacheVersion.parse("4")
        cacheLayout.version.toString() == '4'
        cacheLayout.getPath(new File('some/dir')) == new File('some/dir/transforms-4')
    }

}
