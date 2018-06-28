/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.fixtures.timeout

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.InProcessGradleExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.os.OperatingSystem
import org.spockframework.runtime.SpockAssertionError
import org.spockframework.runtime.SpockTimeoutError
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.extension.builtin.TimeoutInterceptor

import java.util.regex.Matcher
import java.util.regex.Pattern

class IntegrationTestTimeoutInterceptor extends TimeoutInterceptor {
    private static final Pattern UNIX_JAVA_COMMAND_PATTERN = ~/(?i)([^\s]+\/bin\/java)/
    private static final Pattern WINDOWS_JAVA_COMMAND_PATTERN = ~/(?i)^"(.*[\/\\]bin[\/\\]java\.exe)"/
    private static final Pattern WINDOWS_PID_PATTERN = ~/([0-9]+)\s*$/
    private static final Pattern UNIX_PID_PATTERN = ~/([0-9]+)/

    IntegrationTestTimeoutInterceptor(IntegrationTestTimeout timeout) {
        super(new TimeoutAdapter(timeout))
    }

    @Override
    void intercept(final IMethodInvocation invocation) throws Throwable {
        try {
            super.intercept(invocation)
        } catch (SpockTimeoutError e) {
            Object instance = invocation.getInstance()
            if (instance instanceof AbstractIntegrationSpec) {
                String allThreadStackTraces = getAllThreads((AbstractIntegrationSpec) instance)
                throw new SpockAssertionError(allThreadStackTraces, e)
            } else {
                throw e
            }
        } catch (Throwable t) {
            throw t
        }
    }

    static String getAllThreads(AbstractIntegrationSpec spec) {
        try {
            if (spec.executer.gradleExecuter.class == InProcessGradleExecuter) {
                return getAllThreadsInCurrentJVM()
            } else {
                return getAllThreadsByJstack()
            }
        } catch (Throwable e) {
            def stream = new ByteArrayOutputStream()
            e.printStackTrace(new PrintStream(stream))
            return stream.toString()
        }
    }

    @EqualsAndHashCode
    @ToString
    static class JavaProcessInfo {
        String pid
        String javaCommand

        String getJstackCommand() {
            if (javaCommand.endsWith('java')) {
                return javaCommand[0..-5] + 'jstack'
            } else if (javaCommand.endsWith('java.exe')) {
                return javaCommand[0..-9] + 'jstack.exe'
            } else {
                throw new RuntimeException("Unknown java command： $javaCommand")
            }
        }

        String jstack() {
            def process = "${jstackCommand} -F ${pid}".execute()
            def stdout = new StringBuffer()
            def stderr = new StringBuffer()
            process.consumeProcessOutput(stdout, stderr)
            process.waitFor()
            return "Run ${jstackCommand} -F ${pid}:\n${stdout}\n---------\n${stderr}\n"
        }
    }

    static class StdoutAndPatterns {
        String stdout
        Pattern pidPattern
        Pattern javaCommandPattern

        StdoutAndPatterns(String stdout) {
            this.stdout = stdout
            if (OperatingSystem.current().isWindows()) {
                pidPattern = WINDOWS_PID_PATTERN
                javaCommandPattern = WINDOWS_JAVA_COMMAND_PATTERN
            } else {
                pidPattern = UNIX_PID_PATTERN
                javaCommandPattern = UNIX_JAVA_COMMAND_PATTERN
            }
        }

        List<JavaProcessInfo> getSuspiciousDaemons() {
            stdout.readLines().findAll(this.&isSuspiciousDaemon).collect(this.&extractProcessInfo)
        }

        private JavaProcessInfo extractProcessInfo(String line) {
            Matcher javaCommandMatcher = javaCommandPattern.matcher(line)
            Matcher pidMatcher = pidPattern.matcher(line)

            javaCommandMatcher.find()
            pidMatcher.find()
            return new JavaProcessInfo(pid: pidMatcher.group(1), javaCommand: javaCommandMatcher.group(1))
        }

        private boolean isSuspiciousDaemon(String line) {
            return !isTeamCityAgent(line) && javaCommandPattern.matcher(line).find() && pidPattern.matcher(line).find()
        }

        private boolean isTeamCityAgent(String line) {
            return line.contains('jetbrains.buildServer.agent.AgentMain')
        }
    }

    private static StdoutAndPatterns ps() {
        String command = OperatingSystem.current().isWindows() ? "wmic process get processid,commandline" : "ps x"
        Process process = command.execute()

        def stdout = new StringBuffer()
        def stderr = new StringBuffer()

        process.consumeProcessOutput(stdout, stderr)

        if (process.waitFor() == 0) {
            return new StdoutAndPatterns(stdout.toString())
        } else {
            def logFile = IntegrationTestBuildContext.INSTANCE.gradleUserHomeDir.file("error-${System.currentTimeMillis()}.log")
            logFile << "stdout:\n"
            logFile << stdout
            logFile << "stderr:\n"
            logFile << stderr

            throw new RuntimeException("Error when fetching processes, see log file ${logFile.absolutePath}")
        }
    }

    static String getAllThreadsByJstack() {
        return ps().getSuspiciousDaemons().collect { it.jstack() }.join("\n")
    }

    static String getAllThreadsInCurrentJVM() {
        Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces()
        StringBuilder sb = new StringBuilder()
        sb.append("Threads in current JVM:\n")
        sb.append("--------------------------\n")
        allStackTraces.each { Thread thread, StackTraceElement[] stackTraces ->
            sb.append("Thread ${thread.getId()}: ${thread.getName()}\n")
            sb.append("--------------------------\n")
            stackTraces.each {
                sb.append("${it}\n")
            }
            sb.append("--------------------------\n")
        }

        return sb.toString()
    }
}
