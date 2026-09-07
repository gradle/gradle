/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.buildutils.tasks

import spock.lang.Specification
import spock.lang.TempDir

class FlakyCrossVersionTestDetectorTest extends Specification {

    @TempDir
    File tmpDir

    def "is false when the project has no cross-version tests"() {
        expect:
        !FlakyCrossVersionTestDetector.INSTANCE.hasFlakyCrossVersionTest(tmpDir)
    }

    def "is false when cross-version tests exist but none are flaky"() {
        def source = new File(tmpDir, "src/crossVersionTest/groovy/SomeCrossVersionSpec.groovy")
        source.parentFile.mkdirs()
        source.text = """
            class SomeCrossVersionSpec {
                def "not flaky"() {}
            }
        """

        expect:
        !FlakyCrossVersionTestDetector.INSTANCE.hasFlakyCrossVersionTest(tmpDir)
    }

    def "is true when a class is annotated @Flaky"() {
        def source = new File(tmpDir, "src/crossVersionTest/groovy/FlakyCrossVersionSpec.groovy")
        source.parentFile.mkdirs()
        source.text = """
            import org.gradle.test.fixtures.Flaky

            @Flaky(because = "https://github.com/gradle/gradle-private/issues/1")
            class FlakyCrossVersionSpec {
                def "sometimes fails"() {}
            }
        """

        expect:
        FlakyCrossVersionTestDetector.INSTANCE.hasFlakyCrossVersionTest(tmpDir)
    }

    def "is true when a method is annotated @Flaky"() {
        def source = new File(tmpDir, "src/crossVersionTest/java/FlakyCrossVersionTest.java")
        source.parentFile.mkdirs()
        source.text = """
            package org.gradle;

            class FlakyCrossVersionTest {
                @org.gradle.test.fixtures.Flaky(because = "issue")
                void sometimesFails() {}
            }
        """

        expect:
        FlakyCrossVersionTestDetector.INSTANCE.hasFlakyCrossVersionTest(tmpDir)
    }
}
