/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.fixtures.build

import org.gradle.test.fixtures.file.TestFile

class BuildTestFixture {
    private final TestFile rootDir

    BuildTestFixture(TestFile rootDir) {
        this.rootDir = rootDir
    }

    BuildTestFile populate(String projectName, @DelegatesTo(BuildTestFile) Closure cl) {
        def project = new BuildTestFile(rootDir.file(projectName))
        project.with(cl)
        project
    }

    BuildTestFile singleProjectBuild(String projectName, TestFile projectDir, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        def project = new BuildTestFile(projectDir)
        project.settingsFile << "rootProject.name = '${projectName}'"
        project.buildFile << """
            group = 'org.test'
            version = '1.0'
        """
        project.file('src/main/java/Dummy.java') << "public class Dummy {}"
        project.with(cl)
        project
    }

    BuildTestFile multiProjectBuild(String projectName, TestFile projectDir, List<String> subProjects, @DelegatesTo(BuildTestFile) Closure cl = {}) {
        String subProjectList = subProjects.collect({ "'$it'" }).join(',')
        def project = new BuildTestFile(projectDir)
        project.settingsFile << """
            rootProject.name = '${projectName}'
            include ${subProjectList}
        """
        project.buildFile << """
            allprojects {
                group = 'org.test'
                version = '1.0'
            }
        """
        project.with(cl)
        project.file('src/main/java/Dummy.java') << "public class Dummy {}"
        subProjects.each {
            project.file(it, 'src/main/java/Dummy.java') << "public class Dummy {}"
        }
        project
    }

    void includeBuilds(List<File> includedBuilds) {
        def path = rootDir.toPath()
        new File(rootDir, 'settings.gradle') << "\n" + includedBuilds.collect { "includeBuild '${path.relativize(it.toPath()).toFile()}'" }.join("\n")
    }

}
