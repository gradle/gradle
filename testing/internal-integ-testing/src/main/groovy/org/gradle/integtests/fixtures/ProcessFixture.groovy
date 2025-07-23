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

import org.gradle.api.JavaVersion
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.internal.streams.SafeStreams
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.jspecify.annotations.Nullable

class ProcessFixture {
    final Long pid;

    ProcessFixture(Long pid) {
        this.pid = pid
    }

    /**
     * Forcefully kills this process.
     */
    void kill(boolean killTree) {
        println "Killing process with pid: $pid"
        if (pid == null) {
            throw new RuntimeException("Unable to force kill the process because provided pid is null!")
        }

        if (JavaVersion.current().isJava9Compatible()) {
            killJava9(killTree)
            return
        }

        if (!(OperatingSystem.current().unix || OperatingSystem.current().windows)) {
            throw new RuntimeException("This implementation does not know how to forcefully kill a process on os: " + OperatingSystem.current() + " and Java version: " + JavaVersion.current())
        }
        execute(killArgs(pid, killTree), killScript(pid, killTree))
    }

    private void killJava9(boolean killTree) {
        def processHandle = getProcessHandle()
        if (processHandle) {
            if (killTree) {
                processHandle.descendants().forEach { it.destroyForcibly() }
            }
            processHandle.destroyForcibly()
        }
    }

    String[] getChildProcesses() {
        if (pid == null) {
            throw new RuntimeException("Unable to get child processes because provided pid is null!")
        }

        if (JavaVersion.current().isJava9Compatible()) {
            return getChildrenJava9()
        }

        if (!(OperatingSystem.current().unix)) {
            throw new RuntimeException("This implementation does not know how to get child processes on os: " + OperatingSystem.current() + " and Java version: " + JavaVersion.current())
        }
        String result = bash("ps -o pid,ppid -ax | awk '{ if ( \$2 == ${pid} ) { print \$1 }}'").trim()
        return result == "" ? [] : result.split("\\n")
    }

    private String[] getChildrenJava9() {
        def processHandle = getProcessHandle()
        if (processHandle) {
            return processHandle.children()
                .map { it.pid().toString() }
                .toList()
                .toArray(new String[0])
        }
        return new String[0]
    }

    /**
     * Works only on Java 9 or later.
     */
    String[] getDescendants() {
        def processHandle = getProcessHandle()
        if (processHandle) {
            return processHandle.descendants()
                .map { it.pid().toString() }
                .toList()
                .toArray(new String[0])
        }
        return new String[0]
    }

    static String[] getProcessInfo(String[] pids) {
        if (pids == null || pids.size() == 0) {
            throw new RuntimeException("Unable to get process info because provided pids are null or empty!")
        }

        if (JavaVersion.current().isJava9Compatible()) {
            return getProcessInfoJava9(pids)
        }

        if (!(OperatingSystem.current().unix)) {
            throw new RuntimeException("This implementation does not know how to get process info on os: " + OperatingSystem.current() + " and Java version: " + JavaVersion.current())
        }
        return bash("ps -o pid,ppid,args -p ${pids.join(' -p ')}").split("\\n")
    }

    private static String[] getProcessInfoJava9(String[] pids) {
        def processHandles = pids.collect { pid ->
            getProcessHandle(pid as Long)
        }
        return ["PID PPID ARGS"] as String[] + processHandles.collect {
            // ProcessHandle.info() can return commandLine, but on Windows we often get no value
            def command = it.info().command().orElse("<unknown command>")
            def args = it.info().arguments().orElse([]).join(" ")
            "${it.pid()} ${it.parent().map { it.pid() }.orElse(0)} ${command} ${args}" as String
        }.toArray(new String[0])
    }

    boolean isAlive() {
        if (pid == null) {
            return false
        }
        if (JavaVersion.current().isJava9Compatible()) {
            def processHandle = getProcessHandle()
            return processHandle ? processHandle.isAlive() : false
        }
        throw new RuntimeException("ProcessFixture.isAlive() is only supported on Java 9 or later.")
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

    private static String bash(String commands) {
        return execute(["bash"] as Object[], new ByteArrayInputStream(commands.getBytes()))
    }

    private static String execute(Object[] commandLine, InputStream input) {
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

    @Nullable
    private def getProcessHandle() {
        return getProcessHandle(pid)
    }

    @Nullable
    private static def getProcessHandle(Long pid) {
        if (!JavaVersion.current().isJava9Compatible()) {
            throw new RuntimeException("Java 9 or later is required to get process handle.")
        }
        if (pid == null) {
            throw new RuntimeException("Unable to get process handle because provided pid is null!")
        }
        def processHandleOptional = Class.forName("java.lang.ProcessHandle").getMethod("of", long.class).invoke(null, pid)
        return processHandleOptional.orElse(null)
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
