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

package org.gradle.testing.performance.generator.tasks

import org.gradle.testing.performance.generator.DependencyGenerator
import org.gradle.testing.performance.generator.TestProject

class CppMultiProjectGeneratorTask extends AbstractProjectGeneratorTask {
    DependencyGenerator.DependencyInfo depInfo

    @Override
    void generateProjectSource(File projectDir, TestProject testProject, Map args) {
        def projectNumber = testProject.subprojectNumber == null ? -1 : testProject.subprojectNumber
        def projectArgs = [projectType: isLastLayer(depInfo.layerSizes, projectNumber) ? 'exe' : 'lib',
                           projectDeps: depInfo.dependencies[projectNumber - 1].collect {
                               "project${it}"
                           },
                           sourceFiles: testProject.sourceFiles] + args
        generateWithTemplate(projectDir, 'build.gradle', 'build.gradle', projectArgs)
        if (projectArgs.projectType == 'exe') {
            generateWithTemplate(projectDir,'src/main/cpp/exe.cpp', 'exe.cpp', projectArgs)
        }
        testProject.sourceFiles.times { s ->
            def fName = projectNumber == -1 ? "lib${s + 1}" : "project${projectNumber}lib${s + 1}"
            Map classArgs = projectArgs + [functionName: fName]
            def headersDir = classArgs.projectType == 'exe' ? 'headers' : 'public'
            generateWithTemplate(projectDir, "src/main/cpp/${classArgs.functionName}.cpp", 'lib.cpp', classArgs)
            generateWithTemplate(projectDir, "src/main/${headersDir}/${classArgs.functionName}.h", 'lib.h', classArgs)
        }
    }

    static boolean isLastLayer(List<Integer> layerSizes, int projectNumber) {
        return layerSizes.isEmpty() || layerSizes.sum() - layerSizes.last() < projectNumber
    }

    @Override
    List<String> getDefaultProjectFiles() {
        return []
    }

    @Override
    Map getTaskArgs() {
        return [projectCnt: projectCount]
    }

    void initialize() {
        super.initialize()
        def depGenerator = new DependencyGenerator()
        depGenerator.numberOfProjects = projectCount - 1
        depInfo = depGenerator.createDependencies()
    }
}
