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

String startScriptDir = properties['buildSrcDir'] 
String windowsStartScriptHead = new File(startScriptDir, 'windowsStartScriptHead.txt').text
String windowsStartScriptTail = new File(startScriptDir, 'windowsStartScriptTail.txt').text
String unixStartScriptHead = new File(startScriptDir, 'unixStartScriptHead.txt').text
String unixStartScriptTail = new File(startScriptDir, 'unixStartScriptTail.txt').text

List unixLibPath = []
List windowsLibPath = []

File libHome = new File(properties['distExplodedLibDir'])

String gradleHome = 'GRADLE_HOME'
String gradleHomeUnix = "\${$gradleHome}"
String gradleHomeWindows = "%$gradleHome%"

List path = []
libHome.eachFile { path << it.name }
unixLibPath = path.collect {gradleHomeUnix + '/' + libHome.name + '/' + it}
windowsLibPath = path.collect {gradleHomeWindows + '\\' + libHome.name + '\\' + it}

def unixScript = "$unixStartScriptHead\nCLASSPATH=${unixLibPath.join(':')}\n$unixStartScriptTail"
def windowsScript = "$windowsStartScriptHead\nset CLASSPATH=${windowsLibPath.join(';')}\n$windowsStartScriptTail"
new File(properties['distExplodedBinDir'] + '/' + properties['projectName']).withWriter {writer ->
    writer.write(unixScript)
}
new File(properties['distExplodedBinDir'] + '/' + properties['projectName'] + '.bat').withWriter {writer ->
    writer.write(windowsScript)
}

def String getRelativePath(File parentDir, File currentDir) {
    String path = currentDir.name
    while (currentDir != parentDir) {
        currentDir = currentDir.parentFile
        path = "$currentDir.name/" + path
    }
    path
}


