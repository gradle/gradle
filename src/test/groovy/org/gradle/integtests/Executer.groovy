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
package org.gradle.integtests

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.util.exec.ExecHandleBuilder
import org.gradle.util.exec.ExecHandle

/**
 * @author Hans Dockter
 */
class Executer {
    static final int QUIET = 0
    static final int LIFECYCLE = 1
    static final int INFO = 2
    static final int DEBUG = 3
    
    static Map execute(String gradleHome, String currentDirName, List tasknames, Map envs = [:], String buildFileName = '', int outputType = QUIET, boolean expectFailure = false) {
        executeInternal('gradle', "${gradleHome}/bin/gradle", gradleHome, envs,
                currentDirName, tasknames, buildFileName, outputType, expectFailure)
    }

    static Map executeWrapper(String gradleHome, String currentDirName, List tasknames, Map envs = [:], String buildFileName = '', int outputType = QUIET, boolean expectFailure = false) {
        executeInternal('gradlew', "${currentDirName}/gradlew", gradleHome, envs, currentDirName, tasknames, buildFileName, outputType, expectFailure)
    }

    static Map executeInternal(String windowsCommandSnippet, String unixCommandSnippet, String gradleHome,
                                  Map envs, String currentDirName,
                                  List tasknames, String buildFileName, int outputType, boolean expectFailure) {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream()
        ByteArrayOutputStream errStream = new ByteArrayOutputStream()

        ExecHandleBuilder builder = new ExecHandleBuilder()
        builder.standardOutput(outStream)
        builder.errorOutput(errStream)
        builder.inheritEnvironment()
        builder.environment(envs)
        builder.execDirectory(new File(currentDirName))

        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            builder.execCommand("cmd")
            builder.arguments('/c', windowsCommandSnippet)
            builder.environment("Path", "$gradleHome\\bin;${System.getenv('Path')}")
            builder.environment("GRADLE_EXIT_CONSOLE", "true")
        } else {
            builder.execCommand(unixCommandSnippet)
        }

        String outLevel = outputOption(outputType)
        if (outLevel) {
            builder.arguments(outLevel)
        }
        if (buildFileName) {
            builder.arguments('-b', buildFileName)
        }
        builder.arguments(tasknames.collect {it.toString()})

        println "Execute in $builder.execDirectory with: $builder.execCommand $builder.arguments"
        ExecHandle proc = builder.execHandle
        proc.startAndWaitForFinish()

        int exitValue = proc.exitCode
        String output = outStream
        String error = errStream
        boolean failed = exitValue != 0
        if (failed != expectFailure) {
            throw new RuntimeException("Integrationtests failed with: $output $error")
        }
        return [output: output, error: error]
    }

    static String outputOption(int outputType) {
        switch (outputType) {
            case QUIET: return '-q'
            case LIFECYCLE: return null
            case INFO: return '-i'
            case DEBUG: return '-d'
        }
    }
}
