/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.api.UncheckedIOException
import org.gradle.internal.service.ServiceCreationException

@TargetVersions(["1.9", "1.10"])
class CrossVersionRetryTest extends CrossVersionIntegrationSpec {

    def iteration = 0

    def "retry for metadata directory creation issue in Gradle 1.9 or 1.10"() {
        given:
        iteration++

        when:
        throwWhen(new ServiceCreationException("Could not create service of type ArtifactCacheLockingAccessCoordinator using DependencyManagementBuildScopeServices.createArtifactCacheLockingManager().",
            new UncheckedIOException("Unable to create directory 'metadata-2.1'")), iteration == 1)

        then:
        true
    }

    private static void throwWhen(Throwable throwable, boolean condition) {
        if (condition) {
            throw throwable
        }
    }
}
