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

package org.gradle.api.internal.changedetection.state

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.nio.file.Files

class BuildSessionScopeFileTimeStampInspectorTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    File workDir
    BuildSessionScopeFileTimeStampInspector timestampInspector

    def setup() {
        workDir = tmpDir.testDirectory
        timestampInspector = new BuildSessionScopeFileTimeStampInspector(workDir)
    }

    def "last build timestamp is 0 on the first build"() {
        when:
        timestampInspector.afterStart()

        then:
        timestampInspector.lastBuildTimestamp == 0
    }

    def "updates last build timestamp before complete"() {
        when:
        timestampInspector.afterStart()
        def afterStartTimestamp = timestampInspector.lastBuildTimestamp
        timestampInspector.beforeComplete()
        def beforeCompleteTimestamp = timestampInspector.lastBuildTimestamp

        then:
        afterStartTimestamp == 0
        beforeCompleteTimestamp > 0
    }

    def "last build timestamp is equal to minimum of timestamps of the marker file"() {
        given:
        timestampInspector.afterStart()
        timestampInspector.beforeComplete()
        def markerFile = new File(workDir, "last-build.bin")
        // File.lastModified() and Files.getLastModifiedTime() uses different resolution on some JDKs < 11
        def markerFileTimestamp = Math.min(markerFile.lastModified(), Files.getLastModifiedTime(markerFile.toPath()).toMillis())

        when:
        def lastBuildTimestamp = timestampInspector.lastBuildTimestamp

        then:
        lastBuildTimestamp > 0
        lastBuildTimestamp == markerFileTimestamp
    }

    def "timestamp cannot be used to detect file changes if it's equal to the last build timestamp"() {
        given:
        timestampInspector.afterStart()
        timestampInspector.beforeComplete()
        def lastBuildTimestamp = timestampInspector.lastBuildTimestamp

        when:
        def canBeUsedToDetectFileChange = timestampInspector.timestampCanBeUsedToDetectFileChange("test-file.java", lastBuildTimestamp)

        then:
        lastBuildTimestamp > 0
        !canBeUsedToDetectFileChange
    }

    def "timestamp can be used to detect file changes if it's not equal to the last build timestamp"() {
        given:
        timestampInspector.afterStart()
        timestampInspector.beforeComplete()
        def lastBuildTimestamp = timestampInspector.lastBuildTimestamp

        when:
        def canBeUsedToDetectFileChange = timestampInspector.timestampCanBeUsedToDetectFileChange("test-file.java", lastBuildTimestamp + 1)

        then:
        lastBuildTimestamp > 0
        canBeUsedToDetectFileChange
    }
}
