/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.process.internal;

import org.gradle.process.ExecResult;
import org.gradle.util.Jvm;
import org.gradle.util.TemporaryFolder;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

/**
 * @author Tom Eyckmans
 */
public class DefaultExecHandleTest {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();

    @Test
    public void testCanForkProcess() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        DefaultExecHandle execHandle = new DefaultExecHandle(
                "display-name",
                tmpDir.getDir(),
                Jvm.current().getJavaExecutable().getAbsolutePath(),
                Arrays.asList(
                        "-cp",
                        System.getProperty("java.class.path"),
                        TestApp.class.getName(),
                        "arg1", "arg2"), System.getenv(),
                out,
                System.err,
                new ByteArrayInputStream(new byte[0]),
                Collections.<ExecHandleListener>emptyList()
        );

        ExecResult result = execHandle.start().waitForFinish();
        assertEquals(ExecHandleState.SUCCEEDED, execHandle.getState());
        assertEquals(0, result.getExitValue());
        assertEquals("args: [arg1, arg2]", out.toString());
        result.assertNormalExitValue();
    }

    @Test
    public void testProcessCanHaveNonZeroExitCode() throws IOException {
        DefaultExecHandle execHandle = new DefaultExecHandle(
                "display-name",
                tmpDir.getDir(),
                Jvm.current().getJavaExecutable().getAbsolutePath(),
                Arrays.asList(
                        "-cp",
                        System.getProperty("java.class.path"),
                        BrokenApp.class.getName()), System.getenv(),
                System.out,
                System.err,
                new ByteArrayInputStream(new byte[0]),
                Collections.<ExecHandleListener>emptyList()
        );

        ExecResult result = execHandle.start().waitForFinish();
        assertEquals(ExecHandleState.FAILED, execHandle.getState());
        assertEquals(72, result.getExitValue());
        try {
            result.assertNormalExitValue();
            fail();
        } catch (ExecException e) {
            assertEquals("Display-name finished with (non-zero) exit value 72.", e.getMessage());
        }
    }

    @Test
    public void testThrowsExceptionWhenProcessCannotBeStarted() throws IOException {
        DefaultExecHandle execHandle = new DefaultExecHandle(
                "display-name",
                tmpDir.getDir(),
                "no_such_command",
                Arrays.asList("arg"),
                System.getenv(),
                System.out,
                System.err,
                new ByteArrayInputStream(new byte[0]),
                Collections.<ExecHandleListener>emptyList()
        );

        try {
            execHandle.start();
            fail();
        } catch (ExecException e) {
            assertEquals("A problem occurred starting display-name.", e.getMessage());
        }
    }

    @Test
    public void testAbort() throws IOException {
        DefaultExecHandle execHandle = new DefaultExecHandle(
                "display-name",
                tmpDir.getDir(),
                Jvm.current().getJavaExecutable().getAbsolutePath(),
                Arrays.asList(
                        "-cp",
                        System.getProperty("java.class.path"),
                        SlowApp.class.getName()), System.getenv(),
                System.out,
                System.err,
                new ByteArrayInputStream(new byte[0]),
                Collections.<ExecHandleListener>emptyList()
        );

        execHandle.start();
        execHandle.abort();

        ExecResult result = execHandle.waitForFinish();
        assertEquals(ExecHandleState.ABORTED, execHandle.getState());
        assertThat(result.getExitValue(), not(equalTo(0)));
    }

    public static class TestApp {
        public static void main(String[] args) {
            System.out.print("args: " + Arrays.asList(args));
        }
    }

    public static class BrokenApp {
        public static void main(String[] args) {
            System.exit(72);
        }
    }

    public static class SlowApp {
        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(10000L);
        }
    }
}
