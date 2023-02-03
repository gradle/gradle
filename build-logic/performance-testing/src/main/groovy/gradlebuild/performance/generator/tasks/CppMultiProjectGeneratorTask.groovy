/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.performance.generator.tasks

import gradlebuild.performance.generator.TestProject
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault

// TODO: Remove this and replace it with a BuildBuilderGenerator instead.
@DisableCachingByDefault(because = "Not made cacheable, yet")
class CppMultiProjectGeneratorTask extends AbstractProjectGeneratorTask {
    @Internal
    gradlebuild.performance.generator.DependencyGenerator.DependencyInfo depInfo

    CppMultiProjectGeneratorTask() {
        maxWorkers = 6
    }

    @Override
    void generateProjectSource(File projectDir, TestProject testProject, Map args) {
        def projectNumber = testProject.subprojectNumber == null ? -1 : testProject.subprojectNumber
        def projectArgs = [projectType: isLastLayer(depInfo.layerSizes, projectNumber) ? 'exe' : 'lib',
                           projectDeps: depInfo.dependencies[projectNumber - 1].collect {
                               "project${it}"
                           },
                           sourceFiles: testProject.sourceFiles,
                           projectName: testProject.name,
                           useMacroIncludes: false
        ] + args
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

    @Override
    def generateRootProject() {
        super.generateRootProject()
        new File(destDir, 'performance.scenarios').text = generatePerformanceScenarios()
    }

    void initialize() {
        super.initialize()
        def depGenerator = new gradlebuild.performance.generator.DependencyGenerator()
        depGenerator.numberOfProjects = projectCount - 1
        depInfo = depGenerator.createDependencies()
    }

    def generatePerformanceScenarios() {
        String headerFile
        String cppFile

        int chosenSourceFileNumber = sourceFiles / 2
        if (projectCount == 1) {
            cppFile = "src/main/cpp/lib${chosenSourceFileNumber}.cpp"
            headerFile = "src/main/headers/lib${chosenSourceFileNumber}.h"
        } else {
            int chosenSubprojectNumber = subprojects.size() / 2
            def subproject = subprojects[chosenSubprojectNumber]
            def fName = "project${subproject.subprojectNumber}lib${chosenSourceFileNumber}"
            cppFile = "${subproject.name}/src/main/cpp/${fName}.cpp"
            headerFile = "${subproject.name}/src/main/public/${fName}.h"
        }

        """
            defaults {
                gradle-args = ["--max-workers=12"]
            }

            headerChange = \${defaults} {
              tasks = ["assemble"]
              apply-h-change-to = "${headerFile}"
            }

            cppFileChange = \${defaults} {
              tasks = ["assemble"]
              apply-cpp-change-to = "${cppFile}"
            }

            assemble = \${defaults} {
              tasks = ["assemble"]
            }

            cleanAssemble = \${assemble} {
              cleanup-tasks = ["clean"]
            }

            cleanAssembleCached = \${cleanAssemble} {
              gradle-args = \${cleanAssemble.gradle-args} ["-Dorg.gradle.caching.native=true", "--build-cache"]
            }

        """.stripIndent()
    }

}
