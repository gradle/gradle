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

import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository

class SnapshotVersionsReadOnlyCacheDependencyResolutionTest extends AbstractReadOnlyCacheDependencyResolutionTest {

    MavenHttpModule snapshot

    @Override
    List<MavenHttpModule> getModulesInReadOnlyCache(MavenHttpRepository repo) {
        [
            snapshot = repo.module("org.module", "core", "1.0-SNAPSHOT"),
        ]
    }

    def "latest version is fetched from writable cache"() {
        given:
        def latest = snapshot.publishWithChangedContent()
        buildFile << """
            dependencies {
                implementation 'org.module:core:1.0-SNAPSHOT'
            }
        """

        when:
        withReadOnlyCache()
        latest.metaData.allowGetOrHead()
        latest.pom.expectHead()
        latest.pom.sha1.expectGet()
        latest.pom.expectGet()
        latest.moduleMetadata.expectHead()
        latest.moduleMetadata.sha1.expectGet()
        latest.moduleMetadata.expectGet()
        latest.artifact.expectHead()
        latest.artifact.sha1.expectGet()
        latest.artifact.expectGet()

        succeeds ':checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(':', 'org.gradle:ro-test:20') {
                snapshot('org.module:core:1.0-SNAPSHOT', latest.uniqueSnapshotVersion, '1.0-SNAPSHOT')
            }
        }

        and:
        assertNotInReadOnlyCache("core-1.0-SNAPSHOT.jar")
    }

}
