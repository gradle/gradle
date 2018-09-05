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
import org.apache.commons.io.IOUtils
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.os.OperatingSystem

import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Matcher
import java.util.regex.Pattern

class JavaProcessStackTracesMonitor {
    private static final Pattern UNIX_JAVA_COMMAND_PATTERN = ~/(?i)([^\s]+\/bin\/java)/
    private static final Pattern WINDOWS_JAVA_COMMAND_PATTERN = ~/(?i)^"(.*[\/\\]bin[\/\\]java\.exe)"/
    private static final Pattern WINDOWS_PID_PATTERN = ~/([0-9]+)\s*$/
    private static final Pattern UNIX_PID_PATTERN = ~/([0-9]+)/

    static void main(String[] args) {
        System.out.println(getAllStackTracesByJstack())
    }

    @EqualsAndHashCode
    @ToString
    static class JavaProcessInfo {
        String pid
        String javaCommand

        String getJstackCommand() {
            assert javaCommand.endsWith("java") || javaCommand.endsWith("java.exe"): "Unknown java command： $javaCommand"

            Path javaPath = Paths.get(javaCommand)
            String jstackExe = OperatingSystem.current().getExecutableName('jstack')
            if (javaPath.parent.fileName.toString() == 'bin' && javaPath.parent.parent.fileName.toString() == 'jre') {
                return javaPath.resolve("../../../bin/$jstackExe").normalize().toString()
            } else {
                return javaPath.resolve("../../bin/$jstackExe").normalize().toString()
            }
        }

        String jstack() {
            Map result = runProcess([jstackCommand, pid])

            StringBuilder sb = new StringBuilder("Run ${jstackCommand} ${pid} return ${result.code}:\n${result.stdout}\n---------\n${result.stderr}\n")
            if (result.code != 0) {
                result = runProcess([jstackCommand, '-F', pid])
                sb.append("Run ${jstackCommand} -F ${pid} return ${result.code}:\n${result.stdout}\n---------\n${result.stderr}\n")
            }
            return sb.toString()
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
        List<String> command = OperatingSystem.current().isWindows() ? ["wmic", "process", "get", "processid,commandline"] : ["ps", "x"]
        Map result = runProcess(command)

        if (result.code == 0) {
            println("Command $command stdout: ${result.stdout}")
            println("Command $command stderr: ${result.stderr}")
            return new StdoutAndPatterns(result.stdout)
        } else {
            def logFile = IntegrationTestBuildContext.INSTANCE.gradleUserHomeDir.file("error-${System.currentTimeMillis()}.log")
            logFile << "code:\n"
            logFile << result.code
            logFile << "stdout:\n"
            logFile << result.stdout
            logFile << "stderr:\n"
            logFile << result.stderr

            throw new RuntimeException("Error when fetching processes, see log file ${logFile.absolutePath}")
        }
    }

    private static Map runProcess(List<String> command) {
        // Don't use Groovy's Process.consumeProcessOutput
        // https://issues.apache.org/jira/browse/GROOVY-7414
        Process process = new ProcessBuilder(command).start()

        String stdout = IOUtils.toString(process.getInputStream())
        String stderr = IOUtils.toString(process.getErrorStream())
        int code = process.waitFor()
        return [code: code, stdout: stdout, stderr: stderr]
    }

    static String getAllStackTracesByJstack() {
        return ps().getSuspiciousDaemons().collect { it.jstack() }.join("\n")
    }
}
