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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractTaskRelocationIntegrationTest
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.util.Requires
import org.gradle.util.internal.TextUtil

import static org.gradle.util.internal.TextUtil.normaliseLineSeparators

@Requires(adhoc = { AvailableJavaHomes.getAvailableJdks(JavaVersion.VERSION_1_8).size() > 1 })
class TestTaskJdkRelocationIntegrationTest extends AbstractTaskRelocationIntegrationTest {

    private File getOriginalJavaExecutable() {
        getAvailableJdk8s()[0].javaExecutable
    }

    private File getDifferentJavaExecutable() {
        getAvailableJdk8s()[1].javaExecutable
    }

    private List<Jvm> getAvailableJdk8s() {
        AvailableJavaHomes.getAvailableJdks(JavaVersion.VERSION_1_8)
    }

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

            ${mavenCentralRepository()}

            dependencies {
                testImplementation "junit:junit:4.13"
            }

            java {
                sourceCompatibility = "1.7"
                targetCompatibility = "1.7"
            }

            test {
                executable "${TextUtil.escapeString(originalJavaExecutable)}"
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
                executable "${TextUtil.escapeString(differentJavaExecutable)}"
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
