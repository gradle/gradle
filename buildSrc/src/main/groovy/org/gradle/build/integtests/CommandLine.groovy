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

public class CommandLine {

    static void execute(String gradleHome, String samplesDirPath) {
        File javaprojectDir = new File(samplesDirPath, 'javaproject')
        Map result = Executer.execute(gradleHome, javaprojectDir.absolutePath, ['unknown'], [], '', Executer.QUIET, true)
        assert result.error.contains("Task 'unknown' not found ")
    }
}