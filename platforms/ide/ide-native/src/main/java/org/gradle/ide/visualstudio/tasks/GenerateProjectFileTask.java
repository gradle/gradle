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
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject;
import org.gradle.ide.visualstudio.internal.VisualStudioProjectConfiguration;
import org.gradle.ide.visualstudio.internal.VisualStudioTargetBinary;
import org.gradle.ide.visualstudio.tasks.internal.RelativeFileNameTransformer;
import org.gradle.ide.visualstudio.tasks.internal.VisualStudioProjectFile;
import org.gradle.internal.serialization.Cached;
import org.gradle.plugins.ide.api.XmlGeneratorTask;
import org.gradle.plugins.ide.internal.IdePlugin;
import org.gradle.util.internal.VersionNumber;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.util.internal.CollectionUtils.collect;

/**
 * Task for generating a Visual Studio project file (e.g. {@code foo.vcxproj}).
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
public abstract class GenerateProjectFileTask extends XmlGeneratorTask<VisualStudioProjectFile> {
    private transient DefaultVisualStudioProject visualStudioProject;
    private final Cached<ProjectSpec> spec = Cached.of(this::calculateSpec);
    private final Provider<File> outputFile = getProject().provider(SerializableLambdas.callable(() -> visualStudioProject.getProjectFile().getLocation()));
    private final Cached<Transformer<@org.jetbrains.annotations.NotNull String, File>> transformer = Cached.of(this::getTransformer);
    private String gradleExe;
    private String gradleArgs;

    @Inject
    public GenerateProjectFileTask(DefaultVisualStudioProject visualStudioProject) {
        setVisualStudioProject(visualStudioProject);
    }

    @Override
    protected boolean getIncremental() {
        return true;
    }

    public void initGradleCommand() {
        final File gradlew = new File(IdePlugin.toGradleCommand(getProject()));
        getConventionMapping().map("gradleExe", new Callable<Object>() {
            @Override
            public Object call() {
                final String rootDir = transformer.get().transform(getProject().getRootDir());
                String args = "";
                if (!rootDir.equals(".")) {
                    args = " -p \"" + rootDir + "\"";
                }

                if (gradlew.isFile()) {
                    return "\"" + transformer.get().transform(gradlew) + "\"" + args;
                }

                return "\"gradle\"" + args;
            }
        });
    }

    @Internal
    public Transformer<@org.jetbrains.annotations.NotNull String, File> getTransformer() {
        return RelativeFileNameTransformer.forFile(getProject().getRootDir(), visualStudioProject.getProjectFile().getLocation());
    }

    public void setVisualStudioProject(VisualStudioProject vsProject) {
        this.visualStudioProject = (DefaultVisualStudioProject) vsProject;
    }

    @Internal
    public VisualStudioProject getVisualStudioProject() {
        return visualStudioProject;
    }

    /**
     * Returns the {@link ProjectSpec} for this task.
     *
     * @since 8.11
     */
    @Nested
    @Incubating
    protected ProjectSpec getSpec() {
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

    @Override
    protected VisualStudioProjectFile create() {
        return new VisualStudioProjectFile(getXmlTransformer(), transformer.get());
    }

    @Override
    protected void configure(VisualStudioProjectFile projectFile) {
        ProjectSpec spec = this.spec.get();

        projectFile.setGradleCommand(spec.gradleCommand);
        projectFile.setProjectUuid(DefaultVisualStudioProject.getUUID(outputFile.get()));
        projectFile.setVisualStudioVersion(spec.visualStudioVersion.get());
        projectFile.setSdkVersion(spec.sdkVersion.get());

        for (File sourceFile : spec.sourceFiles) {
            projectFile.addSourceFile(sourceFile);
        }

        for (File resourceFile : spec.resourceFiles) {
            projectFile.addResource(resourceFile);
        }

        for (File headerFile : spec.headerFiles) {
            projectFile.addHeaderFile(headerFile);
        }

        if (spec.warning != null) {
            getLogger().warn(spec.warning);
        }
        for (VisualStudioProjectFile.ConfigurationSpec configuration : spec.configurations) {
            projectFile.addConfiguration(configuration);
        }

        for (Action<? super XmlProvider> xmlAction : spec.xmlActions) {
            getXmlTransformer().addAction(xmlAction);
        }
    }

    private String buildGradleCommand() {
        String exe = getGradleExe();
        String args = getGradleArgs();
        if (args == null || args.trim().length() == 0) {
            return exe;
        } else {
            return exe + " " + args.trim();
        }
    }

    @Internal
    public String getGradleExe() {
        return gradleExe;
    }

    public void setGradleExe(String gradleExe) {
        this.gradleExe = gradleExe;
    }

    @Nullable
    @Internal
    public String getGradleArgs() {
        return gradleArgs;
    }

    public void setGradleArgs(@Nullable String gradleArgs) {
        this.gradleArgs = gradleArgs;
    }

    private ProjectSpec calculateSpec() {
        String warning;
        if (visualStudioProject.getConfigurations().stream().noneMatch(it -> it.isBuildable())) {
            warning = "'" + visualStudioProject.getComponentName() + "' component in project '" + getProject().getPath() + "' is not buildable.";
        } else {
            warning = null;
        }

        List<VisualStudioProjectFile.ConfigurationSpec> configurations = new ArrayList<>();
        for (VisualStudioProjectConfiguration configuration : visualStudioProject.getConfigurations()) {
            VisualStudioTargetBinary targetBinary = configuration.getTargetBinary();
            if (targetBinary != null) {
                configurations.add(new VisualStudioProjectFile.ConfigurationSpec(
                    configuration.getName(),
                    configuration.getConfigurationName(),
                    configuration.getProject().getName(),
                    configuration.getPlatformName(),
                    configuration.getType(),
                    configuration.isBuildable(),
                    targetBinary.isDebuggable(),
                    targetBinary.getIncludePaths(),
                    targetBinary.getBuildTaskPath(),
                    targetBinary.getCleanTaskPath(),
                    targetBinary.getCompilerDefines(),
                    targetBinary.getOutputFile(),
                    targetBinary.getLanguageStandard()
                ));
            } else {
                configurations.add(new VisualStudioProjectFile.ConfigurationSpec(
                    configuration.getName(),
                    configuration.getConfigurationName(),
                    configuration.getProject().getName(),
                    configuration.getPlatformName(),
                    configuration.getType(),
                    configuration.isBuildable(),
                    false,
                    Collections.emptySet(),
                    null,
                    null,
                    Collections.emptyList(),
                    null,
                    null
                ));
            }
        }

        return new ProjectSpec(
            visualStudioProject.getVisualStudioVersion(),
            visualStudioProject.getSdkVersion(),
            visualStudioProject.getSourceFiles(),
            visualStudioProject.getResourceFiles(),
            visualStudioProject.getHeaderFiles(),
            buildGradleCommand(),
            warning,
            configurations,
            visualStudioProject.getProjectFile().getXmlActions()
        );
    }

    /**
     * The data to use to generate the project file.
     *
     * @since 8.11
     */
    @Incubating
    protected static class ProjectSpec {
        final Provider<VersionNumber> visualStudioVersion;
        final Provider<VersionNumber> sdkVersion;
        final FileCollection sourceFiles;
        final Set<File> resourceFiles;
        final FileCollection headerFiles;
        final String gradleCommand;
        final String warning;
        final List<VisualStudioProjectFile.ConfigurationSpec> configurations;
        final List<Action<? super XmlProvider>> xmlActions;

        private ProjectSpec(
            Provider<VersionNumber> visualStudioVersion,
            Provider<VersionNumber> sdkVersion,
            FileCollection sourceFiles,
            Set<File> resourceFiles,
            FileCollection headerFiles,
            String gradleCommand,
            @Nullable String warning,
            List<VisualStudioProjectFile.ConfigurationSpec> configurations,
            List<Action<? super XmlProvider>> xmlActions
        ) {
            this.visualStudioVersion = visualStudioVersion;
            this.sdkVersion = sdkVersion;
            this.sourceFiles = sourceFiles;
            this.resourceFiles = resourceFiles;
            this.headerFiles = headerFiles;
            this.gradleCommand = gradleCommand;
            this.warning = warning;
            this.configurations = configurations;
            this.xmlActions = xmlActions;
        }

        /**
         * The VS version for this project.
         *
         * @since 8.11
         */
        @Input
        @Incubating
        public Provider<String> getVisualStudioVersion() {
            return visualStudioVersion.map(VersionNumber::toString);
        }

        /**
         * The SDK version for this project.
         * @since 8.11
         */
        @Input
        @Incubating
        public Provider<String> getSdkVersion() {
            return sdkVersion.map(VersionNumber::toString);
        }

        /**
         * The source files for this project.
         *
         * @since 8.11
         */
        @Input
        @Incubating
        public Provider<Set<String>> getSourceFilePaths() {
            return sourceFiles.getElements().map(files -> collect(files, file -> file.getAsFile().getAbsolutePath()));
        }

        /**
         * The resource files for this project.
         *
         * @since 8.11
         */
        @Input
        @Incubating
        public Set<String> getResourceFilePaths() {
            return collect(resourceFiles, File::getAbsolutePath);
        }

        /**
         * The header files for this project.
         * @since 8.11
         */
        @Input
        @Incubating
        public Provider<Set<String>> getHeaderFilesPaths() {
            return headerFiles.getElements().map(files -> collect(files, file -> file.getAsFile().getAbsolutePath()));
        }

        /**
         * Command to use to run Gradle from the project.
         *
         * @since 8.11
         */
        @Input
        @Incubating
        public String getGradleCommand() {
            return gradleCommand;
        }

        /**
         * Warning to report to users after generating the project file.
         *
         * @since 8.11
         */
        @Input
        @Optional
        @Incubating
        public String getWarning() {
            return warning;
        }

        /**
         * Configurations to include in the project.
         *
         * @since 8.11
         */
        @Nested
        @Incubating
        public List<VisualStudioProjectFile.ConfigurationSpec> getConfigurations() {
            return configurations;
        }

        /**
         * Additional XML generation actions.
         *
         * @since 8.11
         */
        @Nested
        @Incubating
        public List<Action<? super XmlProvider>> getXmlActions() {
            return xmlActions;
        }
    }
}
