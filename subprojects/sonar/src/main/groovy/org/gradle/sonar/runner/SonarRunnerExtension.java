/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.sonar.runner;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.listener.ActionBroadcast;

/**
 * An extension for configuring the <a href="http://docs.codehaus.org/display/SONAR/Analyzing+with+Sonar+Runner"> Sonar Runner</a>.
 * <p>
 * The extension is added to all projects that have the {@code sonar-runner} plugin applied, and all of their subprojects.
 * <p>
 * Example usage:
 * <pre autoTested=''>
 * sonarRunner {
 *   skipProject = false // this is the default
 *   sonarProperties {
 *     property "sonar.host.url", "http://my.sonar.server" // adding a single property
 *     properties mapOfProperties // adding multiple properties at once
 *     properties["sonar.sources"] += sourceSets.other.java.srcDirs // manipulating an existing property }
 *   }
 * }
 * </pre>
 */
@Incubating
public class SonarRunnerExtension {

    public static final String SONAR_RUNNER_CONFIGURATION_NAME = "sonarRunner";
    public static final String SONAR_RUNNER_EXTENSION_NAME = "sonarRunner";
    public static final String SONAR_RUNNER_TASK_NAME = "sonarRunner";

    private boolean skipProject;
    private final ActionBroadcast<SonarProperties> propertiesActions;

    public SonarRunnerExtension(ActionBroadcast<SonarProperties> propertiesActions) {
        this.propertiesActions = propertiesActions;
    }

    /**
     * Adds an action that configures Sonar properties for the associated Gradle project.
     * <p>
     * <em>Global</em> Sonar properties (e.g. database connection settings) have to be set on the "root" project of the Sonar run.
     * This is the project that has the {@code sonar-runner} plugin applied.
     * <p>
     * The action is passed an instance of {@code SonarProperties}.
     * Evaluation of the action is deferred until {@code sonarRunner.sonarProperties} is requested.
     * Hence it is safe to reference other Gradle model properties from inside the action.
     * <p>
     * Sonar properties can also be set via system properties (and therefore from the command line).
     * This is mainly useful for global Sonar properties like database credentials.
     * Every system property starting with {@code "sonar."} is automatically set on the "root" project of the Sonar run (i.e. the project that has the {@code sonar-runner} plugin applied).
     * System properties take precedence over properties declared in build scripts.
     *
     * @param action an action that configures Sonar properties for the associated Gradle project
     */
    public void sonarProperties(Action<? super SonarProperties> action) {
        propertiesActions.add(action);
    }

    /**
     * If the project will be excluded from analysis.
     * <p>
     * Defaults to {@code false}.
     */
    public boolean isSkipProject() {
        return skipProject;
    }

    public void setSkipProject(boolean skipProject) {
        this.skipProject = skipProject;
    }

}
