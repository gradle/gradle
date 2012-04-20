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

import org.gradle.internal.jvm.Jvm
import org.gradle.util.GUtil
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

/**
 * @author Tom Eyckmans
 */
class DefaultExecHandleSpec extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder();

    void "forks process"() {
        given:
        def out = new ByteArrayOutputStream();
        def err = new ByteArrayOutputStream();

        def execHandle = handle()
                .args(args(TestApp.class, "arg1", "arg2"))
                .setStandardOutput(out)
                .setErrorOutput(err)
                .build();

        when:
        def result = execHandle.start().waitForFinish();

        then:
        execHandle.state == ExecHandleState.SUCCEEDED
        result.exitValue == 0
        out.toString() == "output args: [arg1, arg2]"
        err.toString() == "error args: [arg1, arg2]"
        result.assertNormalExitValue()
    }

    void "understands when application exits with non-zero"() {
        given:
        def execHandle = handle().args(args(BrokenApp.class)).build();

        when:
        def result = execHandle.start().waitForFinish();

        then:
        execHandle.state == ExecHandleState.FAILED
        result.exitValue == 72

        when:
        result.assertNormalExitValue();

        then:
        def e = thrown(ExecException)
        e.message.contains "finished with (non-zero) exit value 72."
    }

    void "start fails when process cannot be started"() {
        def execHandle = handle().setDisplayName("awesome process").executable("no_such_command").build();

        when:
        execHandle.start();

        then:
        def e = thrown(ExecException)
        e.message == "A problem occurred starting awesome process."
    }

    void "aborts process"() {
        def execHandle = handle().args(args(SlowApp.class)).build();

        when:
        execHandle.start();
        execHandle.abort();
        def result = execHandle.waitForFinish();

        then:
        execHandle.state == ExecHandleState.ABORTED
        result.exitValue != 0
    }

    void "forks daemon and aborts it"() {
        def execHandle = handle().args(args(DaemonApp.class)).build();

        when:
        execHandle.start();
        execHandle.detach();

        then:
        execHandle.state == ExecHandleState.DETACHED

        when:
        execHandle.abort()
        def result = execHandle.waitForFinish()

        then:
        execHandle.state == ExecHandleState.ABORTED
        result.exitValue != 0
    }

    //TODO SF - we should check if detached process is still operational.
    void "detach does not rethrow daemon failure"() {
        def execHandle = handle().args(args(BrokenApp.class)).build();

        when:
        execHandle.start();
        execHandle.detach();

        then:
        execHandle.state == ExecHandleState.DETACHED
    }

    void "can redirect error stream"() {
        def out = new ByteArrayOutputStream()
        def execHandle = handle().args(args(TestApp.class)).setStandardOutput(out).redirectErrorStream().build();

        when:
        execHandle.start();
        execHandle.waitForFinish()

        then:
        ["output args", "error args"].each { out.toString().contains(it) }
    }

    private ExecHandleBuilder handle() {
        new ExecHandleBuilder()
                .executable(Jvm.current().getJavaExecutable().getAbsolutePath())
                .workingDir(tmpDir.getDir());
    }

    private List args(Class mainClass, String ... args) {
        GUtil.flattenElements("-cp", System.getProperty("java.class.path"), mainClass.getName(), args);
    }

    public static class TestApp {
        public static void main(String[] args) {
            System.out.print("output args: " + Arrays.asList(args));
            System.err.print("error args: " + Arrays.asList(args));
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

    public static class DaemonApp {
        public static void main(String[] args) throws InterruptedException {
            System.out.close();
            System.err.close();
            Thread.sleep(10000L);
        }
    }
}
