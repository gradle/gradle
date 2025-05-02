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
import org.gradle.work.DisableCachingByDefault

/**
 * Generates source code for projects using a standard layout (src/componentname/c).
 *
 * Currently only supports C and PCH.
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
class NativeProjectGeneratorTask extends AbstractProjectGeneratorTask {

    void generateProjectSource(File projectDir, TestProject testProject, Map args) {
        generateProjectSource(projectDir, "c", testProject, args)
    }

    void generateProjectSource(File projectDir, String sourceLang, TestProject testProject, Map args) {
        args.moduleCount.times { m ->
            Map classArgs = args + [componentName: "lib${m + 1}"]
            generateWithTemplate(projectDir, "src/${classArgs.componentName}/headers/pch.h", 'pch.h', classArgs)
        }
        testProject.sourceFiles.times { s ->
            args.moduleCount.times { m ->
                Map classArgs = args + [componentName: "lib${m + 1}", functionName: "lib${s + 1}"]
                generateWithTemplate(projectDir, "src/${classArgs.componentName}/c/${classArgs.functionName}.c", 'lib.c', classArgs)
            }
        }
    }
}
