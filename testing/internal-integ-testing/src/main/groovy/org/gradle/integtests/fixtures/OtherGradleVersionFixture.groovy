/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.internal.jvm.Jvm
import org.junit.Assume

/**
 * A fixture allowing an integration test to leverage another arbitrary Gradle distribution.
 */
trait OtherGradleVersionFixture {

    /**
     * Get the distribution for another Gradle version, ensuring that Gradle version
     * can execute on the current JVM.
     */
    GradleDistribution getOtherVersion() {
        GradleDistribution otherVersion = new ReleasedVersionDistributions().mostRecentRelease

        // Make sure the prior distribution supports the JVM we are testing on.
        def currentVersion = Jvm.current().javaVersionMajor
        Assume.assumeTrue(otherVersion.clientWorksWith(currentVersion))
        Assume.assumeTrue(otherVersion.daemonWorksWith(currentVersion))

        return otherVersion
    }

}
