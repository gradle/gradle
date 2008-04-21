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

import org.gradle.build.samples.WrapperProjectCreator

/**
 * @author Hans Dockter
 */
class WrapperProject {
    static void execute(String gradleHome, String samplesDirName) {
        String nl = System.properties['line.separator']
        File waterDir = new File(samplesDirName, WrapperProjectCreator.WRAPPER_PROJECT_NAME)

        Executer.execute(gradleHome, waterDir.absolutePath, [WrapperProjectCreator.WRAPPER_TASK_NAME])
        Map result = Executer.executeWrapper(gradleHome, waterDir.absolutePath,
                [WrapperProjectCreator.TEST_TASK_NAME])
        String compareValue =  result.output.substring(result.output.size() - WrapperProjectCreator.TEST_TASK_OUTPUT.size() - nl.size())
        assert  compareValue == WrapperProjectCreator.TEST_TASK_OUTPUT + nl
    }
}
