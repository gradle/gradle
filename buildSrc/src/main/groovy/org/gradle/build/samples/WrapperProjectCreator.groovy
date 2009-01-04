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

package org.gradle.build.samples

import org.gradle.api.tasks.wrapper.Wrapper

/**
 * @author Hans Dockter
 */
class WrapperProjectCreator {
    final static String WRAPPER_PROJECT_NAME = 'wrapper-project'
    final static String WRAPPER_TASK_NAME = 'wrapper'
    final static String TEST_TASK_NAME = 'hello'
    final static String TEST_TASK_OUTPUT = 'hello'

    static void createProject(File baseDir, File downloadUrlRoot, String gradleVersion) {
        String gradleScript = """
createTask('$WRAPPER_TASK_NAME', type: $Wrapper.name).configure {
    gradleVersion = '$gradleVersion'
    urlRoot = '${downloadUrlRoot.toURI().toURL()}'
    zipBase = Wrapper.PathBase.PROJECT
    zipPath = 'wrapper'
    archiveBase = Wrapper.PathBase.PROJECT
    archivePath = 'dist'
    distributionBase = Wrapper.PathBase.PROJECT
    distributionPath = 'dist'
}

createTask('$TEST_TASK_NAME') {
    println '$TEST_TASK_OUTPUT'
}
"""
        File wrapperRoot = new File(baseDir, WRAPPER_PROJECT_NAME)
        wrapperRoot.mkdirs()
        new File(wrapperRoot, "build.gradle").withPrintWriter {PrintWriter writer -> writer.write(gradleScript)}
    }

}
