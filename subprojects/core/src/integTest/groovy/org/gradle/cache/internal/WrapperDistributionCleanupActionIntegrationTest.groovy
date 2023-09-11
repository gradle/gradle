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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.GradleVersion

@Requires(IntegTestPreconditions.NotEmbeddedExecutor) // tests a real Gradle distribution
class WrapperDistributionCleanupActionIntegrationTest extends AbstractIntegrationSpec {

    def "reads Gradle version from actual distribution"() {
        given:
        def cleanupAction = new WrapperDistributionCleanupAction(executer.gradleUserHomeDir, Stub(UsedGradleVersions))

        when:
        def gradleVersion = cleanupAction.determineGradleVersionFromDistribution(executer.distribution.gradleHomeDir)

        then:
        gradleVersion == GradleVersion.current()
    }
}
