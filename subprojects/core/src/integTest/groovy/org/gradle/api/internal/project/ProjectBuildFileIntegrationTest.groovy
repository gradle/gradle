/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.project

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ProjectBuildFileIntegrationTest extends AbstractIntegrationSpec {

    def "project.buildFile is non null when build file does not exist"() {
        expect:
        !buildFile.exists()

        when:
        createDirs("child")
        settingsFile << "include 'child'"
        def initScript = file("init.gradle") << """
            rootProject { 
                assert buildFile.canonicalPath == '${buildFile.canonicalPath.replace("\\", "\\\\")}'
                assert project(":child").buildFile.canonicalPath == '${file("child/build.gradle").canonicalPath.replace("\\", "\\\\")}'
                println "init script applied" 
            }
        """

        then:
        succeeds "help", '-I', initScript.absolutePath
        output.contains "init script applied" // ensure we actually tested something
    }

    def "buildSrc project.buildFile is non null when does not exist"() {
        given:
        executer.requireOwnGradleUserHomeDir()
        file("buildSrc/settings.gradle").createFile()

        expect:
        !buildFile.exists()

        when:
        executer.gradleUserHomeDir.file("init.d/init.gradle") << """
            rootProject { 
                if (project.gradle.parent != null) { // is buildSrc build
                    assert buildFile.canonicalPath == '${file("buildSrc/build.gradle").canonicalPath.replace("\\", "\\\\")}'
                    println "init script applied" 
                }
            }
        """

        then:
        succeeds "help"
        output.contains "init script applied" // ensure we actually tested something
    }
}
