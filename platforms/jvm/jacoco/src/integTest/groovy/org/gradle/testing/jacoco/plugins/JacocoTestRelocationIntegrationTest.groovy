/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.AbstractProjectRelocationIntegrationTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest

import static org.gradle.util.BinaryDiffUtils.levenshteinDistance
import static org.gradle.util.BinaryDiffUtils.toHexStrings

class JacocoTestRelocationIntegrationTest extends AbstractProjectRelocationIntegrationTest {

    @Override
    protected String getTaskName() {
        return ":test"
    }

    @Override
    protected void setupProjectIn(TestFile projectDir) {
        def javaProjectUnderTest = new JavaProjectUnderTest(projectDir)
        javaProjectUnderTest.writeBuildScript().writeSourceFiles()
    }

    @Override
    protected extractResultsFrom(TestFile projectDir) {
        projectDir.file("build/jacoco/test.exec").bytes
    }

    @Override
    protected void assertResultsEqual(def originalResult, def movedResult) {
        byte[] originalBytes = originalResult
        byte[] movedBytes = movedResult

        def originalLength = originalBytes.length
        def movedLength = movedBytes.length
        def lengthDiff = Math.abs(originalLength - movedLength)
        def length = Math.min(originalLength, movedLength)
        println String.format("Original is %d bytes, moved is %d bytes (%.2f%% difference)", originalLength, movedLength, 100d * lengthDiff / length)

        def distance = levenshteinDistance(originalBytes, movedBytes)
        println String.format("Levenshtein distance if %s (%.2f%% difference)", distance, 100d * distance / length)
        // We are okay with 2% distance
        if (distance > length * 0.02) {
            // If we had too big a difference we fall back to Groovy reporting it
            def originalAsHex = toHexStrings(originalBytes)
            def movedAsHex = toHexStrings(movedBytes)
            assert movedAsHex == originalAsHex
        }
    }
}
