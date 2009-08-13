/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.util.exec;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import org.apache.tools.ant.util.JavaEnvUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author Tom Eyckmans
 */
public class DefaultExecHandleTest {

    private DefaultExecHandle execHandle;

    private final File tmpDir = new File("tmp");
    private final File sourceDir = new File(tmpDir, "src/main/groovy");

    @Before
    public void setUp() throws IOException {
        sourceDir.mkdirs();

        final File gradlePackage = new File(sourceDir, "org/gradle");
        gradlePackage.mkdirs();

        final File testSourceFile = new File(gradlePackage, "Test.java");
        final String newLine = System.getProperty("line.separator");

        FileUtils.writeStringToFile(testSourceFile,
                "package org.gradle; " + newLine +
                "/**" + newLine +
                " *@author Test Author" + newLine +
                " */" + newLine +
                "public class Test { }"
        );
    }

    @Test
    public void testJavadocVersion() throws IOException {
        StreamWriterExecOutputHandle outHandle = new StreamWriterExecOutputHandle(System.out, true);
        StreamWriterExecOutputHandle errHandle = new StreamWriterExecOutputHandle(System.err, true);

        System.out.println("Javadoc executable = " + JavaEnvUtils.getJdkExecutable("javadoc"));



        execHandle = new DefaultExecHandle(
                tmpDir,
                JavaEnvUtils.getJdkExecutable("javadoc"),
                Arrays.asList(
                        "-verbose",
                        "-d", new File("tmp/javadocTmpOut").getAbsolutePath(),
                        "-sourcepath", "src/main/groovy",
                        "org.gradle"), 0,
                System.getenv(),
                100,
                outHandle,
                errHandle,
                new DefaultExecHandleNotifierFactory(),
                null
        );

        final ExecHandleState endState = execHandle.startAndWaitForFinish();

        if ( endState == ExecHandleState.FAILED ) {
            execHandle.getFailureCause().printStackTrace();
        }

        assertEquals(ExecHandleState.SUCCEEDED, endState);
    }

    @Test
    public void testAbort() throws IOException {
        StreamWriterExecOutputHandle outHandle = new StreamWriterExecOutputHandle(System.out, true);
        StreamWriterExecOutputHandle errHandle = new StreamWriterExecOutputHandle(System.err, true);

        System.out.println("Javadoc executable = " + JavaEnvUtils.getJdkExecutable("javadoc"));

        execHandle = new DefaultExecHandle(
                tmpDir,
                JavaEnvUtils.getJdkExecutable("javadoc"),
                Arrays.asList(
                        "-verbose",
                        "-d", new File("tmp/javadocTmpOut").getAbsolutePath(),
                        "-sourcepath", "src/main/groovy",
                        "org.gradle"), 0,
                System.getenv(),
                100,
                outHandle,
                errHandle,
                new DefaultExecHandleNotifierFactory(), 
                null
        );

        execHandle.start();
        execHandle.abort();

        final ExecHandleState endState = execHandle.waitForFinish();

        if ( endState == ExecHandleState.FAILED ) {
            execHandle.getFailureCause().printStackTrace();
        }

        assertEquals(ExecHandleState.ABORTED, endState);
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(tmpDir);
    }
}
