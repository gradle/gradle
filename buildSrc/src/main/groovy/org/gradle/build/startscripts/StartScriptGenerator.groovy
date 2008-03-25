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
package org.gradle.build.startscripts

/**
 * @author Hans Dockter
 */
class StartScriptsGenerator {
    static void generate(File libDir, File binDir, String projectName) {
        String windowsStartScriptHead = StartScriptsGenerator.getResourceAsStream('windowsStartScriptHead.txt').text
        String windowsStartScriptTail = StartScriptsGenerator.getResourceAsStream('windowsStartScriptTail.txt').text
        String unixStartScriptHead = StartScriptsGenerator.getResourceAsStream('unixStartScriptHead.txt').text
        String unixStartScriptTail = StartScriptsGenerator.getResourceAsStream('unixStartScriptTail.txt').text

        List unixLibPath = []
        List windowsLibPath = []
        
        String gradleHome = 'GRADLE_HOME'
        String gradleHomeUnix = "\${$gradleHome}"
        String gradleHomeWindows = "%$gradleHome%"

        List paths = []
        libDir.eachFile {paths << it.name}
        unixLibPath = paths.collect {gradleHomeUnix + '/' + libDir.name + '/' + it}
        windowsLibPath = paths.collect {gradleHomeWindows + '\\' + libDir.name + '\\' + it}

        def unixScript = "$unixStartScriptHead\nCLASSPATH=${unixLibPath.join(':')}\n$unixStartScriptTail"
        def windowsScript = "$windowsStartScriptHead\nset CLASSPATH=${windowsLibPath.join(';')}\n$windowsStartScriptTail"
        new File(binDir, projectName).withWriter {writer ->
            writer.write(unixScript)
        }
        new File(binDir, projectName + ".bat").withWriter {writer ->
            writer.write(windowsScript)
        }
    }

    static def String getRelativePath(File parentDir, File currentDir) {
        String path = currentDir.name
        while (currentDir != parentDir) {
            currentDir = currentDir.parentFile
            path = "$currentDir.name/" + path
        }
        path
    }
}

