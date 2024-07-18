/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks;

import org.gradle.StartParameter;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.provider.Property;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.List;

import static org.gradle.internal.build.NestedRootBuildRunner.createStartParameterForNewBuild;
import static org.gradle.internal.build.NestedRootBuildRunner.runNestedRootBuild;

/**
 * Executes a Gradle build.
 */
@DisableCachingByDefault(because = "Child Gradle build will do its own caching")
public abstract class GradleBuild extends ConventionTask {
    public GradleBuild() {
        StartParameter startParameter = createStartParameterForNewBuild(getServices());
        startParameter.setCurrentDir(getProject().getProjectDir());
        getStartParameter().convention(startParameter);
    }

    /**
     * Returns the set of parameters that will be used to execute the build.
     *
     * @return the parameters.
     */
    @Input
    @ReplacesEagerProperty(adapter = StartParameterAdapter.class)
    public abstract Property<StartParameter> getStartParameter();

    /**
     * Returns the build file that should be used for this build. Defaults to {@value
     * org.gradle.api.Project#DEFAULT_BUILD_FILE} in the project directory.
     *
     * @return The build file. May be null.
     * @deprecated Use {@code getStartParameter().get().getCurrentDir()} instead to get the root of the nested build.
     * This method will be removed in Gradle 9.0.
     */
    @Nullable
    @Optional
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputFile
    @Deprecated
    public File getBuildFile() {
        logBuildFileDeprecation();
        return DeprecationLogger.whileDisabled(() ->
            getStartParameter().get().getBuildFile()
        );
    }

    /**
     * Sets the build file that should be used for this build.
     *
     * @param file The build file. May be null to use the default build file for the build.
     * @since 4.0
     * @deprecated Use {@code getStartParameter().get().setCurrentDir()} instead to set the root of the nested build.
     * This method will be removed in Gradle 9.0.
     */
    @Deprecated
    public void setBuildFile(@Nullable File file) {
        setBuildFile((Object) file);
    }

    /**
     * Sets the build file that should be used for this build.
     *
     * @param file The build file. May be null to use the default build file for the build.
     * @deprecated Use {@code getStartParameter().get().setCurrentDir()} instead to set the root of the nested build.
     * This method will be removed in Gradle 9.0.
     */
    @Deprecated
    public void setBuildFile(@Nullable Object file) {
        logBuildFileDeprecation();
        DeprecationLogger.whileDisabled(() ->
            getStartParameter().get().setBuildFile(getProject().file(file))
        );
    }

    private void logBuildFileDeprecation() {
        DeprecationLogger.deprecateProperty(GradleBuild.class, "buildFile")
            .withContext("Setting custom build file to select the root of the nested build has been deprecated.")
            .withAdvice("Please use 'dir' to specify the root of the nested build instead.")
            .replaceWith("dir")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "configuring_custom_build_layout")
            .nagUser();
    }

    /**
     * The build name to use for the nested build.
     * <p>
     * If no value is specified, the name of the directory of the build will be used.
     *
     * @return the build name to use for the nested build (or null if the default is to be used)
     * @since 6.0
     */
    @Internal
    @Optional
    @ReplacesEagerProperty
    public abstract Property<String> getBuildName();

    @TaskAction
    void build() {
        // TODO: Allow us to inject plugins into nested builds too.
        runNestedRootBuild(getBuildName().getOrNull(), (StartParameterInternal) getStartParameter(), getServices());
    }

    static class StartParameterAdapter {
        /**
         * Returns the full set of parameters that will be used to execute the build.
         *
         * @return the parameters. Never returns null.
         */
        @BytecodeUpgrade
        static StartParameter getStartParameter(GradleBuild task) {
            return task.getStartParameter().get();
        }

        /**
         * Sets the full set of parameters that will be used to execute the build.
         *
         * @param startParameter the parameters. Should not be null.
         */
        @BytecodeUpgrade
        static void setStartParameter(GradleBuild task, StartParameter startParameter) {
            task.getStartParameter().set(startParameter);
        }

        /**
         * Returns the project directory for the build. Defaults to the project directory.
         *
         * @return The project directory. Never returns null.
         */
        @Internal
        @BytecodeUpgrade
        static File getDir(GradleBuild task) {
            return task.getStartParameter().get().getCurrentDir();
        }

        /**
         * Sets the project directory for the build.
         *
         * @param dir The project directory. Should not be null.
         * @since 4.0
         */
        @BytecodeUpgrade
        static void setDir(GradleBuild task, File dir) {
            setDir(task, (Object) dir);
        }

        /**
         * Sets the project directory for the build.
         *
         * @param dir The project directory. Should not be null.
         */
        @BytecodeUpgrade
        static void setDir(GradleBuild task, Object dir) {
            task.getStartParameter().get().setCurrentDir(task.getProject().file(dir));
        }

        /**
         * Returns the tasks that should be executed for this build.
         *
         * @return The sequence. May be empty. Never returns null.
         */
        @BytecodeUpgrade
        static List<String> getTasks(GradleBuild task) {
            return task.getStartParameter().get().getTaskNames();
        }

        /**
         * Sets the tasks that should be executed for this build.
         *
         * @param tasks The task names. May be empty or null to use the default tasks for the build.
         * @since 4.0
         */
        @BytecodeUpgrade
        static void setTasks(GradleBuild task, List<String> tasks) {
            setTasks(task, (Collection<String>) tasks);
        }

        /**
         * Sets the tasks that should be executed for this build.
         *
         * @param tasks The task names. May be empty or null to use the default tasks for the build.
         */
        @BytecodeUpgrade
        static void setTasks(GradleBuild task, Collection<String> tasks) {
            task.getStartParameter().get().setTaskNames(tasks);
        }
    }
}
