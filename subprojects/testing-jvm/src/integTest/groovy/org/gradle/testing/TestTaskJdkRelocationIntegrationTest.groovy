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

import org.gradle.integtests.fixtures.AbstractTaskRelocationIntegrationTest
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.util.Requires

import static org.gradle.api.JavaVersion.VERSION_1_7
import static org.gradle.util.TextUtil.normaliseLineSeparators

@Requires(adhoc = { AvailableJavaHomes.getAvailableJdks { it.javaVersion == VERSION_1_7 }*.javaHome.unique().size() > 1 })
class TestTaskJdkRelocationIntegrationTest extends AbstractTaskRelocationIntegrationTest {

    static final List<File> JDK_PATHS = AvailableJavaHomes.getAvailableJdks { it.javaVersion == VERSION_1_7 }*.javaHome.unique()

    @Override
    protected String getTaskName() {
        return ":test"
    }

    @Override
    protected void setupProjectInOriginalLocation() {
        file("src/main/java/Foo.java") << "public class Foo {}"
        file("src/test/java/FooTest.java") << """
            import org.junit.*;

            public class FooTest {
                @Test
                public void test() {
                    new Foo();
                }
            }
        """

        file("build.gradle") << """
            apply plugin: "java"

            repositories {
                mavenCentral()
            }

            dependencies {
                testCompile "junit:junit:4.12"
            }

            sourceCompatibility = "1.7"
            targetCompatibility = "1.7"

            test {
                executable "${JDK_PATHS[0]}/bin/java"
            }

            afterEvaluate {
                println "Running with JDK: \$test.executable"
            }
        """
    }

    @Override
    protected void moveFilesAround() {
        buildFile << """
            test {
                executable "${JDK_PATHS[1]}/bin/java"
            }
        """
    }

    @Override
    protected extractResults() {
        def contents = normaliseLineSeparators(file("build/reports/tests/test/index.html").text)
        contents = contents.replaceAll(/(<a href=".*">Gradle .*?<\/a>) at [^<]+/, '$1 at [DATE]' )
        contents = contents.replaceAll(/\b\d+(\.\d+)?s\b/, "[TIME]")
        return contents
    }
}
