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
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.ide.visualstudio.TextProvider;
import org.gradle.ide.visualstudio.VisualStudioSolution;
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioSolution;
import org.gradle.ide.visualstudio.internal.VisualStudioProjectConfigurationMetadata;
import org.gradle.ide.visualstudio.internal.VisualStudioProjectMetadata;
import org.gradle.ide.visualstudio.tasks.internal.VisualStudioSolutionFile;
import org.gradle.internal.serialization.Cached;
import org.gradle.plugins.ide.api.GeneratorTask;
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObjectGenerator;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Task for generating a Visual Studio solution file (e.g. {@code foo.sln}).
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
public abstract class GenerateSolutionFileTask extends GeneratorTask<VisualStudioSolutionFile> {
    private transient DefaultVisualStudioSolution solution;
    private final Provider<File> outputFile = getProject().provider(SerializableLambdas.callable(() -> solution.getSolutionFile().getLocation()));
    private final Cached<SolutionSpec> spec = Cached.of(this::calculateSpec);

    @Inject
    public GenerateSolutionFileTask(DefaultVisualStudioSolution solution) {
        generator = new ConfigurationObjectGenerator();
        setVisualStudioSolution(solution);
    }

    @Override
    protected boolean getIncremental() {
        return true;
    }

    public void setVisualStudioSolution(VisualStudioSolution solution) {
        this.solution = (DefaultVisualStudioSolution) solution;
    }

    @Internal
    public VisualStudioSolution getSolution() {
        return solution;
    }

    /**
     * The {@link SolutionSpec} for this task.
     *
     * @since 8.11
     */
    @Nested
    @Incubating
    protected SolutionSpec getSpec() {
        return spec.get();
    }

    @Override
    @Internal
    public File getInputFile() {
        return null;
    }

    @Override
    @OutputFile
    public File getOutputFile() {
        return outputFile.get();
    }

    private SolutionSpec calculateSpec() {
        DefaultVisualStudioSolution solution = (DefaultVisualStudioSolution) getSolution();
        List<VisualStudioSolutionFile.ProjectSpec> projects = new ArrayList<>();
        for (VisualStudioProjectMetadata project : solution.getProjects()) {
            List<VisualStudioSolutionFile.ConfigurationSpec> configurations = new ArrayList<>();
            for (VisualStudioProjectConfigurationMetadata configuration : project.getConfigurations()) {
                configurations.add(new VisualStudioSolutionFile.ConfigurationSpec(configuration.getName(), configuration.isBuildable()));
            }
            projects.add(new VisualStudioSolutionFile.ProjectSpec(project.getName(), project.getFile(), configurations));
        }
        return new SolutionSpec(projects, solution.getSolutionFile().getTextActions());
    }

    private class ConfigurationObjectGenerator extends PersistableConfigurationObjectGenerator<VisualStudioSolutionFile> {
        @Override
        public VisualStudioSolutionFile create() {
            return new VisualStudioSolutionFile();
        }

        @Override
        public void configure(final VisualStudioSolutionFile solutionFile) {
            SolutionSpec spec = GenerateSolutionFileTask.this.spec.get();

            solutionFile.setProjects(spec.projects);

            for (Action<? super TextProvider> textAction : spec.textActions) {
                solutionFile.getActions().add(textAction);
            }
        }
    }

    /**
     * The data to use to generate the solution file.
     *
     * @since 8.11
     */
    @Incubating
    protected static class SolutionSpec {
        final List<VisualStudioSolutionFile.ProjectSpec> projects;
        final List<Action<? super TextProvider>> textActions;

        private SolutionSpec(List<VisualStudioSolutionFile.ProjectSpec> projects, List<Action<? super TextProvider>> textActions) {
            this.projects = projects;
            this.textActions = textActions;
        }

        /**
         * Projects to include in the solution.
         *
         * @since 8.11
         */
        @Nested
        @Incubating
        public List<VisualStudioSolutionFile.ProjectSpec> getProjects() {
            return projects;
        }

        /**
         * Additional text generation actions.
         *
         * @since 8.11
         */
        @Nested
        @Incubating
        public List<Action<? super TextProvider>> getTextActions() {
            return textActions;
        }
    }
}
