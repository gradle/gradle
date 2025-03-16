/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.fixtures.maven

import groovy.transform.SelfType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Provides access to a {@link ApiMavenResolver} that is configured to resolve artifacts
 * from the integration test's local maven repository.
 *
 * <p>Intended to verify that POMs published by Gradle can be consumed by Maven. Tests should
 * publish artifacts to {@link AbstractIntegrationSpec#mavenRepo} and then use the resolve exposed
 * by this fixture to run tests against those published artifacts.</p>
 */
@SelfType(AbstractIntegrationSpec.class)
trait MavenResolveTestFixture {

    /**
     * Get a resolver that can be used to perform a Maven resolution.
     */
    ApiMavenResolver getMavenResolver() {
        // We intentionally use a different m2 directory than the one defined in AbstractIntegrationSpec
        // since we want to resolve artifacts independently from the build being tested.
        // The m2 installation on the integ spec is intended to verify the way Gradle interacts
        // with the system's local m2 repo, however the resolutions performed by this fixture
        // should be isolated from the build.
        def localRepoDir = temporaryFolder.testDirectory.file("maven-resolver-test-fixture-m2")
        return new ApiMavenResolver(localRepoDir, mavenRepo.uri)
    }

}
