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

import org.gradle.internal.jvm.Jvm;
import org.gradle.process.ExecResult;
import org.gradle.util.GUtil;
import org.gradle.util.TemporaryFolder;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

/**
 * @author Tom Eyckmans
 */
public class DefaultExecHandleTest {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();

    @Test
    public void testCanForkProcess() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ExecHandle execHandle = handle()
                .args(args(TestApp.class, "arg1", "arg2"))
                .setStandardOutput(out)
                .build();

        ExecResult result = execHandle.start().waitForFinish();
        assertEquals(ExecHandleState.SUCCEEDED, execHandle.getState());
        assertEquals(0, result.getExitValue());
        assertEquals("args: [arg1, arg2]", out.toString());
        result.assertNormalExitValue();
    }

    private ExecHandleBuilder handle() {
        return new ExecHandleBuilder()
                .executable(Jvm.current().getJavaExecutable().getAbsolutePath())
                .workingDir(tmpDir.getDir());
    }

    private List args(Class mainClass, String ... args) {
        return GUtil.flattenElements("-cp", System.getProperty("java.class.path"), mainClass.getName(), args);
    }

    @Test
    public void testProcessCanHaveNonZeroExitCode() throws IOException {
        ExecHandle execHandle = handle().args(args(BrokenApp.class)).build();

        ExecResult result = execHandle.start().waitForFinish();
        assertEquals(ExecHandleState.FAILED, execHandle.getState());
        assertEquals(72, result.getExitValue());
        try {
            result.assertNormalExitValue();
            fail();
        } catch (ExecException e) {
            assertTrue(e.getMessage().contains("finished with (non-zero) exit value 72."));
        }
    }

    @Test
    public void testThrowsExceptionWhenProcessCannotBeStarted() throws IOException {
        ExecHandle execHandle = handle().setDisplayName("awesome process").executable("no_such_command").build();

        try {
            execHandle.start();
            fail();
        } catch (ExecException e) {
            assertEquals("A problem occurred starting awesome process.", e.getMessage());
        }
    }

    @Test
    public void testAbort() throws IOException {
        ExecHandle execHandle = handle().args(args(SlowApp.class)).build();

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
