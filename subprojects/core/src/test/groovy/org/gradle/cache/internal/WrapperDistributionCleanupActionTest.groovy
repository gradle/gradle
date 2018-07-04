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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class WrapperDistributionCleanupActionTest extends Specification implements VersionSpecificCacheAndWrapperDistributionCleanupServiceFixture {

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def userHomeDir = temporaryFolder.createDir("user-home")

    @Subject def cleanupAction = new WrapperDistributionCleanupAction(userHomeDir)

    def "deletes distributions for unused versions"() {
        given:
        def versionToCleanUp = GradleVersion.version("2.3.4")
        def oldAllDist = createDistributionDir(versionToCleanUp, "all")
        def oldBinDist = createDistributionDir(versionToCleanUp, "bin")
        def currentAllDist = createDistributionDir(currentVersion, "all")
        def currentBinDist = createDistributionDir(currentVersion, "bin")

        when:
        cleanupAction.execute(versionToCleanUp)

        then:
        oldAllDist.assertDoesNotExist()
        oldBinDist.assertDoesNotExist()
        currentAllDist.assertExists()
        currentBinDist.assertExists()
    }

    @Override
    TestFile getGradleUserHomeDir() {
        return userHomeDir
    }
}
