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

/**
 * @author Hans Dockter
 */
class WrapperScriptGenerator {
//    GRADLE_HOME=`dirname "$0"`/gradle/gradle-0.1
//
//. `dirname "$0"`/gradle/gradle-0.1/bin/gradle
//exec "`dirname "$0"`/gradle/gradle-0.1/bin/gradle" $QUOTED_ARGS
    static void generate(String gradleVersion, String downloadUrlRoot, File scriptDestinationDir) {
        String windowsWrapperScriptHead = Wrapper.getResourceAsStream('windowsWrapperScriptHead.txt').text
        String windowsWrapperScriptTail = Wrapper.getResourceAsStream('windowsWrapperScriptTail.txt').text
        String unixWrapperScriptHead = Wrapper.getResourceAsStream('unixWrapperScriptHead.txt').text
        String unixWrapperScriptTail = Wrapper.getResourceAsStream('unixWrapperScriptTail.txt').text


        String wrapperHomeUnix = '`dirname "$0"`/' + Install.WRAPPER_DIR
        String wrapperJar = wrapperHomeUnix + Install.WRAPPER_JAR
        String gradleHomeUnix = wrapperHomeUnix + 'gradle-dist/gradle-0.1/'
//        String gradleHomeWindows = "%$gradleHome%"

        String filling = """
CLASSPATH=$wrapperJar
URL_ROOT=$downloadUrlRoot
DIST_NAME=gradle-$gradleVersion
"""

        def unixScript = "$unixWrapperScriptHead$filling$unixWrapperScriptTail"
//        def windowsScript = "$windowsWrapperScriptHead\nset CLASSPATH=${windowsLibPath.join(';')}\n$windowsWrapperScriptTail"
        new File(scriptDestinationDir, 'gradlew').withWriter {writer ->
            writer.write(unixScript)
        }
//        new File(binDir, projectName + ".bat").withWriter {writer ->
//            writer.write(windowsScript)
//        }
    }
}
