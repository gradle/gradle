/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.sonar.runner

import org.gradle.process.JavaForkOptions
import org.gradle.util.ConfigureUtil

/**
 * An extension for configuring the analysis process options. The extension is added
 * to the plugin's target project,
 *
 * <p> Example usage:
 *
 * <pre>
 * sonarRunner {
 *      toolVersion = '2.3' // default
 *
 *      forkOptions {
 *          maxHeapSize = '1024m'
 *          jvmArgs '-XX:MaxPermSize=128m'
 *      }
 * }
 * </pre>
 */
class SonarRunnerRootExtension extends SonarRunnerExtension {

    /**
     * Version of SonarRunner JARs to use.
     */
    String toolVersion = '2.3'

    /**
     * Options for the java analysis process
     */
    JavaForkOptions forkOptions

    /**
     * Configure the {@link SonarRunnerRootExtension#forkOptions}
     * @param configClosure the closure to use to configure {@link SonarRunnerRootExtension#forkOptions}
     */
    void forkOptions(Closure configClosure) {
        ConfigureUtil.configure(configClosure, forkOptions)
    }
}
