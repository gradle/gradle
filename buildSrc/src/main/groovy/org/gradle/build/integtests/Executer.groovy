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
        def proc

        def initialSize = 4096

        ByteArrayOutputStream outStream = new ByteArrayOutputStream()
        ByteArrayOutputStream errStream = new ByteArrayOutputStream()
        String taskNameText = tasknames.join(' ')
        String buildFileSpecifier = buildFileName ? "-b$buildFileName" : ''
        long runBeforeKill = 30 * 60 * 1000
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            Execute execute = new Execute()
            String command = "cmd /c gradle ${quite ? '-q' : '-d'}" + " $buildFileSpecifier $taskNameText"
            println "Execute test in $currentDirName with: $command"
            String path = "$gradleHome\\bin;" + System.getenv('Path')
            execute.setEnvironment(["GRADLE_HOME=$gradleHome", "Path=$path"] as String[])
            proc = Runtime.getRuntime().exec(command, execute.getEnvironment(), new File(currentDirName))
        } else {
            String command = "${gradleHome}/bin/gradle ${quite ? '-q' : '-d'} $buildFileSpecifier $taskNameText"
            println "Execute test in $currentDirName with: $command"
            proc = command.execute(["GRADLE_HOME=$gradleHome", "PATH=${System.getenv('PATH')}", "JAVA_HOME=${System.getenv('JAVA_HOME')}"],
                    new File(currentDirName))
        }
        proc.consumeProcessOutput(outStream, errStream)
        proc.waitForOrKill(runBeforeKill)
        if (proc.exitValue()) {
            throw new RuntimeException("Integrationtests failed with: $outStream $errStream,")
        }
        return outStream
    }
    static String executeWrapper(String gradleHome, String currentDirName, List tasknames, String buildFileName = '', boolean quite = true) {
        def proc

        def initialSize = 4096

        ByteArrayOutputStream outStream = new ByteArrayOutputStream()
        ByteArrayOutputStream errStream = new ByteArrayOutputStream()
        String taskNameText = tasknames.join(' ')
        String buildFileSpecifier = buildFileName ? "-b$buildFileName" : ''
        long runBeforeKill = 30 * 60 * 1000
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            Execute execute = new Execute()
            String command = "cmd /c gradlew ${quite ? '-q' : '-d'}" + " $buildFileSpecifier $taskNameText"
            println "Execute test in $currentDirName with: $command"
            String path = "$gradleHome\\bin;"
            execute.setEnvironment(["GRADLE_HOME=$gradleHome", "Path=$path"] as String[])
            proc = Runtime.getRuntime().exec(command, execute.getEnvironment(), new File(currentDirName))
        } else {
            String command = "${currentDirName}/gradlew ${quite ? '-q' : '-d'} $buildFileSpecifier $taskNameText"
            println "Execute test in $currentDirName with: $command"
            proc = command.execute(["GRADLE_HOME=$gradleHome", "PATH=${System.getenv('PATH')}"], new File(currentDirName))
        }
        proc.consumeProcessOutput(outStream, errStream)
        proc.waitForOrKill(runBeforeKill)
        if (proc.exitValue()) {
            throw new RuntimeException("Integrationtests failed with: $outStream $errStream,")
        }
        return outStream
    }
}
