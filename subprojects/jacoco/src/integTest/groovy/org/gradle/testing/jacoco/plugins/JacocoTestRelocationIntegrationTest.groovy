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

import com.google.common.base.Splitter
import org.gradle.integtests.fixtures.AbstractTaskRelocationIntegrationTest

class JacocoTestRelocationIntegrationTest extends AbstractTaskRelocationIntegrationTest {
    @Override
    protected String getTaskName() {
        return ":test"
    }

    @Override
    protected void setupProjectInOriginalLocation() {
        file("src/main/java/org/gradle/Class1.java") <<
            "package org.gradle; public class Class1 { public boolean isFoo(Object arg) { return true; } }"
        file("src/test/java/org/gradle/Class1Test.java") <<
            "package org.gradle; import org.junit.Test; public class Class1Test { @Test public void someTest() { new Class1().isFoo(\"test\"); } }"

        buildFile << """
            apply plugin: "java"
            apply plugin: "jacoco"

            repositories {
                mavenCentral()
            }
            dependencies {
                testCompile 'junit:junit:4.12'
            }
        """
    }

    @Override
    protected void moveFilesAround() {
        buildFile << """
            sourceSets.test.output.classesDir = file("build/test-classes")
        """
        file("build/classes/test").assertIsDir().deleteDir()
    }

    @Override
    protected extractResults() {
        file("build/jacoco/test.exec").bytes
    }

    @Override
    protected void assertResultsEqual(def originalResult, def movedResult) {
        byte[] originalBytes = originalResult
        byte[] movedBytes = movedResult

        def length = originalBytes.length
        if (movedBytes.length == length) {
            int diffCount = 0
            for (int i = 0; i < length; i++) {
                if (originalBytes[i] != movedBytes[i]) {
                    diffCount++
                }
            }
            // We accept less than 0.5% difference because timestamps and whatnot
            println String.format("%d of %d bytes were different (%.2f%%)", diffCount, length, 100d * diffCount / length)
            if (diffCount < length * 0.005) {
                return
            }
        }

        // If we had too big a difference we fall back to Groovy reporting it
        def originalAsHex = toHexStrings(originalBytes)
        def movedAsHex = toHexStrings(movedBytes)
        assert movedAsHex == originalAsHex
    }

    private static def toHexStrings(byte[] bytes) {
        def sw = new StringWriter()
        bytes.encodeHex().writeTo(sw)
        return Splitter.fixedLength(32).split(sw.toString()).collect { line ->
            Splitter.fixedLength(2).split(line).join(" ")
        }
    }
}
