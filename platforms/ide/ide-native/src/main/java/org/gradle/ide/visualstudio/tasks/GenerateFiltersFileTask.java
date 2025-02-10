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
import org.gradle.api.Transformer;
import org.gradle.api.XmlProvider;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject;
import org.gradle.ide.visualstudio.tasks.internal.RelativeFileNameTransformer;
import org.gradle.ide.visualstudio.tasks.internal.VisualStudioFiltersFile;
import org.gradle.internal.serialization.Cached;
import org.gradle.plugins.ide.api.XmlGeneratorTask;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Set;

import static org.gradle.util.internal.CollectionUtils.collect;

/**
 * Task for generating a Visual Studio filters file (e.g. {@code foo.vcxproj.filters}).
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
public abstract class GenerateFiltersFileTask extends XmlGeneratorTask<VisualStudioFiltersFile> {
    private transient DefaultVisualStudioProject visualStudioProject;
    private final Provider<File> outputFile = getProject().provider(SerializableLambdas.callable(() -> visualStudioProject.getFiltersFile().getLocation()));
    private final Cached<FiltersSpec> spec = Cached.of(this::calculateSpec);

    @Inject
    public GenerateFiltersFileTask(DefaultVisualStudioProject visualStudioProject) {
        setVisualStudioProject(visualStudioProject);
    }

    @Override
    protected boolean getIncremental() {
        return true;
    }

    public void setVisualStudioProject(VisualStudioProject vsProject) {
        this.visualStudioProject = (DefaultVisualStudioProject) vsProject;
    }

    @Internal
    public VisualStudioProject getVisualStudioProject() {
        return visualStudioProject;
    }

    /**
     * Returns the {@link FiltersSpec} for this task.
     *
     * @since 8.11
     */
    @Nested
    @Incubating
    protected FiltersSpec getFilterSpec() {
        return spec.get();
    }

    @Override
    public File getInputFile() {
        return null;
    }

    @Override
    public File getOutputFile() {
        return outputFile.get();
    }

    @Override
    protected void configure(final VisualStudioFiltersFile filtersFile) {
        FiltersSpec spec = this.spec.get();

        for (File sourceFile : spec.sourceFiles) {
            filtersFile.addSource(sourceFile);
        }

        for (File headerFile : spec.headerFiles) {
            filtersFile.addHeader(headerFile);
        }

        for (Action<? super XmlProvider> xmlAction : spec.actions) {
            getXmlTransformer().addAction(xmlAction);
        }
    }

    @Override
    protected VisualStudioFiltersFile create() {
        return new VisualStudioFiltersFile(getXmlTransformer(), spec.get().fileNameTransformer);
    }

    private FiltersSpec calculateSpec() {
        return new FiltersSpec(visualStudioProject.getSourceFiles(),
            visualStudioProject.getHeaderFiles(),
            visualStudioProject.getFiltersFile().getXmlActions(),
            RelativeFileNameTransformer.forFile(getProject().getRootDir(), visualStudioProject.getFiltersFile().getLocation()));
    }

    /**
     * The data to use to generate the filters file.
     *
     * @since 8.11
     */
    @Incubating
    protected static class FiltersSpec {
        private final FileCollection sourceFiles;
        private final FileCollection headerFiles;
        private final List<Action<? super XmlProvider>> actions;
        private final Transformer<String, File> fileNameTransformer;

        private FiltersSpec(FileCollection sourceFiles, FileCollection headerFiles, List<Action<? super XmlProvider>> actions, Transformer<String, File> fileNameTransformer) {
            this.sourceFiles = sourceFiles;
            this.headerFiles = headerFiles;
            this.actions = actions;
            this.fileNameTransformer = fileNameTransformer;
        }

        /**
         * The source files to include in the filter.
         *
         * @since 8.11
         */
        @Input
        @Incubating
        public Provider<Set<String>> getSourceFilePaths() {
            return sourceFiles.getElements().map(files -> collect(files, file -> file.getAsFile().getAbsolutePath()));
        }

        /**
         * The header files to include in the filter.
         *
         * @since 8.11
         */
        @Input
        @Incubating
        public Provider<Set<String>> getHeaderFilesPaths() {
            return headerFiles.getElements().map(files -> collect(files, file -> file.getAsFile().getAbsolutePath()));
        }

        /**
         * Additional XML generation actions.
         *
         * @since 8.11
         */
        @Nested
        @Incubating
        public List<Action<? super XmlProvider>> getActions() {
            return actions;
        }
    }
}
