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

package org.gradle.integtests.fixtures

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.internal.streams.SafeStreams
import org.gradle.test.fixtures.ConcurrentTestUtil


class ProcessFixture {
    final Long pid;

    ProcessFixture(Long pid) {
        this.pid = pid
    }

    /**
     * Forcefully kills this daemon.
     */
    void kill(boolean killTree) {
        println "Killing process with pid: $pid"
        if (pid == null) {
            throw new RuntimeException("Unable to force kill the process because provided pid is null!")
        }
        if (!(OperatingSystem.current().unix || OperatingSystem.current().windows)) {
            throw new RuntimeException("This implementation does not know how to forcefully kill a process on os: " + OperatingSystem.current())
        }
        execute(killArgs(pid, killTree), killScript(pid, killTree))
    }

    // Only supported on *nix platforms
    String[] getChildProcesses() {
        if (pid == null) {
            throw new RuntimeException("Unable to get child processes because provided pid is null!")
        }
        if (!(OperatingSystem.current().unix)) {
            throw new RuntimeException("This implementation does not know how to get child processes on os: " + OperatingSystem.current())
        }
        String result = bash("ps -o pid,ppid -ax | awk '{ if ( \$2 == ${pid} ) { print \$1 }}'").trim()
        return result == "" ? [] : result.split("\\n")
    }

    // Only supported on *nix platforms
    String[] getProcessInfo(String[] pids) {
        if (pids == null || pids.size() == 0) {
            throw new RuntimeException("Unable to get process info because provided pids are null or empty!")
        }
        if (!(OperatingSystem.current().unix)) {
            throw new RuntimeException("This implementation does not know how to get process info on os: " + OperatingSystem.current())
        }
        return bash("ps -o pid,ppid,args -p ${pids.join(' -p ')}").split("\\n")
    }

    /**
     * Blocks until the process represented by {@link #pid} has exited.
     */
    void waitForFinish() {
        if (pid == null) {
            throw new RuntimeException("Unable to wait for process to finish because provided pid is null!")
        }
        if (OperatingSystem.current().unix) {
            ConcurrentTestUtil.poll {
                bash("ps -o pid= -p $pid; exit 0").trim() == ""
            }
        } else if (OperatingSystem.current().windows) {
            ConcurrentTestUtil.poll {
                execute(["tasklist.exe", "/fi", "\"PID eq $pid\""] as Object[], SafeStreams.emptyInput()).contains("No tasks are running which match the specified criteria.")
            }
        } else {
            throw new RuntimeException("This implementation does not know how to wait for process to finish on os: " + OperatingSystem.current())
        }
    }

    private String bash(String commands) {
        return execute(["bash"] as Object[], new ByteArrayInputStream(commands.getBytes()))
    }

    private String execute(Object[] commandLine, InputStream input) {
        def output = new ByteArrayOutputStream()
        def e = TestFiles.execHandleFactory().newExecHandleBuilder()
                .commandLine(commandLine)
                .redirectErrorStream()
                .setStandardInput(input)
                .setStandardOutput(output)
                .setWorkingDir(new File(".").absoluteFile) //does not matter
                .build()
        e.start()
        def result = e.waitForFinish()
        result.rethrowFailure()

        return output.toString()
    }

    private static Object[] killArgs(Long pid, boolean killTree) {
        if (OperatingSystem.current().unix) {
            // start shell, read script from stdin
            return ["bash"]
        } else if (OperatingSystem.current().windows) {
            if (killTree) {
                // '/T' kills full process tree
                // TODO: '/T' option should be removed after fixing GRADLE-3298
                return ["taskkill.exe", "/F", "/T", "/PID", pid]
            } else {
                return ["taskkill.exe", "/F", "/PID", pid]
            }
        } else {
            throw new IllegalStateException()
        }
    }

    private static InputStream killScript(Long pid, boolean killTree) {
        if (OperatingSystem.current().unix) {
            // script for killing full process tree
            // TODO: killing full process tree should be removed after fixing GRADLE-3298
            // this script is tested on Linux and MacOSX
            def killScript = '''
killtree() {
    local _pid=$1
    for _child in $(ps -o pid,ppid -ax | awk "{ if ( \\$2 == ${_pid} ) { print \\$1 }}"); do
        killtree ${_child}
    done
    kill -9 ${_pid}
}
'''
            killScript += killTree ? "\nkilltree $pid\n" : "\nkill -9 $pid\n"
            return new ByteArrayInputStream(killScript.getBytes())
        } else if (OperatingSystem.current().windows) {
            return SafeStreams.emptyInput()
        } else {
            throw new IllegalStateException()
        }
    }
}
