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

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository

class ReadOnlyCacheValidationTest extends AbstractReadOnlyCacheDependencyResolutionTest {
    @Override
    List<MavenHttpModule> getModulesInReadOnlyCache(MavenHttpRepository repo) {
        []
    }

    void "reasonable warning if the read-only cache directory doesn't exist"() {
        given:
        executer.withReadOnlyCacheDir(new File(temporaryFolder.testDirectory, "nope"))

        when:
        run ":help"

        then:
        outputContains("The read-only dependency cache is disabled because of a configuration problem:")
        outputContains("The GRADLE_RO_DEP_CACHE environment variable was set to")
        outputContains("nope which doesn't exist!")
    }

    void "reasonable warning if the read-only cache doesn't have the expected layout"() {
        given:
        executer.withReadOnlyCacheDir(temporaryFolder.createDir("oh-noes"))

        when:
        run ":help"

        then:
        outputContains("The read-only dependency cache is disabled because of a configuration problem:")
        outputContains("Read-only cache is configured but the directory layout isn't expected. You must have a pre-populated modules-2 directory at ")
        outputContains("${new DocumentationRegistry().getDocumentationRecommendationFor("instructions on how to do this", "dependency_resolution", "sub:shared-readonly-cache")}")
    }
}
