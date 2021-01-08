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

package org.gradle.ide.visualstudio.tasks;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.ide.visualstudio.TextProvider;
import org.gradle.ide.visualstudio.VisualStudioSolution;
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioSolution;
import org.gradle.ide.visualstudio.tasks.internal.VisualStudioSolutionFile;
import org.gradle.plugins.ide.api.GeneratorTask;
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObjectGenerator;

import java.io.File;

/**
 * Task for generating a Visual Studio solution file (e.g. {@code foo.sln}).
 */
@Incubating
public class GenerateSolutionFileTask extends GeneratorTask<VisualStudioSolutionFile> {
    private DefaultVisualStudioSolution solution;

    public GenerateSolutionFileTask() {
        generator = new ConfigurationObjectGenerator();
    }

    @Override
    protected boolean getIncremental() {
        return true;
    }

    public void setVisualStudioSolution(VisualStudioSolution solution) {
        this.solution = (DefaultVisualStudioSolution) solution;
    }

    @Nested
    public VisualStudioSolution getSolution() {
        return solution;
    }

    @Override
    @Internal
    public File getInputFile() {
        return null;
    }

    @Override
    @OutputFile
    public File getOutputFile() {
        return this.solution.getSolutionFile().getLocation();
    }

    private class ConfigurationObjectGenerator extends PersistableConfigurationObjectGenerator<VisualStudioSolutionFile> {
        @Override
        public VisualStudioSolutionFile create() {
            return new VisualStudioSolutionFile();
        }

        @Override
        public void configure(final VisualStudioSolutionFile solutionFile) {
            DefaultVisualStudioSolution solution = (DefaultVisualStudioSolution) getSolution();

            solutionFile.setProjects(solution.getProjects());

            for (Action<? super TextProvider> textAction : solution.getSolutionFile().getTextActions()) {
                solutionFile.getActions().add(textAction);
            }
        }
    }
}
