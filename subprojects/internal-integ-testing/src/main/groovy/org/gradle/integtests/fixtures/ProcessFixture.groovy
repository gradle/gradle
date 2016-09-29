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
        def output = new ByteArrayOutputStream()
        def e = TestFiles.execHandleFactory().newExec()
            .commandLine(killArgs(pid, killTree))
            .redirectErrorStream()
            .setStandardInput(killScript(pid, killTree))
            .setStandardOutput(output)
            .workingDir(new File(".").absoluteFile) //does not matter
            .build()
        e.start()
        def result = e.waitForFinish()
        result.rethrowFailure()
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
