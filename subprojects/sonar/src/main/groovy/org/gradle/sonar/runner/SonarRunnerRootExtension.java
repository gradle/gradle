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
import org.gradle.listener.ActionBroadcast;
import org.gradle.process.JavaForkOptions;

/**
 * Specialization of {@link SonarRunnerExtension} that is used for the root of the project tree being analyzed.
 * <p>
 * This extension provides extra configuration options that are only applicable to the analysis as a whole,
 * and therefore is used for the project (typically the root) that the plugin is applied too.
 * <p>
 * Example usage:
 * <pre autoTested=''>
 * sonarRunner {
 *   toolVersion = '2.3' // default
 *
 *   forkOptions {
 *     maxHeapSize = '1024m'
 *     jvmArgs '-XX:MaxPermSize=128m'
 *   }
 * }
 * </pre>
 */
public class SonarRunnerRootExtension extends SonarRunnerExtension {

    /**
     * The version of Sonar Runner used if another version was not specified with {@link #setToolVersion(String)}.
     * <p>
     * Value: {@value}
     */
    public static final String DEFAULT_SONAR_RUNNER_VERSION = "2.3"; // IMPORTANT: if updating this, update the DSL doc.

    private String toolVersion = DEFAULT_SONAR_RUNNER_VERSION;
    private JavaForkOptions forkOptions;

    public SonarRunnerRootExtension(ActionBroadcast<SonarProperties> propertiesActions) {
        super(propertiesActions);
    }

    /**
     * Configure the {@link #forkOptions}.
     *
     * @param action the action to use to configure {@link #forkOptions}
     */
    public void forkOptions(Action<? super JavaForkOptions> action) {
        action.execute(forkOptions);
    }

    /**
     * Version of Sonar Runner JARs to use.
     * <p>
     * Defaults to {@code 2.3}.
     */
    // IMPORTANT: update above if DEFAULT_SONAR_RUNNER_VERSION changes
    public String getToolVersion() {
        return toolVersion;
    }

    public void setToolVersion(String toolVersion) {
        this.toolVersion = toolVersion;
    }

    /**
     * Options for the Sonar Runner process.
     */
    public JavaForkOptions getForkOptions() {
        return forkOptions;
    }

    public void setForkOptions(JavaForkOptions forkOptions) {
        this.forkOptions = forkOptions;
    }

}
