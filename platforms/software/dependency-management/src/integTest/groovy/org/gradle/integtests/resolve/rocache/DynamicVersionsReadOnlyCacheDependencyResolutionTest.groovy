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

class DynamicVersionsReadOnlyCacheDependencyResolutionTest extends AbstractReadOnlyCacheDependencyResolutionTest {

    @Override
    List<MavenHttpModule> getModulesInReadOnlyCache(MavenHttpRepository repo) {
        [
            repo.module("org.module", "core", "1.0"),
        ]
    }


    def "latest version is fetched from writable cache"() {
        given:
        def latest = mavenHttpRepo.module('org.module', 'core', '1.1').withModuleMetadata().publish()
        buildFile << """
            dependencies {
                implementation 'org.module:core:+'
            }
        """

        when:
        withReadOnlyCache()
        latest.rootMetaData.allowGetOrHead()
        expectResolve(latest)
        if (resolveDynamic) {
            executer.withArgument("--refresh-dependencies")
        }

        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', 'org.gradle:ro-test:20') {
                edge('org.module:core:+', 'org.module:core:1.1')
            }
        }

        and:
        assertNotInReadOnlyCache("core-1.1.jar")
    }

}
