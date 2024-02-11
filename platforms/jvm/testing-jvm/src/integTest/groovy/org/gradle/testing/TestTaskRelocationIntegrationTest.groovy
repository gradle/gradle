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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractProjectRelocationIntegrationTest
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.util.internal.TextUtil.normaliseLineSeparators

class TestTaskRelocationIntegrationTest extends AbstractProjectRelocationIntegrationTest {

    @Override
    protected String getTaskName() {
        return ":test"
    }

    @Override
    protected void setupProjectIn(TestFile projectDir) {
        projectDir.file("src/main/java/Foo.java") << "public class Foo {}"
        projectDir.file("src/test/java/FooTest.java") << """
            import org.junit.*;

            public class FooTest {
                @Test
                public void test() {
                    new Foo();
                }
            }
        """

        projectDir.file("build.gradle") << """
            apply plugin: "java"

            ${mavenCentralRepository()}

            dependencies {
                testImplementation "$testJunitCoordinates"
            }

            sourceSets.test.java.destinationDirectory.set(file("build/classes/test"))
        """
    }

    @Override
    protected extractResultsFrom(TestFile projectDir) {
        def contents = normaliseLineSeparators(projectDir.file("build/reports/tests/test/index.html").text)
        contents = contents.replaceAll(/(<a href=".*">Gradle .*?<\/a>) at [^<]+/, '$1 at [DATE]' )
        contents = contents.replaceAll(/\b\d+(\.\d+)?s\b/, "[TIME]")
        return contents
    }
}
