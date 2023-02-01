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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository

class StaticVersionsReadOnlyCacheDependencyResolutionTest extends AbstractReadOnlyCacheDependencyResolutionTest {

    @Override
    boolean isPublishJavadocsAndSources() {
        true
    }

    def "fetches dependencies from read-only cache"() {
        given:
        buildFile << """
            dependencies {
                implementation 'org.readonly:core:1.0'
            }
        """

        when:
        withReadOnlyCache()
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', 'org.gradle:ro-test:20') {
                module('org.readonly:core:1.0')
            }
        }

        and:
        assertInReadOnlyCache('core-1.0.jar')
    }

    def "missing dependencies are added to writable cache"() {
        given:
        def other = mavenHttpRepo.module('org.other', 'other', '1.0').withModuleMetadata().publish()
        buildFile << """
            dependencies {
                implementation 'org.readonly:core:1.0'
                implementation 'org.other:other:1.0'
            }
        """

        when:
        withReadOnlyCache()

        other.pom.expectGet()
        other.moduleMetadata.expectGet()
        other.artifact.expectGet()

        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', 'org.gradle:ro-test:20') {
                module('org.readonly:core:1.0')
                module('org.other:other:1.0')
            }
        }

        and:
        assertInReadOnlyCache('core-1.0.jar')
        assertNotInReadOnlyCache("other-1.0.jar")
    }

    @UnsupportedWithConfigurationCache(because = "task uses artifact resolution API")
    def "can recover from corrupt read-only cache (#file)"() {
        given:
        def core = mavenHttpRepo.module('org.readonly', 'core', '1.0')
        def util = mavenHttpRepo.module('org.readonly', 'util', '1.0')
        buildFile << """
            dependencies {
                implementation 'org.readonly:core:+'
            }
        """

        when:
        fileInReadReadOnlyCache("modules-${CacheLayout.ROOT.version}/metadata-${CacheLayout.META_DATA.version}/${file}.bin").bytes = [0, 0, 0]
        withReadOnlyCache()

        core.allowAll()
        core.rootMetaData.allowGetOrHead()
        util.allowAll()

        if (resolveDynamic) {
            mavenHttpRepo.module('org.readonly', 'core', '0.9').publish()
            executer.withArgument("--refresh-dependencies")
        }

        succeeds ':checkDeps', ':extraArtifacts'

        then:
        resolve.expectGraph {
            root(':', 'org.gradle:ro-test:20') {
                edge('org.readonly:core:+', 'org.readonly:core:1.0')
            }
        }

        where:
        file << [
            'module-metadata', // stores parsed metadata in binary form
            'module-artifact', // an index of artifact id -> artifact
            'module-artifacts', // an index of modules -> artifact list
            'resource-at-url' // used for external resources found in a repository like maven-metadata.xml (listing)
        ]
    }

    @UnsupportedWithConfigurationCache(because = "task uses artifact resolution API")
    def "fetches javadocs and sources from read-only cache"() {
        given:
        buildFile << """
            dependencies {
                implementation 'org.readonly:core:1.0'
            }
        """

        when:
        withReadOnlyCache()
        succeeds ':extraArtifacts'

        then:
        noExceptionThrown()
    }

    @Override
    List<MavenHttpModule> getModulesInReadOnlyCache(MavenHttpRepository repo) {
        [
            repo.module("org.readonly", "core", "1.0"),
            repo.module("org.readonly", "util", "1.0")
        ]
    }
}
