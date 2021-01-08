/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.rocache

import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import spock.lang.Issue

class ParentPomsReadOnlyCacheDependencyResolutionTest extends AbstractReadOnlyCacheDependencyResolutionTest {

    @Issue("https://github.com/gradle/gradle/issues/12996")
    def "doesn't attempt to write in read-only cache if parent POM is missing from it"() {
        given:
        def parent = mavenHttpRepo.module('org.readonly', 'parent', '1.0')
        def other = mavenHttpRepo.module("org.other", "other", "1.0")
            .parent("org.readonly", "parent", "1.0").publish()
        buildFile << """
            dependencies {
                implementation 'org.other:other:1.0'
            }
        """

        when:
        fileInReadReadOnlyCache("modules-${CacheLayout.ROOT.version}/files-${CacheLayout.FILE_STORE.version}/org.readonly/parent/1.0").eachFileRecurse { it.delete() }
        withReadOnlyCache()
        other.allowAll()
        parent.allowAll()
        succeeds ':checkDeps'

        then:
        noExceptionThrown()
    }


    @Override
    List<MavenHttpModule> getModulesInReadOnlyCache(MavenHttpRepository repo) {
        def parent = repo.module("org.readonly", "parent", "1.0").hasPackaging("pom")
        [
            repo.module("org.readonly", "core", "1.0"),
            repo.module("org.readonly", "util", "1.0"),
            parent,
            repo.module("org.readonly", "child", "1.0").parent("org.readonly", "parent", "1.0")
        ]
    }
}
