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
    static void generate(String gradleJarName, File binDir, String projectName) {
        String unixStartScriptHead = StartScriptsGenerator.getResourceAsStream('unixStartScriptHead.txt').text
        String unixStartScriptTail = StartScriptsGenerator.getResourceAsStream('unixStartScriptTail.txt').text

        String gradleHome = 'GRADLE_HOME'

        String unixLibPath = "\$$gradleHome/lib/$gradleJarName"

        def unixScript = "$unixStartScriptHead\nCLASSPATH=$unixLibPath\n$unixStartScriptTail"

        new File(binDir, projectName).withWriter {writer ->
            writer.write(unixScript)
        }
    }
}

