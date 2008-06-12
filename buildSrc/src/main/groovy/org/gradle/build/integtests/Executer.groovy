/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.build.integtests

import org.apache.tools.ant.taskdefs.Execute
import org.apache.tools.ant.taskdefs.condition.Os

/**
 * @author Hans Dockter
 */
class Executer {
    static final int QUIET = 0
    static final int INFO = 1
    static final int DEBUG = 2
    static Map execute(String gradleHome, String currentDirName, List tasknames, List envs = [], String buildFileName = '', int outputType = QUIET) {
        executeInternal('gradle', "${gradleHome}/bin/gradle", gradleHome, envs + ["GRADLE_HOME=$gradleHome"],
                currentDirName, tasknames, buildFileName, outputType)
    }

    static Map executeWrapper(String gradleHome, String currentDirName, List tasknames, List envs = [], String buildFileName = '', int outputType = QUIET) {
        executeInternal('gradlew', "${currentDirName}/gradlew", gradleHome, envs, currentDirName, tasknames, buildFileName, outputType)
    }

    static String windowsPath(String gradleHome) {
        "Path=$gradleHome\\bin;" + System.getenv('Path')
    }

    static String unixPath() {
        "PATH=${System.getenv('PATH')}"
    }

    static Map executeInternal(String windowsCommandSnippet, String unixCommandSnippet, String gradleHome,
                                  List envs, String currentDirName,
                                  List tasknames, String buildFileName, int outputType) {
        def proc

        def initialSize = 4096

        ByteArrayOutputStream outStream = new ByteArrayOutputStream()
        ByteArrayOutputStream errStream = new ByteArrayOutputStream()
        String taskNameText = tasknames.join(' ')
        String buildFileSpecifier = buildFileName ? "-b$buildFileName" : ''
        long runBeforeKill = 30 * 60 * 1000
        List additionalEnvs = []
        if (System.getenv('JAVA_HOME')) {additionalEnvs << "JAVA_HOME=${System.getenv('JAVA_HOME')}"}
        String actualCommand
        String windowsCommand = "cmd /c $windowsCommandSnippet ${outputOption(outputType)}" + "$buildFileSpecifier $taskNameText"
        String unixCommand = "$unixCommandSnippet ${outputOption(outputType)}$buildFileSpecifier $taskNameText"
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            Execute execute = new Execute()
            actualCommand = windowsCommand
            println "Execute in $currentDirName with: $actualCommand"
            additionalEnvs << windowsPath(gradleHome)
            execute.setEnvironment(envs + additionalEnvs as String[])
            proc = Runtime.getRuntime().exec(actualCommand, execute.getEnvironment(), new File(currentDirName))
        } else {
            actualCommand = unixCommand
            println "Execute in $currentDirName with: $actualCommand"
            additionalEnvs << unixPath()
            proc = actualCommand.execute(envs + additionalEnvs, new File(currentDirName))
        }
        unixCommand = stripGradlePath(unixCommand, unixCommandSnippet)
        proc.consumeProcessOutput(outStream, errStream)
        proc.waitForOrKill(runBeforeKill)
        int exitValue = proc.exitValue()
        String output = outStream
        String error = errStream
        if (exitValue) {
            throw new RuntimeException("Integrationtests failed with: $output $error")
        }
        return [output: output, command: actualCommand, unixCommand: unixCommand, windowsCommand: windowsCommand]
    }

    static String stripGradlePath(String unixCommand, String gradleWithPath) {
        unixCommand.substring(gradleWithPath.length() - 'gradle'.length())
    }

    static String outputOption(int outputType) {
        switch (outputType) {
            case QUIET: return '-q '
            case INFO: return ''
            case DEBUG: return '-d '
        }
    }
}
