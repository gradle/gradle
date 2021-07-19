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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.server.http.MavenHttpModule

class DependencyResolutionBlacklistIntegrationTest extends AbstractDependencyUnresolvedModuleIntegrationTest {
    @ToBeFixedForConfigurationCache
    def "repository is disabled only for the current build execution"() {
        given:

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA)}
            ${configSyncTask()}
        """

        when:
        moduleA.pom.expectGetBlocking()
        fails('resolve')

        then:
        assertDependencyMetaDataReadTimeout(moduleA)
        !downloadedLibsDir.isDirectory()

        when:
        moduleA.pom.expectGet()
        moduleA.artifact.expectGet()
        succeeds('resolve')

        then:
        downloadedLibsDir.assertContainsDescendants('a-1.0.jar')
    }

    def "skips subsequent dependency resolution if HTTP connection exceeds timeout"() {
        given:
        MavenHttpModule moduleB = publishMavenModule(mavenHttpRepo, 'b')
        MavenHttpModule moduleC = publishMavenModule(mavenHttpRepo, 'c')

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA, moduleB, moduleC)}
            ${configSyncTask()}
        """

        when:
        moduleA.pom.expectGetBlocking()
        fails('resolve', '--max-workers=1')

        then:
        assertDependencyMetaDataReadTimeout(moduleA)
        assertDependencySkipped(moduleB)
        assertDependencySkipped(moduleC)
        !downloadedLibsDir.isDirectory()
    }
}
