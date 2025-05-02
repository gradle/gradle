/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugins.ide

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.ConfigureUtil

abstract class AbstractIdeProjectIntegrationTest extends AbstractIntegrationSpec {
    protected abstract String projectName(String path)

    protected abstract String getIdeName()

    protected abstract String getConfiguredModule()

    String getLifeCycleTaskName() {
        return ideName
    }

    String getCleanTaskName() {
        return "clean${lifeCycleTaskName.capitalize()}"
    }

    Project project(String projectName, boolean allProjects = true, Closure configClosure) {
        String applyTo = allProjects ? "allprojects" : "subprojects"
        buildFile.createFile().text = """
$applyTo {
    apply plugin:'java'
    apply plugin:'$ideName'
}
"""
        settingsFile.createFile().text = "rootProject.name='$projectName'\n"
        def proj = new Project(name: projectName, path: "", projectDir: getTestDirectory())
        ConfigureUtil.configure(configClosure, proj);

        def includeSubProject
        includeSubProject = { Project p ->
            for (Project subProj : p.subProjects) {
                settingsFile << "include '${subProj.path}'\n"
                includeSubProject.trampoline().call(subProj)
            }
        }

        includeSubProject.trampoline().call(proj);
    }

    public static class Project {
        String name
        String path
        def subProjects = []
        TestFile projectDir

        def project(String projectName, Closure configClosure) {
            def p = new Project(name: projectName, path: "$path:$projectName", projectDir: projectDir.createDir(projectName));
            subProjects << p;
            ConfigureUtil.configure(configClosure, p);
        }

        File getBuildFile() {
            def buildFile = projectDir.file("build.gradle")
            if (!buildFile.exists()) {
                buildFile.createFile()
            }
            return buildFile
        }
    }
}
