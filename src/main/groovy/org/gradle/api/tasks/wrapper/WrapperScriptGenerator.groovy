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

package org.gradle.api.tasks.wrapper

import org.gradle.wrapper.Install
import org.gradle.wrapper.InstallMain

/**
 * @author Hans Dockter
 */
class WrapperScriptGenerator {

    static void generate(String gradleVersion, String downloadUrlRoot, File scriptDestinationDir, AntBuilder ant) {
        String windowsWrapperScriptHead = Wrapper.getResourceAsStream('windowsWrapperScriptHead.txt').text
        String windowsWrapperScriptTail = Wrapper.getResourceAsStream('windowsWrapperScriptTail.txt').text
        String unixWrapperScriptHead = Wrapper.getResourceAsStream('unixWrapperScriptHead.txt').text
        String unixWrapperScriptTail = Wrapper.getResourceAsStream('unixWrapperScriptTail.txt').text

        String currentDirUnix = '`dirname "$0"`/'
        String currentDirWindows = '"%~dp0\\'
        String wrapperHomeUnix = currentDirUnix + Install.WRAPPER_DIR
        String wrapperHomeWindows = '%DIRNAME%' + Install.WRAPPER_DIR
        String wrapperJarUnix = wrapperHomeUnix + '/' + Install.WRAPPER_JAR
        String wrapperJarWindows = wrapperHomeWindows + '\\' + Install.WRAPPER_JAR
        String gradleHomeUnix = wrapperHomeUnix + "/gradle-dist/gradle-$gradleVersion"
        String gradleHomeWindows = wrapperHomeWindows + "\\gradle-dist\\gradle-$gradleVersion"
        String gradleUnix = gradleHomeUnix + '/bin/gradle'
        String gradleWindowsPath = gradleHomeWindows + '\\bin'
        String fillingUnix = """

STARTER_MAIN_CLASS=$InstallMain.name
CLASSPATH=$wrapperJarUnix
URL_ROOT=$downloadUrlRoot
DIST_NAME=gradle-${gradleVersion}-bin
GRADLE_HOME=$gradleHomeUnix
GRADLE=$gradleUnix
"""
        String fillingWindows = """
set STARTER_MAIN_CLASS=$InstallMain.name
set CLASSPATH=$wrapperJarWindows
set URL_ROOT=$downloadUrlRoot
set DIST_NAME=gradle-${gradleVersion}
set GRADLE_HOME=$gradleHomeWindows
set Path=$gradleWindowsPath;%Path%
"""

        def unixScript = "$unixWrapperScriptHead$fillingUnix$unixWrapperScriptTail"
        def windowsScript = "$windowsWrapperScriptHead$fillingWindows$windowsWrapperScriptTail"

        File unixScriptFile = new File(scriptDestinationDir, 'gradlew')
        unixScriptFile.withWriter {writer ->
            writer.write(unixScript)
        }
        
        ant.chmod(file: unixScriptFile, perm: "ugo+rx")

        new File(scriptDestinationDir, 'gradlew' + ".bat").withWriter {writer ->
            writer.write(windowsScript)
        }
    }
}
