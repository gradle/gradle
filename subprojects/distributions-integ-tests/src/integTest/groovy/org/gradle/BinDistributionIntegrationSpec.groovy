/*
 * Copyright 2012 the original author or authors.
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

package org.gradle

import org.gradle.test.fixtures.file.TestFile

class BinDistributionIntegrationSpec extends DistributionIntegrationSpec {

    def setup() {
        executer.requireOwnGradleUserHomeDir().requireIsolatedDaemons()
    }

    @Override
    String getDistributionLabel() {
        "bin"
    }

    @Override
    int getMaxDistributionSizeBytes() {
        return 125 * 1024 * 1024
    }

    def binZipContents() {
        given:
        TestFile contentsDir = unpackDistribution()

        expect:
        checkMinimalContents(contentsDir)
        contentsDir.file('src').assertDoesNotExist()
        contentsDir.file('samples').assertDoesNotExist()
        contentsDir.file('docs').assertDoesNotExist()
    }
}
