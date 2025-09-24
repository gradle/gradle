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
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.gradle.internal.build.NestedRootBuildRunner.createStartParameterForNewBuild;
import static org.gradle.internal.build.NestedRootBuildRunner.runNestedRootBuild;

/**
 * Executes a Gradle build.
 */
@DisableCachingByDefault(because = "Child Gradle build will do its own caching")
public abstract class GradleBuild extends ConventionTask {
    private StartParameter startParameter;
    private String buildName;

    public GradleBuild() {
        this.startParameter = createStartParameterForNewBuild(getServices());
        startParameter.setCurrentDir(getProject().getProjectDir());
    }

    /**
     * Returns the full set of parameters that will be used to execute the build.
     *
     * @return the parameters. Never returns null.
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public StartParameter getStartParameter() {
        return startParameter;
    }

    /**
     * Sets the full set of parameters that will be used to execute the build.
     *
     * @param startParameter the parameters. Should not be null.
     */
    public void setStartParameter(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    /**
     * Returns the project directory for the build. Defaults to the project directory.
     *
     * @return The project directory. Never returns null.
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public File getDir() {
        return getStartParameter().getCurrentDir();
    }

    /**
     * Sets the project directory for the build.
     *
     * @param dir The project directory. Should not be null.
     * @since 4.0
     */
    public void setDir(File dir) {
        setDir((Object) dir);
    }

    /**
     * Sets the project directory for the build.
     *
     * @param dir The project directory. Should not be null.
     */
    public void setDir(Object dir) {
        getStartParameter().setCurrentDir(getProject().file(dir));
    }

    /**
     * Returns the tasks that should be executed for this build.
     *
     * @return The sequence. May be empty. Never returns null.
     */
    @Input
    @ToBeReplacedByLazyProperty
    public List<String> getTasks() {
        return getStartParameter().getTaskNames();
    }

    /**
     * Sets the tasks that should be executed for this build.
     *
     * @param tasks The task names. May be empty or null to use the default tasks for the build.
     * @since 4.0
     */
    public void setTasks(List<String> tasks) {
        setTasks((Collection<String>) tasks);
    }

    /**
     * Sets the tasks that should be executed for this build.
     *
     * @param tasks The task names. May be empty or null to use the default tasks for the build.
     */
    public void setTasks(Collection<String> tasks) {
        getStartParameter().setTaskNames(tasks);
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
    @ToBeReplacedByLazyProperty
    public String getBuildName() {
        return buildName;
    }

    /**
     * Sets build name to use for the nested build.
     *
     * @param buildName the build name to use for the nested build
     * @since 6.0
     */
    public void setBuildName(String buildName) {
        this.buildName = buildName;
    }

    @TaskAction
    void build() {
        // TODO: Allow us to inject plugins into nested builds too.
        StartParameterInternal startParameter = (StartParameterInternal) getStartParameter();
        nagForNonStringProjectProperties(startParameter.getProjectPropertiesUntracked());
        runNestedRootBuild(buildName, startParameter, getServices());
    }

    @SuppressWarnings("ConstantValue")
    private static void nagForNonStringProjectProperties(Map<String, String> projectProperties) {
        for (Map.Entry<String, String> entry : projectProperties.entrySet()) {
            String propertyName = entry.getKey();
            Object propertyValue = entry.getValue();
            if (!(propertyValue instanceof String)) {
                // TODO: Remove non-String project properties support in Gradle 10 - https://github.com/gradle/gradle/issues/34454
                DeprecationLogger.deprecateBehaviour(String.format("Using non-String project properties: property '%s' has value of type %s.", propertyName, propertyValue.getClass().getName()))
                    .willBecomeAnErrorInGradle10()
                    .withUpgradeGuideSection(9, "deprecated-gradle-build-non-string-properties")
                    .nagUser();
                return;
            }
        }
    }
}
