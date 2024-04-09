/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Shared

class DocsDistributionIntegrationSpec extends DistributionIntegrationSpec {
    @Shared String version = buildContext.distZipVersion.version

    @Override
    String getDistributionLabel() {
        "docs"
    }

    @Override
    int getMaxDistributionSizeBytes() {
        return 84 * 1024 * 1024
    }

    @Override
    int getLibJarsCount() {
        0
    }

    @Requires([UnitTestPreconditions.NotWindows, UnitTestPreconditions.StableGroovy]) // cannot link to public javadocs of Groovy snapshots like https://docs.groovy-lang.org/docs/groovy-4.0.5-SNAPSHOT/html/gapi/
    def docsZipContents() {
        given:
        TestFile contentsDir = unpackDistribution()

        expect:
        contentsDir.file("LICENSE").assertIsFile()
        assertDocsExist(contentsDir, version)

        // Docs distribution contains all published samples
        contentsDir.file("docs/samples/index.html").assertIsFile()
    }
}
