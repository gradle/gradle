/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.plugins.antlr;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.antlr.internal.DefaultAntlrSourceDirectorySet;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A plugin for adding Antlr support to {@link org.gradle.api.plugins.JavaPlugin java projects}.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/antlr_plugin.html">ANTLR plugin reference</a>
 */
@SuppressWarnings("JavadocReference")
public abstract class AntlrPlugin implements Plugin<Project> {
    /**
     * Adding a dependency to the antlr configuration will add it both to the classpath used for ANTLR code generation
     * and to the project's api classpath (meaning code generation libraries will leak into the runtime classpath). It
     * is generally preferable to use the antlrTool classpath.
     */
    public static final String ANTLR_CONFIGURATION_NAME = "antlr";
    /**
     * The antlrTool configuration is where users should add the antlr library used for running ANTLR code generation.
     * When using it, the corresponding runtime library for ANTLR (antlr for ANTLR 2, antlr-runtime for ANTLR 3, or
     * antlr4-runtime for ANTLR 4) should be added as an api or implementation dependency.
     *
     * @since 8.11
     */
    @Incubating
    public static final String ANTLR_TOOL_CONFIGURATION_NAME = "antlrTool";
    /**
     * The antlrToolClasspath configuration is the resolvable counterpart of antlrTool.
     *
     * @since 8.11
     */
    @Incubating
    public static final String ANTLR_TOOL_CLASSPATH_CONFIGURATION_NAME = "antlrToolClasspath";
    private static final String DEFAULT_ANTLR_ARTIFACT_COORDINATES = "antlr:antlr:2.7.7@jar";
    private final ObjectFactory objectFactory;

    @Inject
    public AntlrPlugin(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);

        final Configuration antlrConfiguration = ((ProjectInternal) project).getConfigurations().resolvableDependencyScopeUnlocked(ANTLR_CONFIGURATION_NAME)
            .setVisible(false);
        final Configuration antlrToolConfiguration = ((ProjectInternal) project).getConfigurations().dependencyScope(ANTLR_TOOL_CONFIGURATION_NAME).get();
        final Configuration antlrToolClasspathConfiguration = ((ProjectInternal) project).getConfigurations().resolvable(ANTLR_TOOL_CLASSPATH_CONFIGURATION_NAME).get();

        // Support people using the deprecated antlr configuration
        antlrToolConfiguration.extendsFrom(antlrConfiguration);
        antlrToolClasspathConfiguration.extendsFrom(antlrToolConfiguration);

        AtomicBoolean hasWarnedAboutLegacyConf = new AtomicBoolean(false);
        antlrConfiguration.getDependencies().configureEach(_dependency -> {
            if (!hasWarnedAboutLegacyConf.getAndSet(true)) {
                project.getLogger().warn("TODO write warning: Used the antlr configuration, should change that to antlrTool");
            }
        });

        antlrConfiguration.defaultDependencies(dependencies -> {
            if (antlrToolConfiguration.getDependencies().isEmpty()) {
                project.getLogger().warn("TODO write warning: Used the default 2.7.7 version of antlr, should add explicit dependencies");
                hasWarnedAboutLegacyConf.set(true);
                dependencies.add(project.getDependencies().create(DEFAULT_ANTLR_ARTIFACT_COORDINATES));
            }
        });

        Configuration apiConfiguration = project.getConfigurations().getByName(JvmConstants.API_CONFIGURATION_NAME);
        apiConfiguration.extendsFrom(antlrConfiguration);

        // Wire the antlrToolClasspath configuration into all antlr tasks
        project.getTasks().withType(AntlrTask.class).configureEach(antlrTask -> antlrTask.getConventionMapping().map("antlrClasspath", () -> project.getConfigurations().getByName(ANTLR_TOOL_CLASSPATH_CONFIGURATION_NAME)));

        project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().all(
            new Action<SourceSet>() {
                @Override
                public void execute(final SourceSet sourceSet) {
                    // for each source set we will:
                    // 1) Add a new 'antlr' virtual directory mapping
                    AntlrSourceDirectorySet antlrSourceSet = createAntlrSourceDirectorySet(((DefaultSourceSet) sourceSet).getDisplayName(), objectFactory);
                    sourceSet.getExtensions().add(AntlrSourceDirectorySet.class, AntlrSourceDirectorySet.NAME, antlrSourceSet);
                    final String srcDir = "src/" + sourceSet.getName() + "/antlr";
                    antlrSourceSet.srcDir(srcDir);
                    sourceSet.getAllSource().source(antlrSourceSet);

                    // 2) create an AntlrTask for this sourceSet following the gradle
                    //    naming conventions via call to sourceSet.getTaskName()
                    final String taskName = sourceSet.getTaskName("generate", "GrammarSource");

                    // 3) Set up the Antlr output directory (adding to javac inputs!)
                    final String outputDirectoryName = project.getBuildDir() + "/generated-src/antlr/" + sourceSet.getName();
                    final File outputDirectory = new File(outputDirectoryName);
                    sourceSet.getJava().srcDir(outputDirectory);

                    project.getTasks().register(taskName, AntlrTask.class, new Action<AntlrTask>() {
                        @Override
                        public void execute(AntlrTask antlrTask) {
                            antlrTask.setDescription("Processes the " + sourceSet.getName() + " Antlr grammars.");
                            // 4) set up convention mapping for default sources (allows user to not have to specify)
                            antlrTask.setSource(antlrSourceSet);
                            antlrTask.setOutputDirectory(outputDirectory);
                        }
                    });

                    // 5) register fact that antlr should be run before compiling
                    project.getTasks().named(sourceSet.getCompileJavaTaskName(), new Action<Task>() {
                        @Override
                        public void execute(Task task) {
                            task.dependsOn(taskName);
                        }
                    });
                }
            });
    }

    private static AntlrSourceDirectorySet createAntlrSourceDirectorySet(String parentDisplayName, ObjectFactory objectFactory) {
        String name = parentDisplayName + ".antlr";
        String displayName = parentDisplayName + " Antlr source";
        AntlrSourceDirectorySet antlrSourceSet = objectFactory.newInstance(DefaultAntlrSourceDirectorySet.class, objectFactory.sourceDirectorySet(name, displayName));
        antlrSourceSet.getFilter().include("**/*.g");
        antlrSourceSet.getFilter().include("**/*.g4");
        return antlrSourceSet;
    }
}
