/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.ide.visualstudio.tasks
import org.gradle.api.Incubating
import org.gradle.ide.visualstudio.VisualStudioSolution
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioSolution
import org.gradle.ide.visualstudio.tasks.internal.VisualStudioSolutionFile
import org.gradle.plugins.ide.api.GeneratorTask
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObjectGenerator

@Incubating
class GenerateSolutionFileTask extends GeneratorTask<VisualStudioSolutionFile> {
    private DefaultVisualStudioSolution solution

    GenerateSolutionFileTask() {
        generator = new ConfigurationObjectGenerator();
    }

    void setVisualStudioSolution(VisualStudioSolution solution) {
        this.solution = solution as DefaultVisualStudioSolution

        dependsOn {
            this.solution.projects
        }
    }

    VisualStudioSolution getSolution() {
        return solution
    }

    @Override
    File getInputFile() {
        return null
    }

    @Override
    File getOutputFile() {
        return this.solution.solutionFile.location
    }

    private class ConfigurationObjectGenerator extends PersistableConfigurationObjectGenerator<VisualStudioSolutionFile> {
        public VisualStudioSolutionFile create() {
            return new VisualStudioSolutionFile()
        }

        public void configure(VisualStudioSolutionFile solutionFile) {
            DefaultVisualStudioSolution solution = getSolution() as DefaultVisualStudioSolution
            solutionFile.setMainProject(solution.rootProject)
            solution.solutionConfigurations.each { solutionConfig ->
                solutionFile.addSolutionConfiguration(solutionConfig.name, solution.getProjectConfigurations(solutionConfig))
            }

            solution.solutionFile.textActions.each {
                solutionFile.actions << it
            }
        }
    }
}