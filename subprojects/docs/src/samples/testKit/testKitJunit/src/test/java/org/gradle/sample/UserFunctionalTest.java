/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.sample;

import org.gradle.testkit.functional.BuildResult;
import org.gradle.testkit.functional.GradleRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// START SNIPPET functional-test-junit
public class UserFunctionalTest {
    @Rule public final TemporaryFolder testProjectDir = new org.junit.rules.TemporaryFolder();
    private File buildFile;

    @Before
    public void setup() throws IOException {
        buildFile = testProjectDir.newFile("build.gradle");
    }

    @Test
    public void testHelloWorldTask() throws IOException {
        // write build script file under test
        String buildFileContent = "task helloWorld {" +
                                  "    doLast {" +
                                  "        println 'Hello world!'" +
                                  "    }" +
                                  "}";
        writeFile(buildFile, buildFileContent);

        // create and configure Gradle runner
        GradleRunner gradleRunner = GradleRunner.create();
        gradleRunner.withWorkingDir(testProjectDir.getRoot()).withTasks("helloWorld");

        // execute build script
        BuildResult result = gradleRunner.succeeds();

        // verify build result
        assertTrue(result.getStandardOutput().contains("Hello world!"));
        assertEquals(result.getStandardError(), "");
    }

    private void writeFile(File destination, String content) throws IOException {
        BufferedWriter output = null;

        try {
            output = new BufferedWriter(new FileWriter(destination));
            output.write(content);
        }
        finally {
            if(output != null) {
                output.close();
            }
        }
    }
}
// END SNIPPET functional-test-junit
