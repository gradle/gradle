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
    static String execute(String gradleHome, String currentDirName, List tasknames, String buildFileName = '', boolean quite = true) {
        executeInternal('gradle', "${gradleHome}/bin/gradle", gradleHome, ["GRADLE_HOME=$gradleHome"],
                currentDirName, tasknames, buildFileName, quite)
    }

    static String executeWrapper(String gradleHome, String currentDirName, List tasknames, String buildFileName = '', boolean quite = true) {
        executeInternal('gradlew', "${currentDirName}/gradlew", gradleHome, [], currentDirName, tasknames, buildFileName, quite)
    }

    static String windowsPath(String gradleHome) {
        "Path=$gradleHome\\bin;" + System.getenv('Path')
    }

    static String unixPath() {
        "PATH=${System.getenv('PATH')}"
    }

    static String executeInternal(String windowsCommand, String unixCommand, String gradleHome,
                                  List envs, String currentDirName,
                                  List tasknames, String buildFileName, boolean quite) {
        def proc

        def initialSize = 4096

        ByteArrayOutputStream outStream = new ByteArrayOutputStream()
        ByteArrayOutputStream errStream = new ByteArrayOutputStream()
        String taskNameText = tasknames.join(' ')
        String buildFileSpecifier = buildFileName ? "-b$buildFileName" : ''
        long runBeforeKill = 30 * 60 * 1000
        List additionalEnvs = []
        if (System.getenv('JAVA_HOME')) {additionalEnvs << "JAVA_HOME=${System.getenv('JAVA_HOME')}"}
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            Execute execute = new Execute()
            String command = "cmd /c $windowsCommand ${quite ? '-q' : '-d'}" + " $buildFileSpecifier $taskNameText"
            println "Execute test in $currentDirName with: $command"
            additionalEnvs << windowsPath(gradleHome)
            execute.setEnvironment(envs + additionalEnvs as String[])
            proc = Runtime.getRuntime().exec(command, execute.getEnvironment(), new File(currentDirName))
        } else {
            String command = "$unixCommand ${quite ? '-q' : '-d'} $buildFileSpecifier $taskNameText"
            println "Execute test in $currentDirName with: $command"
            additionalEnvs << unixPath()
            proc = command.execute(envs + additionalEnvs, new File(currentDirName))
        }
        proc.consumeProcessOutput(outStream, errStream)
        proc.waitForOrKill(runBeforeKill)
        if (proc.exitValue()) {
            throw new RuntimeException("Integrationtests failed with: $outStream $errStream,")
        }
        return outStream
    }
}
