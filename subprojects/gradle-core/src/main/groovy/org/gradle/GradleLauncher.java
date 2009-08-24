/*
 * Copyright 2007 the original author or authors.
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
package org.gradle;

import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.BuildExecuter;
import org.gradle.initialization.BuildLoader;
import org.gradle.initialization.DefaultLoggingConfigurer;
import org.gradle.initialization.IGradlePropertiesLoader;
import org.gradle.initialization.SettingsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>{@code GradleLauncher} is the main entry point for embedding Gradle. You use this class to manage a Gradle build, as
 * follows:</p>
 * <ol>
 * <li>Optionally create a {@link StartParameter} instance and configure it with the desired properties. The properties of {@code
 * StartParameter} generally correspond to the command-line options of Gradle.</li>
 * <li>Obtain a {@code GradleLauncher} instance by calling {@link #newInstance}, passing in the {@code StartParameter}, or an array of
 * Strings that will be treated as command line arguments.</li>
 * <li>Call {@link #run} to execute the build. This will return a {@link BuildResult}. Note that if the build fails, the
 * resulting exception will be contained in the {@code BuildResult}.</li>
 * <li>Query the build result. You might want to call {@link BuildResult#rethrowFailure()} to rethrow any build failure.</li>
 * </ol>
 *
 * @author Hans Dockter
 */
public class GradleLauncher {
    private static final Logger logger = LoggerFactory.getLogger(GradleLauncher.class);

    private static GradleFactory factory = new DefaultGradleFactory(new DefaultLoggingConfigurer(), new DefaultCommandLine2StartParameterConverter());

    private GradleInternal gradle;
    private SettingsHandler settingsHandler;
    private IGradlePropertiesLoader gradlePropertiesLoader;
    private BuildLoader buildLoader;
    private BuildConfigurer buildConfigurer;

    /**
     * Creates a new instance.  Don't call this directly, use {@link #newInstance(StartParameter)} or
     * {@link #newInstance(String[])} instead.  Note that this method is package-protected to discourage
     * it's direct use.
     */
    public GradleLauncher(GradleInternal gradle, SettingsHandler settingsHandler,
                  IGradlePropertiesLoader gradlePropertiesLoader,
                  BuildLoader buildLoader, BuildConfigurer buildConfigurer) {
        this.gradle = gradle;
        this.settingsHandler = settingsHandler;
        this.gradlePropertiesLoader = gradlePropertiesLoader;
        this.buildLoader = buildLoader;
        this.buildConfigurer = buildConfigurer;
    }

    /**
     * <p>Executes the build for this GradleLauncher instance and returns the result. Note that when the build fails, the
     * exception is available using {@link BuildResult#getFailure()}.</p>
     *
     * @return The result. Never returns null.
     */
    public BuildResult run() {
        return doBuild(new RunSpecification() {
            public void run(GradleInternal gradle, ProjectDescriptor rootProject) {
                loadAndConfigureAndRun(gradle, rootProject);
            }
        });
    }

    /**
     * Evaluates the settings and all the projects. The information about available tasks and projects is accessible
     * via the {@link org.gradle.api.invocation.Gradle#getRootProject()} object.
     *
     * @return A BuildResult object. Never returns null.
     */
    public BuildResult getBuildAnalysis() {
        return doBuild(new RunSpecification() {
            public void run(GradleInternal gradle, ProjectDescriptor rootProject) {
                loadAndConfigure(gradle, rootProject);
            }
        });
    }

    /**
     * Evaluates the settings and all the projects. The information about available tasks and projects is accessible via the
     * {@link org.gradle.api.invocation.Gradle#getRootProject()} object. Fills the execution plan without running the build.
     * The tasks to be executed tasks are available via {@link org.gradle.api.invocation.Gradle#getTaskGraph()}.
     *
     * @return A BuildResult object. Never returns null.
     */
    public BuildResult getBuildAndRunAnalysis() {
        return doBuild(new RunSpecification() {
            public void run(GradleInternal gradle, ProjectDescriptor rootProject) {
                loadAndConfigureAndRun(gradle, rootProject);
            }
        });
    }

    private BuildResult doBuild(RunSpecification runSpecification) {
        gradle.getBuildListenerBroadcaster().buildStarted(gradle);

        SettingsInternal settings = null;
        Throwable failure = null;
        try {
            settings = settingsHandler.findAndLoadSettings(gradle, gradlePropertiesLoader);
            gradle.getBuildListenerBroadcaster().settingsEvaluated(settings);
            
            runSpecification.run(gradle, settings.getRootProject());
        } catch (Throwable t) {
            failure = t;
        }
        BuildResult buildResult = new BuildResult(settings, gradle, failure);
        gradle.getBuildListenerBroadcaster().buildFinished(buildResult);

        return buildResult;
    }

    private void loadAndConfigureAndRun(GradleInternal gradle, ProjectDescriptor rootProject) {
        loadAndConfigure(gradle, rootProject);
        attachTaskGraphListener();

        // Execute build
        BuildExecuter executer = gradle.getStartParameter().getBuildExecuter();

        executer.select(gradle);
        logger.info(String.format("Starting build for %s.", executer.getDisplayName()));
        executer.execute();
    }

    private void loadAndConfigure(GradleInternal gradle, ProjectDescriptor rootProject) {
        // Load build
        buildLoader.load(rootProject, gradle, gradlePropertiesLoader.getGradleProperties());
        gradle.getBuildListenerBroadcaster().projectsLoaded(gradle);

        // Configure build
        buildConfigurer.process(gradle.getRootProject());
        gradle.getBuildListenerBroadcaster().projectsEvaluated(gradle);
    }

    private void attachTaskGraphListener() {
        final GradleInternal theGradle = gradle;
        theGradle.getTaskGraph().addTaskExecutionGraphListener(new TaskExecutionGraphListener() {
            public void graphPopulated(TaskExecutionGraph graph) {
                assert theGradle.getTaskGraph() == graph;
                theGradle.getBuildListenerBroadcaster().taskGraphPopulated(graph);
            }
        });
    }

    /**
     * Returns a GradleLauncher instance based on the passed start parameter.
     *
     * @param startParameter The start parameter object the GradleLauncher instance is initialized with
     */
    public static GradleLauncher newInstance(final StartParameter startParameter) {
        return factory.newInstance(startParameter);
    }

    /**
     * Returns a GradleLauncher instance based on the passed command line syntax arguments. Certain command line arguments
     * won't have any effect if you choose this method (e.g. -v, -h). If you want to act upon, you better
     * use {@link #createStartParameter(String[])} in conjunction with {@link #newInstance(String[])}.
     *
     * @param commandLineArgs A String array where each element denotes an entry of the Gradle command line syntax
     */
    public static GradleLauncher newInstance(final String[] commandLineArgs) {
        return factory.newInstance(commandLineArgs);
    }

    /**
     * Returns a StartParameter object out of command line syntax arguments. Every possible command line
     * option has it associated field in the StartParameter object.
     *
     * @param commandLineArgs A String array where each element denotes an entry of the Gradle command line syntax
     */
    public static StartParameter createStartParameter(final String[] commandLineArgs) {
        return factory.createStartParameter(commandLineArgs);
    }

    // This is used for mocking
    public static void injectCustomFactory(GradleFactory gradleFactory) {
        factory = gradleFactory == null ? new DefaultGradleFactory(new DefaultLoggingConfigurer(), new DefaultCommandLine2StartParameterConverter()) : gradleFactory;
    }

    /**
     * <p>Adds a {@link BuildListener} to this Gradle's build instance. The listener is notified of events which occur
     * during the execution of the build.</p>
     *
     * @param buildListener The listener to add.
     */
    public void addBuildListener(BuildListener buildListener)
    {
        gradle.addBuildListener(buildListener);
    }

    private static interface RunSpecification {
        void run(GradleInternal gradle, ProjectDescriptor rootProject);
    }
}
