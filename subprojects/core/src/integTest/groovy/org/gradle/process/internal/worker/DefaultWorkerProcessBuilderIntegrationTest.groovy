/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.process.internal.worker

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.internal.TextUtil

class DefaultWorkerProcessBuilderIntegrationTest extends AbstractIntegrationSpec {

    def "test classpath does not contain nonexistent entries"() {
        given:
        def existingDir = createDir("existing")

        def pathToExistingDir = TextUtil.escapeString(existingDir.absolutePath)
        def pathToNonExistingDir = TextUtil.escapeString(new File(testDirectory, "Non exist path").absolutePath)

        javaFile("src/test/java/ClasspathTest.java", """
            import org.junit.Test;
            import java.io.*;
            import java.util.*;
            import java.util.stream.*;
            import java.util.regex.Pattern;

            import static org.junit.Assert.*;

            public class ClasspathTest {
                private static final File EXISTING_DIR = new File("$pathToExistingDir");
                private static final File NON_EXISTING_DIR = new File("$pathToNonExistingDir");

                @Test
                public void test() {
                    List<File> runtimeClasspath = Arrays.stream(
                            System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator))
                        ).map(File::new)
                        .collect(Collectors.toList());

                    System.out.println("CLASSPATH = ");
                    runtimeClasspath.forEach(System.out::println);  // Helps debugging

                    assertTrue("Must contain existing dir: " + EXISTING_DIR, runtimeClasspath.contains(EXISTING_DIR));
                    assertTrue("Must contain existing dir with star: " + EXISTING_DIR, runtimeClasspath.contains(new File(EXISTING_DIR, "*")));

                    assertFalse("Must not contain non-existent path: " + NON_EXISTING_DIR, runtimeClasspath.contains(NON_EXISTING_DIR));
                    assertFalse("Must not contain non-existent path with star: " + NON_EXISTING_DIR, runtimeClasspath.contains(new File(NON_EXISTING_DIR, "*")));
                }
            }
        """)

        buildFile """
            plugins {
                id "java-library"
            }

            repositories {
                ${mavenCentralRepository()}
            }

            dependencies {
                testImplementation 'junit:junit:4.13'
            }

            tasks.test {
                doNotTrackState("Non-existent inputs, skip fingerprint to avoid failure")

                testLogging {
                    showStandardStreams = true
                    exceptionFormat = "full"
                }

                def existingDir = new File("$pathToExistingDir")
                def nonExistingDir = new File("$pathToNonExistingDir")

                def extraClasspath = files(
                    existingDir,
                    new File(existingDir, "*"),
                    nonExistingDir,
                    new File(nonExistingDir, "*")
                )

                classpath += extraClasspath
            }
        """

        when:
        succeeds("test")

        then:
        // Verify that the test has run
        outputContains("CLASSPATH = ")
        outputContains(existingDir.absolutePath)
    }

}
