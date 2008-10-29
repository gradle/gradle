/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.util.GradleUtil

/**
 * @author Hans Dockter
 */
class MavenRepo {
    static final String PROJECT_NAME = 'mavenRepo'
    static final String TEST_PROJECT_NAME = 'testproject'

    static void execute(String gradleHome, String samplesDirName) {
        List expectedFiles = ['sillyexceptions-1.0.1.jar', 'repotest-1.0.jar', 'testdep-1.0.jar', 'testdep2-1.0.jar',
                'classifier-1.0-jdk15.jar', 'classifier-dep-1.0.jar', 'jaronly-1.0.jar']

        File projectDir = new File(samplesDirName, PROJECT_NAME)
        Executer.execute(gradleHome, projectDir.absolutePath, ['retrieve'], [], '', Executer.DEBUG)
        expectedFiles.each { JavaProject.checkExistence(projectDir, 'build', it)}
        GradleUtil.deleteDir(new File(projectDir, 'build'))
    }

    static void main(String[] args) {
        execute(args[0], args[1])
    }
}
